CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    hold_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','PAYMENT_UNKNOWN','CONFIRMED','FAILED','REFUND_PENDING','REFUNDED')),
    payment_id UUID,
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
