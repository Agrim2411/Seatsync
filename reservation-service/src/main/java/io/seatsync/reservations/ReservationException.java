package io.seatsync.reservations;

class ReservationException extends RuntimeException {
  private final String code;

  ReservationException(String code, String message) {
    super(message);
    this.code = code;
  }

  String getCode() {
    return code;
  }
}
