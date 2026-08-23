CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    venue VARCHAR(180) NOT NULL,
    sale_starts_at TIMESTAMPTZ NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    section VARCHAR(40) NOT NULL,
    row_label VARCHAR(20) NOT NULL,
    label VARCHAR(30) NOT NULL,
    price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
    availability VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' CHECK (availability IN ('AVAILABLE','HELD','BOOKED')),
    CONSTRAINT uk_event_seat_label UNIQUE (event_id, label)
);

CREATE TABLE event_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO events VALUES
('10000000-0000-0000-0000-000000000001', 'SeatSync Launch Concert', 'Bengaluru Arena', now() - interval '1 hour', now() + interval '30 days');

INSERT INTO seats(id, event_id, section, row_label, label, price_minor) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'A', '1', 'A-1', 499900),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'A', '1', 'A-2', 499900),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'A', '1', 'A-3', 499900);
