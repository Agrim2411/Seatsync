package io.seatsync.payments;

import static io.seatsync.payments.PaymentDtos.*;

import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentService {
  private final PaymentRepository payments;

  PaymentService(PaymentRepository payments) {
    this.payments = payments;
  }

  @Transactional
  PaymentResponse authorize(String key, AuthorizeRequest request) {
    if (key == null || key.isBlank()) {
      throw new PaymentException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    }

    var prior = payments.findByIdempotencyKey(key);
    if (prior.isPresent()) {
      Payment p = prior.get();
      if (!p.getBookingId().equals(request.bookingId())
          || p.getAmountMinor() != request.amountMinor()
          || !p.getCurrency().equals(request.currency())) {
        throw new PaymentException(
            "IDEMPOTENCY_KEY_REUSED", "Key was used for a different payment");
      }
      return PaymentResponse.from(p);
    }

    simulateLatency(request.paymentMethodToken());
    Payment.Status status =
        request.paymentMethodToken().startsWith("pm_decline")
            ? Payment.Status.DECLINED
            : Payment.Status.AUTHORIZED;
    return PaymentResponse.from(
        payments.save(
            new Payment(
                request.bookingId(), key, request.amountMinor(), request.currency(), status)));
  }

  @Transactional
  PaymentResponse refund(UUID paymentId) {
    Payment payment =
        payments
            .findById(paymentId)
            .orElseThrow(() -> new PaymentException("PAYMENT_NOT_FOUND", "Payment does not exist"));
    payment.refund();
    return PaymentResponse.from(payment);
  }

  @Transactional(readOnly = true)
  PaymentResponse findByBooking(UUID bookingId) {
    return payments
        .findByBookingId(bookingId)
        .map(PaymentResponse::from)
        .orElseThrow(() -> new PaymentException("PAYMENT_NOT_FOUND", "Payment does not exist"));
  }

  private void simulateLatency(String token) {
    Duration delay = token.startsWith("pm_timeout") ? Duration.ofSeconds(4) : Duration.ofMillis(30);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PaymentException("PAYMENT_INTERRUPTED", "Payment simulation interrupted");
    }
  }
}
