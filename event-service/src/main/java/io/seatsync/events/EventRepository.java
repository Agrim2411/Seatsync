package io.seatsync.events;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EventRepository extends JpaRepository<Event, UUID> {}
