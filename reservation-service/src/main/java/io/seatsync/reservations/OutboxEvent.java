package io.seatsync.reservations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
  @Id private UUID id;

  @Column(nullable = false)
  private String aggregateType;

  @Column(nullable = false)
  private UUID aggregateId;

  @Column(nullable = false)
  private String eventType;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant publishedAt;

  protected OutboxEvent() {}

  OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
    id = UUID.randomUUID();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    createdAt = Instant.now();
  }

  UUID getId() {
    return id;
  }

  UUID getAggregateId() {
    return aggregateId;
  }

  String getEventType() {
    return eventType;
  }

  String getPayload() {
    return payload;
  }

  void published() {
    publishedAt = Instant.now();
  }
}
