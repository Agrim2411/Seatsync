# SeatSync

SeatSync is a clean-room, event-driven ticket reservation platform for high-demand event openings. Its central invariant is simple: **a seat can be sold at most once**, even when concurrent requests, retries, delayed payments, or expired holds occur at the same time.

The repository is intentionally production-oriented without pretending to be a production deployment. It includes correctness boundaries, failure handling, observability, integration tests, and load-test scenarios. Performance numbers belong in documentation only after they have been reproduced on a stated environment.

## Architecture

```mermaid
flowchart TD
    C[Web or mobile client] --> G[Gateway]
    G --> E[Event service]
    G --> R[Reservation service]
    G --> B[Booking service]
    B --> R
    B --> P[Payment service]
    R --> K[(Kafka)]
    K --> E
    E --> PG[(PostgreSQL)]
    R --> PG
    B --> PG
    P --> PG
    R --> RD[(Redis)]
```

| Service | Responsibility |
| --- | --- |
| `gateway-service` | Routing, request correlation, JWT resource-server integration, and Redis-backed admission control |
| `event-service` | Events, venues, sections, and seat-map read APIs |
| `reservation-service` | Atomic expiring holds, authoritative seat state, expiration, and confirmation |
| `booking-service` | Idempotent checkout orchestration and compensating actions |
| `payment-service` | Deterministic payment-provider simulator with success, decline, timeout, and duplicate request modes |

## Correctness model

PostgreSQL is the source of truth. Redis is a fast admission gate and TTL index; it is never the final authority for ownership.

1. A compare-and-set Redis script rejects obvious contention cheaply.
2. A conditional PostgreSQL update changes `AVAILABLE` to `HELD`. Only one transaction can update the row.
3. A unique active-hold constraint provides a second database boundary.
4. If the database transaction fails, ownership-scoped Redis cleanup removes only the caller's token.
5. Confirmation requires the original hold token and an unexpired database record.
6. The expiration worker locks due holds, releases seat state, and emits an outbox event in the same transaction.
7. Booking and payment endpoints require idempotency keys; replays return the original result.

See [the invariants](docs/INVARIANTS.md) and [architecture decisions](docs/adr/0001-reservation-consistency.md).

## Technology

Java 21, Spring Boot, PostgreSQL, Redis, Kafka, REST/OpenAPI, gRPC/Protocol Buffers, Flyway, transactional outbox, Prometheus, Grafana, Docker Compose, Kubernetes/Helm, and k6.

## Run locally

Requirements: Java 21, Maven 3.9+, and Docker with Compose.

```bash
docker compose up -d postgres redis kafka
mvn clean package -DskipTests
mvn -pl event-service spring-boot:run
mvn -pl reservation-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl gateway-service spring-boot:run
```

The gateway listens on `http://localhost:8080`. HTTP application services expose OpenAPI documents through `/swagger-ui.html`.

Live seat changes are available over WebSocket at `ws://localhost:8081/ws/events/{eventId}/seats`. The event service projects reservation events idempotently before broadcasting availability changes.

### Hold a seat

```bash
curl -X POST http://localhost:8080/api/reservations/holds \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-hold-1' \
  -d '{"eventId":"10000000-0000-0000-0000-000000000001","seatId":"20000000-0000-0000-0000-000000000001","customerId":"30000000-0000-0000-0000-000000000001"}'
```

### Confirm a booking

```bash
curl -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-booking-1' \
  -d '{"holdId":"<hold-id>","customerId":"30000000-0000-0000-0000-000000000001","amountMinor":499900,"currency":"INR","paymentMethodToken":"pm_success"}'
```

## Verification

```bash
mvn clean package -DskipTests
docker compose --profile observability up -d
k6 run --summary-export=load-tests/results/contention.json load-tests/contention.js
```

The k6 contention benchmark submits concurrent hold attempts for one seat and requires exactly one winner. See [the benchmark instructions](load-tests/README.md). No performance result is claimed until this workload has been run and its result retained.

### Run without Docker on your laptop

After pushing the repository to GitHub, open **Actions → cloud contention benchmark → Run workflow**. Choose a concurrency level and start the workflow. GitHub builds the reservation stack on a hosted Linux runner, executes k6, and provides the JSON result under the workflow's **Artifacts** section. No project process runs on the local computer.

## Repository guide

- `docs/` — invariants, API lifecycle, ADRs, and operational notes
- `infra/` — database bootstrap, dashboards, and local configuration
- `deploy/helm/` — Kubernetes deployment chart
- `load-tests/` — reproducible high-contention workload
- `.github/workflows/` — Maven compilation and container-build checks

The Helm chart deploys application services and expects PostgreSQL, Redis, Kafka, and a Kubernetes metrics server to exist in the target environment. Override dependency addresses and the database secret in a private values file.

Review [operations](docs/OPERATIONS.md) and [security boundaries](SECURITY.md) before deploying beyond local development.

## License

MIT
