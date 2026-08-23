package io.seatsync.reservations;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  @org.springframework.data.jpa.repository.Query(
      value =
          "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at LIMIT 100 FOR"
              + " UPDATE SKIP LOCKED",
      nativeQuery = true)
  java.util.List<OutboxEvent> findUnpublishedBatch();
}
