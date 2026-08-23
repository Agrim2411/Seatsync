package io.seatsync.reservations;

import static io.seatsync.reservations.ReservationDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class ReservationService {
  private final SeatInventoryRepository inventory;
  private final SeatHoldRepository holds;
  private final IdempotencyRepository idempotency;
  private final OutboxRepository outbox;
  private final RedisSeatGate gate;
  private final ObjectMapper json;
  private final Clock clock;
  private final Duration holdTtl;

  ReservationService(
      SeatInventoryRepository inventory,
      SeatHoldRepository holds,
      IdempotencyRepository idempotency,
      OutboxRepository outbox,
      RedisSeatGate gate,
      ObjectMapper json,
      @Value("${seatsync.hold-ttl:PT5M}") Duration holdTtl) {
    this.inventory = inventory;
    this.holds = holds;
    this.idempotency = idempotency;
    this.outbox = outbox;
    this.gate = gate;
    this.json = json;
    this.clock = Clock.systemUTC();
    this.holdTtl = holdTtl;
  }

  @Transactional
  HoldResponse createHold(String key, CreateHoldRequest request) {
    if (key == null || key.isBlank())
      throw new ReservationException(
          "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    String hash = hash(request.eventId() + ":" + request.seatId() + ":" + request.customerId());
    var replay = idempotency.findById(key);
    if (replay.isPresent()) {
      if (!replay.get().getRequestHash().equals(hash))
        throw new ReservationException(
            "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for another request");
      return holds
          .findById(replay.get().getHoldId())
          .map(HoldResponse::from)
          .orElseThrow(
              () -> new ReservationException("HOLD_NOT_FOUND", "Stored hold no longer exists"));
    }

    UUID holdId = UUID.randomUUID();
    String token = holdId.toString();
    Duration gateTtl = holdTtl.plusSeconds(30);
    if (!gate.acquire(request.eventId(), request.seatId(), token, gateTtl)) {
      throw new ReservationException("SEAT_UNAVAILABLE", "Seat is already held or booked");
    }
    releaseGateIfTransactionRollsBack(request.eventId(), request.seatId(), token);

    if (inventory.tryHold(request.eventId(), request.seatId()) != 1) {
      gate.release(request.eventId(), request.seatId(), token);
      throw new ReservationException("SEAT_UNAVAILABLE", "Seat is already held or booked");
    }

    Instant expiresAt = clock.instant().plus(holdTtl);
    SeatHold hold =
        new SeatHold(
            holdId, request.eventId(), request.seatId(), request.customerId(), token, expiresAt);
    try {
      holds.saveAndFlush(hold);
      idempotency.saveAndFlush(new IdempotencyRecord(key, hash, holdId));
      outbox.save(new OutboxEvent("SeatHold", holdId, "reservation.hold.created", payload(hold)));
    } catch (DataIntegrityViolationException conflict) {
      throw new ReservationException("SEAT_UNAVAILABLE", "Concurrent reservation won the seat");
    }
    return HoldResponse.from(hold);
  }

  @Transactional(readOnly = true)
  HoldResponse get(UUID holdId) {
    return holds
        .findById(holdId)
        .map(HoldResponse::from)
        .orElseThrow(() -> new ReservationException("HOLD_NOT_FOUND", "Hold does not exist"));
  }

  @Transactional
  HoldResponse confirm(UUID holdId, UUID customerId) {
    SeatHold hold = locked(holdId);
    if (!hold.getCustomerId().equals(customerId))
      throw new ReservationException("HOLD_OWNER_MISMATCH", "Hold belongs to another customer");
    if (hold.getStatus() == HoldStatus.CONFIRMED) return HoldResponse.from(hold);
    if (hold.getStatus() != HoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(clock.instant())) {
      throw new ReservationException("HOLD_NOT_ACTIVE", "Hold expired or was released");
    }
    if (inventory.confirm(hold.getSeatId()) != 1)
      throw new ReservationException("INVALID_SEAT_STATE", "Seat cannot be confirmed");
    hold.confirm();
    gate.release(hold.getEventId(), hold.getSeatId(), hold.getOwnershipToken());
    outbox.save(new OutboxEvent("SeatHold", holdId, "reservation.hold.confirmed", payload(hold)));
    return HoldResponse.from(hold);
  }

  @Transactional
  void release(UUID holdId, UUID customerId) {
    SeatHold hold = locked(holdId);
    if (!hold.getCustomerId().equals(customerId))
      throw new ReservationException("HOLD_OWNER_MISMATCH", "Hold belongs to another customer");
    if (hold.getStatus() != HoldStatus.ACTIVE) return;
    inventory.release(hold.getSeatId());
    hold.release(HoldStatus.RELEASED);
    gate.release(hold.getEventId(), hold.getSeatId(), hold.getOwnershipToken());
    outbox.save(new OutboxEvent("SeatHold", holdId, "reservation.hold.released", payload(hold)));
  }

  private SeatHold locked(UUID id) {
    return holds
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ReservationException("HOLD_NOT_FOUND", "Hold does not exist"));
  }

  private void releaseGateIfTransactionRollsBack(UUID eventId, UUID seatId, String token) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) gate.release(eventId, seatId, token);
          }
        });
  }

  private String payload(SeatHold h) {
    try {
      return json.writeValueAsString(HoldResponse.from(h));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize outbox event", e);
    }
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
