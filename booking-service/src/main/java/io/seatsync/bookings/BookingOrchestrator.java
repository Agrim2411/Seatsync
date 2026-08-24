package io.seatsync.bookings;

import static io.seatsync.bookings.BookingDtos.*;

import io.grpc.ManagedChannel;
import io.seatsync.grpc.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
class BookingOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(BookingOrchestrator.class);

  private final BookingStore store;
  private final ReservationCommandServiceGrpc.ReservationCommandServiceBlockingStub reservations;
  private final RestClient payments;
  private final Duration paymentResultGrace;

  BookingOrchestrator(
      BookingStore store,
      RestClient.Builder builder,
      ManagedChannel reservationChannel,
      @Value("${seatsync.payment-url:http://localhost:8084}") String paymentUrl,
      @Value("${seatsync.payment-result-grace:PT10S}") Duration paymentResultGrace) {
    this.store = store;
    reservations = ReservationCommandServiceGrpc.newBlockingStub(reservationChannel);
    payments = builder.clone().baseUrl(paymentUrl).build();
    this.paymentResultGrace = paymentResultGrace;
  }

  BookingResponse checkout(Booking booking, CreateBookingRequest request) {
    if (booking.getStatus() != Booking.Status.PENDING) return BookingResponse.from(booking);
    PaymentResponse payment;
    try {
      payment =
          payments
              .post()
              .uri("/internal/payments/authorize")
              .header("Idempotency-Key", "booking-" + booking.getId())
              .body(
                  new PaymentRequest(
                      booking.getId(),
                      booking.getAmountMinor(),
                      booking.getCurrency(),
                      request.paymentMethodToken()))
              .retrieve()
              .body(PaymentResponse.class);
    } catch (Exception e) {
      store.paymentUnknown(booking.getId());
      return BookingResponse.from(store.get(booking.getId()));
    }
    if (payment == null || !"AUTHORIZED".equals(payment.status())) {
      releaseHold(request.holdId(), request.customerId());
      store.failed(booking.getId(), "PAYMENT_DECLINED");
      return BookingResponse.from(store.get(booking.getId()));
    }
    completeAuthorized(booking, payment);
    return BookingResponse.from(store.get(booking.getId()));
  }

  void reconcile(Booking booking) {
    try {
      PaymentResponse payment =
          payments
              .get()
              .uri(
                  uri ->
                      uri.path("/internal/payments")
                          .queryParam("bookingId", booking.getId())
                          .build())
              .retrieve()
              .body(PaymentResponse.class);
      if (payment != null && "AUTHORIZED".equals(payment.status())) {
        completeAuthorized(booking, payment);
      } else if (payment != null) {
        releaseHold(booking.getHoldId(), booking.getCustomerId());
        store.failed(booking.getId(), "PAYMENT_DECLINED");
      }
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404
          && Duration.between(booking.getCreatedAt(), Instant.now())
                  .compareTo(paymentResultGrace)
              > 0) {
        releaseHold(booking.getHoldId(), booking.getCustomerId());
        store.failed(booking.getId(), "PAYMENT_NOT_FOUND");
      }
    } catch (Exception e) {
      log.debug("Payment reconciliation deferred for booking {}", booking.getId(), e);
    }
  }

  void retryRefund(Booking booking) {
    if (booking.getStatus() != Booking.Status.REFUND_PENDING || booking.getPaymentId() == null)
      return;
    try {
      PaymentResponse payment =
          payments
              .post()
              .uri("/internal/payments/{id}/refund", booking.getPaymentId())
              .retrieve()
              .body(PaymentResponse.class);
      if (payment != null && "REFUNDED".equals(payment.status())) {
        store.refunded(booking.getId());
      } else {
        log.warn(
            "Refund provider returned a non-refunded state for booking {} and payment {}",
            booking.getId(),
            booking.getPaymentId());
      }
    } catch (Exception e) {
      log.warn(
          "Refund attempt deferred for booking {} and payment {}",
          booking.getId(),
          booking.getPaymentId(),
          e);
    }
  }

  private void completeAuthorized(Booking booking, PaymentResponse payment) {
    try {
      reservations
          .withDeadlineAfter(2, TimeUnit.SECONDS)
          .confirmHold(
              io.seatsync.grpc.ConfirmHoldRequest.newBuilder()
                  .setHoldId(booking.getHoldId().toString())
                  .setCustomerId(booking.getCustomerId().toString())
                  .build());
      store.confirmed(booking.getId(), payment.paymentId());
    } catch (Exception e) {
      store.refundPending(booking.getId(), payment.paymentId(), "HOLD_CONFIRMATION_FAILED");
      retryRefund(store.get(booking.getId()));
    }
  }

  private void releaseHold(UUID holdId, UUID customerId) {
    try {
      reservations
          .withDeadlineAfter(2, TimeUnit.SECONDS)
          .releaseHold(
              ReleaseHoldRequest.newBuilder()
                  .setHoldId(holdId.toString())
                  .setCustomerId(customerId.toString())
                  .build());
    } catch (Exception e) {
      log.warn("Hold release deferred to expiry for hold {}", holdId, e);
    }
  }
}
