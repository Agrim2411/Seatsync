package io.seatsync.events;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {
  private final EventRepository events;
  private final SeatRepository seats;

  EventController(EventRepository events, SeatRepository seats) {
    this.events = events;
    this.seats = seats;
  }

  @GetMapping
  public List<Event> list() {
    return events.findAll();
  }

  @GetMapping("/{eventId}")
  public ResponseEntity<Event> get(@PathVariable UUID eventId) {
    return ResponseEntity.of(events.findById(eventId));
  }

  @GetMapping("/{eventId}/seats")
  public List<Seat> seatMap(@PathVariable UUID eventId) {
    return seats.findAllByEventIdOrderBySectionAscRowLabelAscLabelAsc(eventId);
  }
}
