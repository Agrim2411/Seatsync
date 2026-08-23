package io.seatsync.reservations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecord {
  @Id private String idempotencyKey;

  @Column(nullable = false)
  private String requestHash;

  @Column(nullable = false)
  private UUID holdId;

  @Column(nullable = false)
  private Instant createdAt;

  protected IdempotencyRecord() {}

  IdempotencyRecord(String key, String hash, UUID holdId) {
    this.idempotencyKey = key;
    this.requestHash = hash;
    this.holdId = holdId;
    this.createdAt = Instant.now();
  }

  String getRequestHash() {
    return requestHash;
  }

  UUID getHoldId() {
    return holdId;
  }
}
