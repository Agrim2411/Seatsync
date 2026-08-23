package io.seatsync.bookings;

import static io.seatsync.bookings.BookingDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BookingStore {
  private final BookingRepository bookings;

  BookingStore(BookingRepository bookings) {
    this.bookings = bookings;
  }

  @Transactional
  Booking start(String key, CreateBookingRequest request) {
    if (key == null || key.isBlank())
      throw new BookingException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
    String hash =
        hash(
            request.holdId()
                + ":"
                + request.customerId()
                + ":"
                + request.amountMinor()
                + ":"
                + request.currency()
                + ":"
                + request.paymentMethodToken());
    var prior = bookings.findByIdempotencyKey(key);
    if (prior.isPresent()) {
      if (!prior.get().getRequestHash().equals(hash))
        throw new BookingException("IDEMPOTENCY_KEY_REUSED", "Key was used for another booking");
      return prior.get();
    }
    return bookings.save(
        new Booking(
            UUID.randomUUID(),
            key,
            hash,
            request.holdId(),
            request.customerId(),
            request.amountMinor(),
            request.currency()));
  }

  @Transactional(readOnly = true)
  Booking get(UUID id) {
    return bookings
        .findById(id)
        .orElseThrow(() -> new BookingException("BOOKING_NOT_FOUND", "Booking does not exist"));
  }

  @Transactional
  void confirmed(UUID id, UUID paymentId) {
    getMutable(id).confirmed(paymentId);
  }

  @Transactional
  void paymentUnknown(UUID id) {
    getMutable(id).paymentUnknown();
  }

  @Transactional(readOnly = true)
  java.util.List<Booking> unknownPayments() {
    return bookings.findTop100ByStatusOrderByCreatedAtAsc(Booking.Status.PAYMENT_UNKNOWN);
  }

  @Transactional(readOnly = true)
  java.util.List<Booking> pendingRefunds() {
    return bookings.findTop100ByStatusOrderByCreatedAtAsc(Booking.Status.REFUND_PENDING);
  }

  @Transactional
  void failed(UUID id, String code) {
    getMutable(id).failed(code);
  }

  @Transactional
  void refundPending(UUID id, UUID paymentId, String code) {
    getMutable(id).refundPending(paymentId, code);
  }

  @Transactional
  void refunded(UUID id) {
    getMutable(id).refunded();
  }

  private Booking getMutable(UUID id) {
    return bookings
        .findById(id)
        .orElseThrow(() -> new BookingException("BOOKING_NOT_FOUND", "Booking does not exist"));
  }

  private String hash(String v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
