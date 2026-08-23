package io.seatsync.reservations;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {
  @Modifying
  @Query(
      value =
          "UPDATE seat_inventory SET state='HELD', version=version+1 WHERE seat_id=:seatId AND"
              + " event_id=:eventId AND state='AVAILABLE'",
      nativeQuery = true)
  int tryHold(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);

  @Modifying
  @Query(
      value =
          "UPDATE seat_inventory SET state='AVAILABLE', version=version+1 WHERE seat_id=:seatId AND"
              + " state='HELD'",
      nativeQuery = true)
  int release(@Param("seatId") UUID seatId);

  @Modifying
  @Query(
      value =
          "UPDATE seat_inventory SET state='BOOKED', version=version+1 WHERE seat_id=:seatId AND"
              + " state='HELD'",
      nativeQuery = true)
  int confirm(@Param("seatId") UUID seatId);
}
