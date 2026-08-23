CREATE TABLE payments (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    booking_id UUID NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('AUTHORIZED','DECLINED','REFUNDED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
