# Resume evidence worksheet

Use only statements supported by the repository and retained benchmark output.

## Defensible before benchmarking

- Built a five-service ticket reservation platform using Java, Spring Boot, PostgreSQL, Redis, Kafka, and gRPC.
- Implemented atomic, expiring seat holds using Redis Lua admission control, conditional PostgreSQL updates, ownership tokens, and database uniqueness constraints.
- Designed an idempotent booking workflow with payment authorization, timeout reconciliation, hold confirmation, compensating release/refund flows, and an at-least-once transactional outbox.
- Built an idempotent Kafka read-model projector with retry and dead-letter recovery for eventually consistent seat availability.

## Complete after running k6

- Prevented overselling under **[VUS] concurrent attempts for one seat**, producing exactly one successful hold with **[p95] ms** reservation latency.
- Sustained **[requests/second]** reservation attempts with **[error rate]** unexpected failures on **[machine and container limits]**.

Store the k6 JSON output, machine specification, date, and commit SHA together. Do not use target thresholds as achieved results.
