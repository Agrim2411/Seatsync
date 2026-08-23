package io.seatsync.reservations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
class SeatHold {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID eventId;

  @Column(nullable = false)
  private UUID seatId;

  @Column(nullable = false)
  private UUID customerId;

  @Column(nullable = false)
  private String ownershipToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HoldStatus status;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant updatedAt;

  protected SeatHold() {}

  SeatHold(UUID id, UUID eventId, UUID seatId, UUID customerId, String token, Instant expiresAt) {
    this.id = id;
    this.eventId = eventId;
    this.seatId = seatId;
    this.customerId = customerId;
    this.ownershipToken = token;
    this.status = HoldStatus.ACTIVE;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  UUID getId() {
    return id;
  }

  UUID getEventId() {
    return eventId;
  }

  UUID getSeatId() {
    return seatId;
  }

  UUID getCustomerId() {
    return customerId;
  }

  String getOwnershipToken() {
    return ownershipToken;
  }

  HoldStatus getStatus() {
    return status;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  void confirm() {
    status = HoldStatus.CONFIRMED;
    updatedAt = Instant.now();
  }

  void release(HoldStatus target) {
    status = target;
    updatedAt = Instant.now();
  }
}
