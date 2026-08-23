package io.seatsync.reservations;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from SeatHold h where h.id = :id")
  java.util.Optional<SeatHold> findByIdForUpdate(
      @org.springframework.data.repository.query.Param("id") UUID id);

  List<SeatHold> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
      HoldStatus status, Instant deadline);
}
