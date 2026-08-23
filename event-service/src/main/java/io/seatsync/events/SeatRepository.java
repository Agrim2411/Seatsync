package io.seatsync.events;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SeatRepository extends JpaRepository<Seat, UUID> {
  List<Seat> findAllByEventIdOrderBySectionAscRowLabelAscLabelAsc(UUID eventId);

  @Modifying
  @Query("update Seat s set s.availability=:availability where s.id=:seatId and s.eventId=:eventId")
  int updateAvailability(
      @Param("eventId") UUID eventId,
      @Param("seatId") UUID seatId,
      @Param("availability") Seat.Availability availability);
}
