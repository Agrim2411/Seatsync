package io.seatsync.reservations;

import static io.seatsync.reservations.ReservationDtos.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations/holds")
public class ReservationController {
  private final ReservationService reservations;

  ReservationController(ReservationService reservations) {
    this.reservations = reservations;
  }

  @PostMapping
  ResponseEntity<HoldResponse> create(
      @RequestHeader(name = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody CreateHoldRequest request) {
    HoldResponse response = reservations.createHold(key, request);
    return ResponseEntity.created(URI.create("/api/reservations/holds/" + response.holdId()))
        .body(response);
  }

  @GetMapping("/{holdId}")
  HoldResponse get(@PathVariable UUID holdId) {
    return reservations.get(holdId);
  }

  @PostMapping("/{holdId}/confirm")
  HoldResponse confirm(@PathVariable UUID holdId, @Valid @RequestBody ConfirmHoldRequest request) {
    return reservations.confirm(holdId, request.customerId());
  }

  @DeleteMapping("/{holdId}")
  ResponseEntity<Void> release(@PathVariable UUID holdId, @RequestParam UUID customerId) {
    reservations.release(holdId, customerId);
    return ResponseEntity.noContent().build();
  }
}
