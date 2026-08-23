package io.seatsync.bookings;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
class Booking {
  enum Status {
    PENDING,
    PAYMENT_UNKNOWN,
    CONFIRMED,
    FAILED,
    REFUND_PENDING,
    REFUNDED
  }

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String idempotencyKey;

  @Column(nullable = false)
  private String requestHash;

  @Column(nullable = false, unique = true)
  private UUID holdId;

  @Column(nullable = false)
  private UUID customerId;

  @Column(nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  private UUID paymentId;
  private String failureCode;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant updatedAt;

  protected Booking() {}

  Booking(
      UUID id,
      String key,
      String hash,
      UUID holdId,
      UUID customerId,
      long amount,
      String currency) {
    this.id = id;
    this.idempotencyKey = key;
    this.requestHash = hash;
    this.holdId = holdId;
    this.customerId = customerId;
    this.amountMinor = amount;
    this.currency = currency;
    status = Status.PENDING;
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getHoldId() {
    return holdId;
  }

  UUID getCustomerId() {
    return customerId;
  }

  long getAmountMinor() {
    return amountMinor;
  }

  String getCurrency() {
    return currency;
  }

  Status getStatus() {
    return status;
  }

  UUID getPaymentId() {
    return paymentId;
  }

  String getFailureCode() {
    return failureCode;
  }

  String getRequestHash() {
    return requestHash;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void paymentUnknown() {
    status = Status.PAYMENT_UNKNOWN;
    updatedAt = Instant.now();
  }

  void confirmed(UUID paymentId) {
    this.paymentId = paymentId;
    status = Status.CONFIRMED;
    updatedAt = Instant.now();
  }

  void failed(String code) {
    failureCode = code;
    status = Status.FAILED;
    updatedAt = Instant.now();
  }

  void refundPending(UUID paymentId, String code) {
    this.paymentId = paymentId;
    failureCode = code;
    status = Status.REFUND_PENDING;
    updatedAt = Instant.now();
  }

  void refunded() {
    status = Status.REFUNDED;
    updatedAt = Instant.now();
  }
}
