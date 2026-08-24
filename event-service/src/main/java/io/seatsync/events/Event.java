package io.seatsync.events;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String venue;

  @Column(nullable = false)
  private Instant saleStartsAt;

  @Column(nullable = false)
  private Instant startsAt;

  protected Event() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getVenue() {
    return venue;
  }

  public Instant getSaleStartsAt() {
    return saleStartsAt;
  }

  public Instant getStartsAt() {
    return startsAt;
  }
}
