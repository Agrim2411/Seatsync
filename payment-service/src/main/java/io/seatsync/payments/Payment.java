package io.seatsync.payments;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class Payment {
  enum Status {
    AUTHORIZED,
    DECLINED,
    REFUNDED
  }

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String idempotencyKey;

  @Column(nullable = false)
  private UUID bookingId;

  @Column(nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant updatedAt;

  protected Payment() {}

  Payment(UUID bookingId, String key, long amountMinor, String currency, Status status) {
    id = UUID.randomUUID();
    this.bookingId = bookingId;
    this.idempotencyKey = key;
    this.amountMinor = amountMinor;
    this.currency = currency;
    this.status = status;
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getBookingId() {
    return bookingId;
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

  void refund() {
    if (status == Status.AUTHORIZED) {
      status = Status.REFUNDED;
      updatedAt = Instant.now();
    }
  }
}
