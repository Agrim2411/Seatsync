# ADR 0002: At-least-once events with idempotent consumers

- Status: Accepted
- Date: 2026-08-24

## Decision

Services write domain changes and outbox rows in the same PostgreSQL transaction. A CDC connector or publisher forwards the outbox to Kafka. Consumers record processed event IDs before applying non-idempotent side effects.

The platform does not claim end-to-end exactly-once delivery. Exactly-once business effects are achieved through database uniqueness, idempotency keys, and consumer inboxes.
