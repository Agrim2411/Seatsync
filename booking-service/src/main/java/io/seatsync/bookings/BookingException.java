package io.seatsync.bookings;

class BookingException extends RuntimeException {
  private final String code;

  BookingException(String c, String m) {
    super(m);
    code = c;
  }

  String getCode() {
    return code;
  }
}
