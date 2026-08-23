package io.seatsync.reservations;

import io.seatsync.contracts.ApiError;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ReservationErrorHandler {
  @ExceptionHandler(ReservationException.class)
  ResponseEntity<ApiError> reservation(ReservationException e) {
    HttpStatus status =
        switch (e.getCode()) {
          case "HOLD_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "IDEMPOTENCY_KEY_REQUIRED" -> HttpStatus.BAD_REQUEST;
          default -> HttpStatus.CONFLICT;
        };
    return ResponseEntity.status(status).body(ApiError.of(e.getCode(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "VALIDATION_FAILED", "Request validation failed", Instant.now(), Map.of()));
  }
}
