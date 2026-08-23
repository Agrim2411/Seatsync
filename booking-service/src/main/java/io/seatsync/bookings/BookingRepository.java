package io.seatsync.bookings;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByIdempotencyKey(String key);

  java.util.List<Booking> findTop100ByStatusOrderByCreatedAtAsc(Booking.Status status);
}
