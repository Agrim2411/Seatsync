CREATE TABLE seat_inventory (
    seat_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('AVAILABLE','HELD','BOOKED')),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_inventory_event_state ON seat_inventory(event_id, state);

CREATE TABLE seat_holds (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    seat_id UUID NOT NULL REFERENCES seat_inventory(seat_id),
    customer_id UUID NOT NULL,
    ownership_token VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','CONFIRMED','RELEASED','EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_active_hold_per_seat ON seat_holds(seat_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_holds_expiry ON seat_holds(expires_at) WHERE status = 'ACTIVE';

CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(180) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    hold_id UUID NOT NULL REFERENCES seat_holds(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

INSERT INTO seat_inventory(seat_id, event_id, state) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'AVAILABLE'),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'AVAILABLE'),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'AVAILABLE');
