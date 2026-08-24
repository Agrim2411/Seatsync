package io.seatsync.events;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "seats",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_event_seat_label",
            columnNames = {"event_id", "label"}))
public class Seat {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(nullable = false)
  private String section;

  @Column(nullable = false)
  private String rowLabel;

  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  private long priceMinor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Availability availability;

  public enum Availability {
    AVAILABLE,
    HELD,
    BOOKED
  }

  protected Seat() {}

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getSection() {
    return section;
  }

  public String getRowLabel() {
    return rowLabel;
  }

  public String getLabel() {
    return label;
  }

  public long getPriceMinor() {
    return priceMinor;
  }

  public Availability getAvailability() {
    return availability;
  }
}
