# SeatSync verified-runtime addendum

This addendum updates the SeatSync End-to-End Interview Guide with the runtime verification work completed after the original PDF was generated. It supplements the original architecture and request traces; it does not replace them.

Verified baseline: commit `22522f2` on 24 August 2026. Java 21 remains the required runtime.

## What this addendum supersedes

- The original GitHub Actions section describes the build and contention benchmark but predates the complete hosted smoke workflow.
- The original limitations table says there are no automated test sources. SeatSync now has 12 focused tests covering the gateway context and core reservation, booking recovery, and payment behavior.
- The original debugging playbook predates actionable smoke-test annotations and per-service startup diagnostics.
- The original file map predates `.github/workflows/smoke.yml`, `scripts/smoke-test.sh`, and the forward-only booking/payment V2 migrations.

## The complete end-to-end smoke test

The smoke test answers a different question from compilation or the contention benchmark: can a real customer journey cross every implemented runtime boundary and reach the expected durable and projected state?

The verified request path is:

1. The script waits for health on gateway `8080`, event `8081`, reservation `8082`, booking `8083`, and payment `8084`.
2. It reads the seeded event seat map through the gateway and selects an `AVAILABLE` seat.
3. It posts a hold with an `Idempotency-Key` through the gateway.
4. Reservation uses Redis as the fast admission gate and PostgreSQL as the authoritative single-winner boundary.
5. The script repeats the identical hold request and verifies that the same `holdId` is returned.
6. It posts a booking with `pm_success` through the gateway.
7. Booking persists the workflow, calls payment over internal REST, and receives an authorized payment.
8. Booking confirms the hold through the generated gRPC/protobuf contract.
9. Reservation changes the authoritative seat state to `BOOKED` and writes an outbox event in the same local transaction.
10. The outbox publisher sends the event to Kafka.
11. Event-service consumes it, uses its inbox to make processing idempotent, and updates the seat-map projection.
12. The script polls the public seat map until the selected seat becomes `BOOKED`.

The final polling step matters. A confirmed booking response alone would not prove that the outbox, Kafka broker, consumer, inbox, and read-model update all worked.

## How the hosted workflow runs

The workflow file is `.github/workflows/smoke.yml`. GitHub creates a temporary Ubuntu runner with Docker available, checks out the selected commit, and executes:

```bash
docker compose up -d --build
./scripts/smoke-test.sh
```

The workflow always displays container state. If a step fails, it displays service logs. It always runs `docker compose down -v --remove-orphans`, so databases and containers do not survive the job.

The workflow can be started manually from **Actions -> end-to-end smoke test -> Run workflow**. It also runs on pushes to `main` when runtime inputs change: the workflow itself, Dockerfile, Compose file, root or module POMs, service source/resources, or the smoke script.

This hosted path is useful when a corporate proxy prevents the local container engine from trusting Docker registry certificates. That certificate problem affects local image download; it does not imply that SeatSync cannot run. GitHub's runner provides an independent clean Linux environment.

## Four verification layers and what each proves

### Focused tests

The Maven build runs 12 focused tests. They check deterministic domain and recovery behavior without requiring the complete distributed runtime. They are fast and identify local regressions, but they do not prove container networking or cross-service wiring.

### Build workflow

The regular build compiles the Java 21 multi-module reactor, generates protobuf/gRPC sources, runs tests, packages the services, and builds each of the five Docker images. It proves clean-runner reproducibility and image construction, but it does not execute a booking journey.

### Contention benchmark

The k6 workflow sends many distinct customer requests to one fresh seat and checks that exactly one hold wins while the other supported outcomes are conflicts. It focuses on the reservation concurrency invariant and produces a retained JSON artifact. It intentionally bypasses unrelated services and must not be described as a full-system capacity benchmark.

### End-to-end smoke test

The smoke workflow uses one successful customer journey to verify the five services and all implemented runtime dependencies together. It proves integration for that scenario and commit. It is not a broad performance test, chaos test, security audit, or proof that every failure path works.

Interview answer: tests isolate logic, the build proves clean compilation and images, the contention benchmark stresses the single-winner invariant, and the smoke test proves that the complete happy path is wired correctly.

## Runtime defects the smoke test exposed

These were configuration and integration defects that compilation alone could not reveal. They are useful interview examples because each demonstrates a real boundary.

### Duplicate gateway security bean names

The configuration class and its `@Bean` method originally resolved to the same Spring bean name, preventing the gateway context from starting. The development and production filter-chain methods now have distinct descriptive names. A gateway context-startup test protects this boundary.

Lesson: independently compilable code can still fail during dependency-injection container creation.

### Database type mismatch

The booking and payment V1 migrations created `currency` as PostgreSQL `CHAR(3)`, while the JPA entities declared a length-three `String`, which Hibernate validates as `VARCHAR(3)`. Hibernate correctly refused startup because the durable schema and object mapping disagreed.

Forward-only V2 Flyway migrations alter both columns to `VARCHAR(3)`. V1 was not rewritten, so databases that already recorded V1 can advance safely through normal migration history.

Lesson: schema validation is valuable because it fails at startup instead of allowing a latent persistence mismatch into traffic.

### Missing Java parameter metadata

SeatSync imports Spring Boot dependency management through a custom parent POM rather than inheriting the Spring Boot parent plugin configuration. The compiler therefore did not initially retain method parameter names.

Spring MVC endpoints such as `@PathVariable UUID eventId` rely on the reflected name when the annotation does not explicitly repeat it. The root compiler configuration now sets:

```xml
<release>${java.version}</release>
<parameters>true</parameters>
```

This emits Java's `-parameters` metadata for every module. Java stays at version 21, and the fix covers all implicit `@PathVariable` and `@RequestParam` arguments consistently.

Lesson: importing a BOM controls dependency versions; it does not automatically inherit every build-plugin default from a framework parent.

### CI trigger and diagnostic coverage

The first smoke trigger filter watched only a narrow set of files. It now watches all runtime inputs so a POM or Java change cannot silently skip integration verification.

The smoke script now reports the failing command, HTTP status/body, unhealthy service, and relevant startup logs as GitHub annotations. This made failures understandable without searching hundreds of mixed container log lines.

Lesson: a verification workflow needs both correct trigger coverage and failure output that points to the responsible boundary.

## How to run and read the result

In GitHub:

1. Open the repository's **Actions** tab.
2. Select **end-to-end smoke test**.
3. Choose **Run workflow**, select `main`, and start it.
4. Open the `smoke` job and expand **Exercise complete booking path**.
5. A successful run prints `holdId`, `bookingId`, `seatId`, and final availability `BOOKED`.

For a local compatible container engine:

```bash
docker compose up -d --build
./scripts/smoke-test.sh
docker compose down -v --remove-orphans
```

Use `docker compose down -v` only when resetting disposable Compose data; it deletes the project-managed PostgreSQL volume.

## Debugging sequence for a failed smoke run

Start at the earliest failed boundary rather than reading every log at once:

1. If a port never becomes healthy, inspect the named service's last startup cause. Common boundaries are Spring context creation, Flyway, Hibernate schema validation, or dependency connectivity.
2. If the seat-map GET fails, compare the gateway response with a direct event-service response. This distinguishes route/security behavior from an event-service error.
3. If hold creation fails unexpectedly, inspect Redis availability, reservation inventory state, and the returned domain error code.
4. If booking does not confirm, inspect the booking state, payment row, and reservation gRPC availability.
5. If booking confirms but the seat map stays stale, inspect unpublished outbox rows, Kafka health, consumer lag/retries, and the event inbox.

The workflow's `if: failure()` log step is diagnostic only. Cleanup still runs under `if: always()`, so a red job does not leak hosted containers or volumes.

## Current verified evidence

At commit `22522f2`:

- Java 21 `mvn clean install -T8` completed successfully.
- All seven Maven reactor projects succeeded.
- 12 focused tests passed.
- `docker compose config` validated the runtime topology.
- The regular GitHub build succeeded: `https://github.com/Agrim2411/SeatSync/actions/runs/32705074923`.
- The complete hosted smoke test succeeded: `https://github.com/Agrim2411/SeatSync/actions/runs/32705074946`.

This evidence is intentionally specific to the recorded commit and workflows. Do not turn it into an unsupported throughput, latency, availability, or production-readiness claim.

## Updated limitations statement

Replace "no automated test sources" with this explanation:

SeatSync has a deliberately small focused test suite plus a hosted end-to-end smoke workflow and an opt-in contention benchmark. The remaining testing gap is broader database/infrastructure integration coverage for failure paths such as expiry races, Kafka recovery, and reconciliation under process interruption. Add those only when they improve confidence without making the project harder to own.

## Files to read for this update

- `.github/workflows/smoke.yml` - hosted runner lifecycle, trigger paths, diagnostics, and cleanup.
- `scripts/smoke-test.sh` - exact API journey and assertions.
- `pom.xml` - Java 21 and compiler parameter metadata.
- `booking-service/src/main/resources/db/migration/V2__use_varchar_currency.sql` - booking schema evolution.
- `payment-service/src/main/resources/db/migration/V2__use_varchar_currency.sql` - payment schema evolution.
- `gateway-service/src/main/java/io/seatsync/gateway/DevSecurity.java` - local filter chain.
- `gateway-service/src/main/java/io/seatsync/gateway/ProductionSecurity.java` - production JWT filter chain.
- `gateway-service/src/test/java/io/seatsync/gateway/GatewayApplicationTest.java` - gateway startup regression coverage.

## Interview-ready summary

I verify SeatSync at four levels. Focused tests exercise deterministic domain and recovery logic. The regular GitHub build compiles Java 21, generates protobuf code, packages all modules, and builds five images. The contention workflow checks the one-winner invariant for a hot seat. The complete smoke workflow starts the whole Compose topology and traces one booking through the gateway, reservation, Redis/PostgreSQL, payment, gRPC confirmation, outbox, Kafka, and the event read model. The smoke test found real DI, schema, and compiler-metadata integration issues that compilation alone could not expose; each was fixed at the responsible boundary and the final hosted run passed.
