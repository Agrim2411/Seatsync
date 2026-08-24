package io.seatsync.reservations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OutboxPublisher {
  private final OutboxRepository outbox;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final String topic;

  OutboxPublisher(
      OutboxRepository outbox,
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      @Value("${seatsync.reservation-topic:reservation-events}") String topic) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.json = json;
    this.topic = topic;
  }

  @Scheduled(fixedDelayString = "${seatsync.outbox-poll-ms:500}")
  @Transactional
  void publish() {
    for (OutboxEvent event : outbox.findUnpublishedBatch()) {
      try {
        String envelope =
            json.writeValueAsString(
                Map.of(
                    "eventId",
                    event.getId(),
                    "eventType",
                    event.getEventType(),
                    "schemaVersion",
                    1,
                    "aggregateType",
                    event.getAggregateType(),
                    "aggregateId",
                    event.getAggregateId(),
                    "data",
                    json.readTree(event.getPayload())));
        kafka.send(topic, event.getAggregateId().toString(), envelope).get(2, TimeUnit.SECONDS);
        event.published();
      } catch (Exception e) {
        break;
      }
    }
  }
}
