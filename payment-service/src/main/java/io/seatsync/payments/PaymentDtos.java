package io.seatsync.payments;

import jakarta.validation.constraints.*;
import java.util.UUID;

final class PaymentDtos {
  private PaymentDtos() {}

  record AuthorizeRequest(
      @NotNull UUID bookingId,
      @Positive long amountMinor,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethodToken) {}

  record PaymentResponse(
      UUID paymentId, UUID bookingId, long amountMinor, String currency, String status) {
    static PaymentResponse from(Payment p) {
      return new PaymentResponse(
          p.getId(), p.getBookingId(), p.getAmountMinor(), p.getCurrency(), p.getStatus().name());
    }
  }
}
