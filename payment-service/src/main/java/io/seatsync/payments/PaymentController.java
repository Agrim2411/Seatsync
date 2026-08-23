package io.seatsync.payments;

import static io.seatsync.payments.PaymentDtos.*;

import io.seatsync.contracts.ApiError;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/payments")
public class PaymentController {
  private final PaymentService service;

  PaymentController(PaymentService service) {
    this.service = service;
  }

  @PostMapping("/authorize")
  PaymentResponse authorize(
      @RequestHeader(name = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody AuthorizeRequest request) {
    return service.authorize(key, request);
  }

  @PostMapping("/{paymentId}/refund")
  PaymentResponse refund(@PathVariable UUID paymentId) {
    return service.refund(paymentId);
  }

  @GetMapping
  PaymentResponse byBooking(@RequestParam UUID bookingId) {
    return service.findByBooking(bookingId);
  }

  @ExceptionHandler(PaymentException.class)
  ResponseEntity<ApiError> errors(PaymentException e) {
    HttpStatus status =
        e.getCode().equals("PAYMENT_NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(ApiError.of(e.getCode(), e.getMessage()));
  }
}
