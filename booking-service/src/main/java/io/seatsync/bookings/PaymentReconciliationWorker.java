package io.seatsync.bookings;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PaymentReconciliationWorker {
  private final BookingStore store;
  private final BookingOrchestrator orchestrator;
  private final Duration paymentResultGrace;

  PaymentReconciliationWorker(
      BookingStore store,
      BookingOrchestrator orchestrator,
      @Value("${seatsync.payment-result-grace:PT10S}") Duration paymentResultGrace) {
    this.store = store;
    this.orchestrator = orchestrator;
    this.paymentResultGrace = paymentResultGrace;
  }

  @Scheduled(fixedDelayString = "${seatsync.payment-reconciliation-ms:2000}")
  void reconcile() {
    // The grace period avoids racing with a checkout that is still running.
    store
        .pendingPaymentsBefore(Instant.now().minus(paymentResultGrace))
        .forEach(orchestrator::reconcile);
    store.unknownPayments().forEach(orchestrator::reconcile);
    store.pendingRefunds().forEach(orchestrator::retryRefund);
  }
}
