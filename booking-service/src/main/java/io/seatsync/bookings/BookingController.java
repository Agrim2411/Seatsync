package io.seatsync.bookings;

import static io.seatsync.bookings.BookingDtos.*;

import io.seatsync.contracts.ApiError;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
  private final BookingStore store;
  private final BookingOrchestrator orchestrator;

  BookingController(BookingStore store, BookingOrchestrator orchestrator) {
    this.store = store;
    this.orchestrator = orchestrator;
  }

  @PostMapping
  ResponseEntity<BookingResponse> create(
      @RequestHeader(name = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody CreateBookingRequest request) {
    Booking b = store.start(key, request);
    return ResponseEntity.status(
            b.getStatus() == Booking.Status.PENDING ? HttpStatus.CREATED : HttpStatus.OK)
        .body(orchestrator.checkout(b, request));
  }

  @GetMapping("/{id}")
  BookingResponse get(@PathVariable UUID id) {
    return BookingResponse.from(store.get(id));
  }

  @ExceptionHandler(BookingException.class)
  ResponseEntity<ApiError> errors(BookingException e) {
    return ResponseEntity.status(e.getCode().equals("BOOKING_NOT_FOUND") ? 404 : 409)
        .body(ApiError.of(e.getCode(), e.getMessage()));
  }
}
