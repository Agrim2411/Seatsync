# Manual verification

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
