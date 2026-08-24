package io.seatsync.payments;

import static io.seatsync.payments.PaymentDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
  private final PaymentRepository payments = mock(PaymentRepository.class);
  private final PaymentService service = new PaymentService(payments);

  @Test
  void returnsTheOriginalPaymentWhenAnIdempotentRequestIsReplayed() {
    UUID bookingId = UUID.randomUUID();
    Payment existing =
        new Payment(bookingId, "payment-key", 2500, "USD", Payment.Status.AUTHORIZED);
    when(payments.findByIdempotencyKey("payment-key")).thenReturn(Optional.of(existing));

    PaymentResponse response =
        service.authorize(
            "payment-key", new AuthorizeRequest(bookingId, 2500, "USD", "pm_success"));

    assertThat(response.paymentId()).isEqualTo(existing.getId());
    assertThat(response.status()).isEqualTo("AUTHORIZED");
    verify(payments, never()).save(any());
  }

  @Test
  void rejectsAnIdempotencyKeyReusedForDifferentPaymentDetails() {
    UUID bookingId = UUID.randomUUID();
    Payment existing =
        new Payment(bookingId, "payment-key", 2500, "USD", Payment.Status.AUTHORIZED);
    when(payments.findByIdempotencyKey("payment-key")).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.authorize(
                    "payment-key", new AuthorizeRequest(bookingId, 3000, "USD", "pm_success")))
        .isInstanceOf(PaymentException.class)
        .extracting(error -> ((PaymentException) error).getCode())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void recordsADeclinedPayment() {
    UUID bookingId = UUID.randomUUID();
    when(payments.findByIdempotencyKey("decline-key")).thenReturn(Optional.empty());
    when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PaymentResponse response =
        service.authorize(
            "decline-key", new AuthorizeRequest(bookingId, 2500, "USD", "pm_decline_card"));

    assertThat(response.status()).isEqualTo("DECLINED");
    assertThat(response.bookingId()).isEqualTo(bookingId);
  }

  @Test
  void refundsAnAuthorizedPaymentIdempotently() {
    Payment payment =
        new Payment(UUID.randomUUID(), "refund-key", 2500, "USD", Payment.Status.AUTHORIZED);
    when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));

    assertThat(service.refund(payment.getId()).status()).isEqualTo("REFUNDED");
    assertThat(service.refund(payment.getId()).status()).isEqualTo("REFUNDED");
  }
}
