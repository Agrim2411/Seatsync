package io.seatsync.contracts;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID aggregateId,
    Instant occurredAt,
    String correlationId,
    T data) {

  public static <T> EventEnvelope<T> create(
      String type, UUID aggregateId, String correlationId, T data) {
    return new EventEnvelope<>(
        UUID.randomUUID(), type, 1, aggregateId, Instant.now(), correlationId, data);
  }
}
