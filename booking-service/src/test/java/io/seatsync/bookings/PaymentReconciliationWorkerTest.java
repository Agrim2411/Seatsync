package io.seatsync.bookings;

import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentReconciliationWorkerTest {
  @Test
  void dispatchesInterruptedUnknownAndRefundPendingBookings() {
    BookingStore store = mock(BookingStore.class);
    BookingOrchestrator orchestrator = mock(BookingOrchestrator.class);
    PaymentReconciliationWorker worker =
        new PaymentReconciliationWorker(store, orchestrator, Duration.ofSeconds(10));
    Booking pending = booking();
    Booking unknown = booking();
    unknown.paymentUnknown();
    Booking refund = booking();
    refund.refundPending(UUID.randomUUID(), "HOLD_CONFIRMATION_FAILED");
    when(store.pendingPaymentsBefore(any())).thenReturn(List.of(pending));
    when(store.unknownPayments()).thenReturn(List.of(unknown));
    when(store.pendingRefunds()).thenReturn(List.of(refund));

    worker.reconcile();

    verify(orchestrator).reconcile(pending);
    verify(orchestrator).reconcile(unknown);
    verify(orchestrator).retryRefund(refund);
  }

  private Booking booking() {
    return new Booking(
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        "request-hash",
        UUID.randomUUID(),
        UUID.randomUUID(),
        2500,
        "USD");
  }
}
