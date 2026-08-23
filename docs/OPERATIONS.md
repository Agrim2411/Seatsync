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

Do not treat expected `409 SEAT_UNAVAILABLE` responses as server failures.

## Failure behavior

| Dependency | Behavior |
| --- | --- |
| Redis unavailable | New holds fail closed; PostgreSQL correctness is preserved |
| PostgreSQL unavailable | Mutating requests fail; no cache-only reservation is allowed |
| Kafka unavailable | State changes commit with outbox rows; projection becomes stale until publishing resumes |
| Event consumer poison record | Retries three times, then publishes to the `.DLT` topic |
| Payment timeout | Booking enters `PAYMENT_UNKNOWN`; reconciliation queries the provider simulator |
| gRPC confirmation timeout | An authorized payment moves into compensation and refund processing |

## Alerts

- Oldest outbox row exceeds 60 seconds.
- Any active hold is more than 30 seconds past expiry.
- Any `PAYMENT_UNKNOWN` booking exceeds 30 seconds.
- Any `REFUND_PENDING` booking exceeds five minutes.
- Kafka DLT receives a record.
- PostgreSQL connection pool is above 85% utilization for five minutes.

## Scaling

Gateway and event reads scale horizontally. Reservation instances may also scale horizontally because PostgreSQL constraints remain authoritative and Redis scripts are atomic. Kafka preserves ordering by aggregate ID. The outbox query uses `FOR UPDATE SKIP LOCKED` so multiple publishers can share work.

The single PostgreSQL primary remains a deliberate boundary. At larger scale, partition event ownership by `eventId`, isolate exceptionally hot events, and keep each event on one reservation shard. Do not introduce sharding until database telemetry justifies it.
