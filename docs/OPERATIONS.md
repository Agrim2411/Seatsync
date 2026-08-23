# Operations

## Service-level indicators

Track these before defining objectives:

| Signal | Reason |
| --- | --- |
| Hold success and contention rejection rate | Separates healthy demand from system errors |
| Reservation p50/p95/p99 latency | Detects hot rows, Redis latency, and database saturation |
| Expired active-hold backlog | Indicates a stuck expiration worker |
| Oldest unpublished outbox age | Detects Kafka or publisher failure |
| Kafka consumer lag and DLT rate | Detects a stale seat-map projection |
| `PAYMENT_UNKNOWN` age and count | Detects unresolved provider outcomes |
| Refund-pending age | Detects financial compensation risk |

The booking reconciliation worker checks stale `PENDING` and all `PAYMENT_UNKNOWN` bookings, then retries pending refunds. Scanning `PENDING` closes the crash window between creating a booking and recording the checkout result. `PAYMENT_RESULT_GRACE` defaults to 10 seconds, which is longer than this project's four-second maximum simulated payment delay; it also keeps recovery from racing an active checkout. Payment authorization and refund requests are idempotent, so duplicate worker execution does not issue a second financial effect. Increase that grace period if the simulator is replaced by a provider with a longer completion window.

Do not treat expected `409 SEAT_UNAVAILABLE` responses as server failures.

## Failure behavior

| Dependency | Behavior |
| --- | --- |
| Redis unavailable | New holds fail closed; PostgreSQL correctness is preserved |
| PostgreSQL unavailable | Mutating requests fail; no cache-only reservation is allowed |
| Kafka unavailable | State changes commit with outbox rows; projection becomes stale until publishing resumes |
| Event consumer poison record | Retries three times, then publishes to the `.DLT` topic |
| Payment timeout | Booking enters `PAYMENT_UNKNOWN`; reconciliation queries the provider simulator |
| Booking process interruption | Persisted `PENDING` bookings are reconciled against the payment service |
| gRPC confirmation timeout | An authorized payment moves into compensation and refund processing |

## Alerts

- Oldest outbox row exceeds 60 seconds.
- Any active hold is more than 30 seconds past expiry.
- Any `PENDING` booking exceeds 30 seconds.
- Any `PAYMENT_UNKNOWN` booking exceeds 30 seconds.
- Any `REFUND_PENDING` booking exceeds five minutes.
- Kafka DLT receives a record.
- PostgreSQL connection pool is above 85% utilization for five minutes.

## Scaling

Gateway and event reads scale horizontally. Reservation instances may also scale horizontally because PostgreSQL constraints remain authoritative and Redis scripts are atomic. Kafka preserves ordering by aggregate ID. The outbox query uses `FOR UPDATE SKIP LOCKED` so multiple publishers can share work.

The single PostgreSQL primary remains a deliberate boundary. At larger scale, partition event ownership by `eventId`, isolate exceptionally hot events, and keep each event on one reservation shard. Do not introduce sharding until database telemetry justifies it.
