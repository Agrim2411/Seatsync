package io.seatsync.events;

import com.fasterxml.jackson.databind.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ReservationEventProjector {
  private final JdbcTemplate jdbc;
  private final SeatRepository seats;
  private final ObjectMapper json;

  ReservationEventProjector(JdbcTemplate jdbc, SeatRepository seats, ObjectMapper json) {
    this.jdbc = jdbc;
    this.seats = seats;
    this.json = json;
  }

  @KafkaListener(
      topics = "${seatsync.reservation-topic:reservation-events}",
      groupId = "seat-map-projector")
  @Transactional
  void project(String raw) throws Exception {
    JsonNode root = json.readTree(raw);
    UUID eventMessageId = UUID.fromString(required(root, "eventId"));
    String type = required(root, "eventType");
    int inserted =
        jdbc.update(
            "INSERT INTO event_inbox(event_id,event_type) VALUES (?,?) ON CONFLICT DO NOTHING",
            eventMessageId,
            type);
    if (inserted == 0) return;
    JsonNode data = root.path("data");
    UUID eventId = UUID.fromString(required(data, "eventId"));
    UUID seatId = UUID.fromString(required(data, "seatId"));
    Seat.Availability availability = toAvailability(required(data, "status"));
    if (seats.updateAvailability(eventId, seatId, availability) != 1) {
      throw new IllegalStateException("Seat projection target does not exist: " + seatId);
    }
  }

  private Seat.Availability toAvailability(String reservationStatus) {
    return switch (reservationStatus) {
      case "ACTIVE" -> Seat.Availability.HELD;
      case "CONFIRMED" -> Seat.Availability.BOOKED;
      case "RELEASED", "EXPIRED" -> Seat.Availability.AVAILABLE;
      default -> throw new IllegalArgumentException("Unsupported reservation state");
    };
  }

  private String required(JsonNode node, String field) {
    String value = node.path(field).asText();
    if (value.isBlank()) {
      throw new IllegalArgumentException("Missing " + field);
    }
    return value;
  }
}
