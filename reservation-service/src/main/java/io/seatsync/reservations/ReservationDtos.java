package io.seatsync.reservations;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

final class ReservationDtos {
  private ReservationDtos() {}

  record CreateHoldRequest(@NotNull UUID eventId, @NotNull UUID seatId, @NotNull UUID customerId) {}

  record HoldResponse(
      UUID holdId, UUID eventId, UUID seatId, UUID customerId, String status, Instant expiresAt) {
    static HoldResponse from(SeatHold h) {
      return new HoldResponse(
          h.getId(),
          h.getEventId(),
          h.getSeatId(),
          h.getCustomerId(),
          h.getStatus().name(),
          h.getExpiresAt());
    }
  }

  record ConfirmHoldRequest(@NotNull UUID customerId) {}
}
