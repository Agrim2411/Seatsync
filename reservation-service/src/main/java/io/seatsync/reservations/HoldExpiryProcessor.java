package io.seatsync.reservations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Component
class HoldExpiryScheduler {
  private final SeatHoldRepository holds;
  private final HoldExpiryProcessor processor;

  HoldExpiryScheduler(SeatHoldRepository holds, HoldExpiryProcessor processor) {
    this.holds = holds;
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${seatsync.expiry-scan-ms:1000}")
  void scan() {
    holds
        .findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(HoldStatus.ACTIVE, Instant.now())
        .forEach(h -> processor.expire(h.getId()));
  }
}

@Service
class HoldExpiryProcessor {
  private final SeatHoldRepository holds;
  private final SeatInventoryRepository inventory;
  private final OutboxRepository outbox;
  private final RedisSeatGate gate;
  private final ObjectMapper json;

  HoldExpiryProcessor(
      SeatHoldRepository holds,
      SeatInventoryRepository inventory,
      OutboxRepository outbox,
      RedisSeatGate gate,
      ObjectMapper json) {
    this.holds = holds;
    this.inventory = inventory;
    this.outbox = outbox;
    this.gate = gate;
    this.json = json;
  }

  @Transactional
  public void expire(UUID id) {
    SeatHold hold = holds.findByIdForUpdate(id).orElse(null);
    if (hold == null
        || hold.getStatus() != HoldStatus.ACTIVE
        || hold.getExpiresAt().isAfter(Instant.now())) return;
    inventory.release(hold.getSeatId());
    hold.release(HoldStatus.EXPIRED);
    gate.release(hold.getEventId(), hold.getSeatId(), hold.getOwnershipToken());
    try {
      outbox.save(
          new OutboxEvent(
              "SeatHold",
              id,
              "reservation.hold.expired",
              json.writeValueAsString(ReservationDtos.HoldResponse.from(hold))));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
