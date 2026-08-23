package io.seatsync.reservations;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {}
