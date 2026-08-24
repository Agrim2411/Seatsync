package io.seatsync.bookings;

import static io.seatsync.bookings.BookingDtos.CreateBookingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BookingStoreTest {
  private final BookingRepository bookings = mock(BookingRepository.class);
  private final BookingStore store = new BookingStore(bookings);

  @Test
  void requiresAnIdempotencyKey() {
    assertThatThrownBy(() -> store.start(" ", request("pm_success")))
        .isInstanceOf(BookingException.class)
        .extracting(error -> ((BookingException) error).getCode())
        .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
  }

  @Test
  void returnsTheOriginalBookingWhenTheSameRequestIsReplayed() {
    CreateBookingRequest request = request("pm_success");
    AtomicReference<Booking> saved = new AtomicReference<>();
    when(bookings.findByIdempotencyKey("booking-key"))
        .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
    when(bookings.save(any(Booking.class)))
        .thenAnswer(
            invocation -> {
              Booking booking = invocation.getArgument(0);
              saved.set(booking);
              return booking;
            });

    Booking first = store.start("booking-key", request);
    Booking replay = store.start("booking-key", request);

    assertThat(replay).isSameAs(first);
    verify(bookings, times(1)).save(any());
  }

  @Test
  void rejectsAnIdempotencyKeyReusedForAnotherRequest() {
    CreateBookingRequest original = request("pm_success");
    when(bookings.findByIdempotencyKey("booking-key")).thenReturn(Optional.empty());
    when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    Booking first = store.start("booking-key", original);
    when(bookings.findByIdempotencyKey("booking-key")).thenReturn(Optional.of(first));

    assertThatThrownBy(() -> store.start("booking-key", request("pm_decline")))
        .isInstanceOf(BookingException.class)
        .extracting(error -> ((BookingException) error).getCode())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  private CreateBookingRequest request(String token) {
    return new CreateBookingRequest(
        UUID.fromString("10000000-0000-0000-0000-000000000001"),
        UUID.fromString("20000000-0000-0000-0000-000000000002"),
        2500,
        "USD",
        token);
  }
}
