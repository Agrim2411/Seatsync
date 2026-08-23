package io.seatsync.bookings;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PaymentReconciliationWorker {
  private final BookingStore store;
  private final BookingOrchestrator orchestrator;

  PaymentReconciliationWorker(BookingStore store, BookingOrchestrator orchestrator) {
    this.store = store;
    this.orchestrator = orchestrator;
  }

  @Scheduled(fixedDelayString = "${seatsync.payment-reconciliation-ms:2000}")
  void reconcile() {
    store.unknownPayments().forEach(orchestrator::reconcile);
    store.pendingRefunds().forEach(orchestrator::retryRefund);
  }
}
