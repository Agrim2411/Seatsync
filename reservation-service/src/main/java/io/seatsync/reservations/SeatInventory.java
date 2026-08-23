package io.seatsync.reservations;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "seat_inventory")
class SeatInventory {
  @Id private UUID seatId;

  @Column(nullable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SeatState state;

  @Version private long version;

  protected SeatInventory() {}
}
