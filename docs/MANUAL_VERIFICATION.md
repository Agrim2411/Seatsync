# Manual verification

## Complete local smoke test

This exercises the running services through the gateway and verifies the complete successful path:

```text
gateway -> reservation -> Redis/PostgreSQL -> booking -> payment
        -> reservation gRPC confirmation -> outbox -> Kafka -> event read model
```

Prerequisites are Rancher Desktop with its container engine running, the Docker Compose plugin,
`curl`, and `jq`. From the repository root:

```bash
docker compose up -d --build
./scripts/smoke-test.sh
```

The script waits for all five services, selects an available seeded seat, creates and replays a
hold, completes a successful booking, and waits until Kafka changes that seat to `BOOKED` in the
event-service read model. A successful run prints the generated hold, booking, and seat IDs.

Inspect failures with:

```bash
docker compose ps
docker compose logs --tail=200
```

Stop the stack while preserving PostgreSQL data with `docker compose down`. After all three seeded
seats have been used, reset the local demo databases with `docker compose down -v`; this permanently
deletes the Compose-managed PostgreSQL volume and the demo booking data inside it.

The same API smoke test is not currently a GitHub workflow. GitHub runs the unit tests, image builds,
and the separately triggered contention benchmark. Run this complete workflow locally because it is
intended to teach and demonstrate the request path interactively.

## Health

```bash
for port in 8080 8081 8082 8083 8084; do curl --fail "http://localhost:${port}/actuator/health"; done
```

## Idempotency

Send the same hold request twice with the same `Idempotency-Key`. Both responses must contain the same `holdId`. Change the seat while retaining the key; the service must return `409 IDEMPOTENCY_KEY_REUSED`.

## Expiration

Start the reservation service with `HOLD_TTL=PT10S`, create a hold, wait beyond the deadline, and fetch it. The state must become `EXPIRED`, and another customer must then be able to hold the seat.

## Payment compensation

- Use `pm_decline` to verify that the booking fails and releases the hold.
- Let a hold expire before checkout with `pm_success`; the booking must reach `REFUNDED` after payment authorization cannot confirm the seat.
- Use `pm_timeout` to exercise the booking service's unavailable-payment path.
- Stop the booking service after a booking row is created but before checkout finishes, then restart it. The reconciliation worker must recover the `PENDING` booking from the payment service's recorded outcome.
- Make the payment service unavailable during compensation and verify the booking remains `REFUND_PENDING`; after restoring the payment service, the reconciliation worker must advance it to `REFUNDED`.

## Kafka outbox

After a hold transition, inspect `outbox_events`. The row must be committed with the state change and later receive a non-null `published_at`. Consume `reservation-events` to inspect the emitted payload.
