package io.seatsync.bookings;

import jakarta.validation.constraints.*;
import java.util.UUID;

final class BookingDtos {
  private BookingDtos() {}

  record CreateBookingRequest(
      @NotNull UUID holdId,
      @NotNull UUID customerId,
      @Positive long amountMinor,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethodToken) {}

  record BookingResponse(
      UUID bookingId, UUID holdId, UUID paymentId, String status, String failureCode) {
    static BookingResponse from(Booking b) {
      return new BookingResponse(
          b.getId(), b.getHoldId(), b.getPaymentId(), b.getStatus().name(), b.getFailureCode());
    }
  }

  record PaymentRequest(
      UUID bookingId, long amountMinor, String currency, String paymentMethodToken) {}

  record PaymentResponse(
      UUID paymentId, UUID bookingId, long amountMinor, String currency, String status) {}
}
