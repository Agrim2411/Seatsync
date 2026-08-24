package io.seatsync.reservations;

import static io.seatsync.reservations.ReservationDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {
  private final SeatInventoryRepository inventory = mock(SeatInventoryRepository.class);
  private final SeatHoldRepository holds = mock(SeatHoldRepository.class);
  private final IdempotencyRepository idempotency = mock(IdempotencyRepository.class);
  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final RedisSeatGate gate = mock(RedisSeatGate.class);
  private final ReservationService service =
      new ReservationService(
          inventory,
          holds,
          idempotency,
          outbox,
          gate,
          new ObjectMapper().findAndRegisterModules(),
          Duration.ofMinutes(5));

  @Test
  void rejectsASeatAlreadyOwnedByAnotherRedisGateToken() {
    CreateHoldRequest request = request();
    when(idempotency.findById("hold-key")).thenReturn(Optional.empty());
    when(gate.acquire(eq(request.eventId()), eq(request.seatId()), any(), any()))
        .thenReturn(false);

    assertThatThrownBy(() -> service.createHold("hold-key", request))
        .isInstanceOf(ReservationException.class)
        .extracting(error -> ((ReservationException) error).getCode())
        .isEqualTo("SEAT_UNAVAILABLE");
    verify(inventory, never()).tryHold(any(), any());
  }

  @Test
  void replaysTheStoredHoldWithoutAcquiringTheSeatAgain() throws Exception {
    CreateHoldRequest request = request();
    UUID holdId = UUID.randomUUID();
    SeatHold hold =
        new SeatHold(
            holdId,
            request.eventId(),
            request.seatId(),
            request.customerId(),
            "owner-token",
            Instant.now().plusSeconds(60));
    String hash =
        sha256(request.eventId() + ":" + request.seatId() + ":" + request.customerId());
    when(idempotency.findById("hold-key"))
        .thenReturn(Optional.of(new IdempotencyRecord("hold-key", hash, holdId)));
    when(holds.findById(holdId)).thenReturn(Optional.of(hold));

    HoldResponse response = service.createHold("hold-key", request);

    assertThat(response.holdId()).isEqualTo(holdId);
    assertThat(response.status()).isEqualTo("ACTIVE");
    verifyNoInteractions(gate, inventory, outbox);
  }

  @Test
  void confirmsAnActiveOwnedHoldAndReleasesItsRedisGate() {
    CreateHoldRequest request = request();
    UUID holdId = UUID.randomUUID();
    SeatHold hold =
        new SeatHold(
            holdId,
            request.eventId(),
            request.seatId(),
            request.customerId(),
            "owner-token",
            Instant.now().plusSeconds(60));
    when(holds.findByIdForUpdate(holdId)).thenReturn(Optional.of(hold));
    when(inventory.confirm(request.seatId())).thenReturn(1);

    HoldResponse response = service.confirm(holdId, request.customerId());

    assertThat(response.status()).isEqualTo("CONFIRMED");
    verify(gate).release(request.eventId(), request.seatId(), "owner-token");
    verify(outbox).save(any(OutboxEvent.class));
  }

  private CreateHoldRequest request() {
    return new CreateHoldRequest(
        UUID.fromString("30000000-0000-0000-0000-000000000003"),
        UUID.fromString("40000000-0000-0000-0000-000000000004"),
        UUID.fromString("50000000-0000-0000-0000-000000000005"));
  }

  private String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
