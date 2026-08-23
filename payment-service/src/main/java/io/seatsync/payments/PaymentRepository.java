package io.seatsync.payments;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByIdempotencyKey(String key);

  Optional<Payment> findByBookingId(UUID bookingId);
}
