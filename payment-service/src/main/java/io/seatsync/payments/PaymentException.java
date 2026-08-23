package io.seatsync.payments;

class PaymentException extends RuntimeException {
  private final String code;

  PaymentException(String code, String message) {
    super(message);
    this.code = code;
  }

  String getCode() {
    return code;
  }
}
