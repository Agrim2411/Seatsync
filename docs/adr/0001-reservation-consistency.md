# ADR 0001: PostgreSQL is the reservation authority

- Status: Accepted
- Date: 2026-08-24

## Context

A popular event creates a hot-key workload. Redis can admit contenders quickly and provide natural TTLs, but cache loss, failover, and asynchronous persistence make it an unsafe final authority for a paid seat.

## Decision

PostgreSQL owns seat state. Reservation uses a conditional update and a unique active-hold index inside one transaction. Redis stores a short-lived ownership token as an admission gate. Redis cleanup uses compare-and-delete so one request cannot remove another request's token.

## Consequences

- Database constraints protect correctness even when Redis is empty or unavailable.
- Redis absorbs losing contenders and supports low-cost TTL discovery.
- The reservation service requires compensation when Redis succeeds but the database transaction fails.
- A reconciliation job may rebuild Redis state from active database holds.
- The database remains a scaling boundary; event-level partitioning and primary ownership are future scale-out options.
