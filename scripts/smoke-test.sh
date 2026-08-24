#!/usr/bin/env bash
set -Eeuo pipefail

report_failed_command() {
  local exit_code="$?"
  echo "::error title=SeatSync smoke command failed::Line ${BASH_LINENO[0]} exited ${exit_code}: ${BASH_COMMAND}" >&2
}

fail() {
  echo "::error title=SeatSync smoke test failed::$1" >&2
  echo "$1" >&2
  exit 1
}

trap report_failed_command ERR

base_url="${BASE_URL:-http://localhost:8080}"
event_id="10000000-0000-0000-0000-000000000001"
customer_id="30000000-0000-0000-0000-000000000001"

for command in curl jq; do
  if ! command -v "$command" >/dev/null 2>&1; then
    fail "Required command is missing: ${command}"
  fi
done

echo "Waiting for SeatSync services..."
for port in 8080 8081 8082 8083 8084; do
  ready=false
  for attempt in {1..60}; do
    if curl --silent --fail "http://localhost:${port}/actuator/health" >/dev/null; then
      ready=true
      break
    fi
    sleep 1
  done
  if [[ "$ready" != "true" ]]; then
    case "$port" in
      8080) service="gateway-service" ;;
      8081) service="event-service" ;;
      8082) service="reservation-service" ;;
      8083) service="booking-service" ;;
      8084) service="payment-service" ;;
    esac
    relevant_log="$({
      docker compose logs --no-color --tail=150 "$service" \
        | grep -E 'APPLICATION FAILED|BeanDefinitionOverrideException|Schema-validation|Caused by:| ERROR ' \
        | tail -n 1
    } || true)"
    echo "Inspect logs with: docker compose logs --tail=200" >&2
    fail "Service ${service} on port ${port} did not become healthy within 60 seconds. Last relevant log: ${relevant_log:-not found}"
  fi
done

seats_response_file="$(mktemp)"
seats_status="$(
  curl --silent --show-error \
    --output "$seats_response_file" \
    --write-out '%{http_code}' \
    "${base_url}/api/events/${event_id}/seats"
)"
seats="$(<"$seats_response_file")"
rm -f "$seats_response_file"

if [[ "$seats_status" != "200" ]]; then
  relevant_logs="$({
    docker compose logs --no-color --tail=80 gateway-service event-service \
      | grep -E ' ERROR |Exception|status=|Connection refused' \
      | tail -n 4
  } || true)"
  fail "Seat-map request returned HTTP ${seats_status}. Body: ${seats:-empty}. Relevant logs: ${relevant_logs:-not found}"
fi

seat_id="$(jq -r '.[] | select(.availability == "AVAILABLE") | .id' <<<"$seats" | head -n 1)"
price_minor="$(jq -r --arg seatId "$seat_id" '.[] | select(.id == $seatId) | .priceMinor' <<<"$seats")"

if [[ -z "$seat_id" || "$seat_id" == "null" ]]; then
  echo "Reset local demo data with: docker compose down -v" >&2
  fail "No AVAILABLE demo seat remains"
fi

run_id="$(date +%s)"
hold_key="smoke-hold-${run_id}"
booking_key="smoke-booking-${run_id}"
hold_request="$(
  jq -n \
    --arg eventId "$event_id" \
    --arg seatId "$seat_id" \
    --arg customerId "$customer_id" \
    '{eventId: $eventId, seatId: $seatId, customerId: $customerId}'
)"

echo "Creating a hold for seat ${seat_id}..."
hold="$(
  curl --silent --show-error --fail-with-body \
    -X POST "${base_url}/api/reservations/holds" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${hold_key}" \
    -d "$hold_request"
)"
hold_id="$(jq -er '.holdId' <<<"$hold")"
jq -e '.status == "ACTIVE"' <<<"$hold" >/dev/null

echo "Replaying the hold request to verify idempotency..."
replayed_hold="$(
  curl --silent --show-error --fail-with-body \
    -X POST "${base_url}/api/reservations/holds" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${hold_key}" \
    -d "$hold_request"
)"
replayed_hold_id="$(jq -er '.holdId' <<<"$replayed_hold")"
if [[ "$replayed_hold_id" != "$hold_id" ]]; then
  fail "Idempotency replay returned another hold"
fi

booking_request="$(
  jq -n \
    --arg holdId "$hold_id" \
    --arg customerId "$customer_id" \
    --argjson amountMinor "$price_minor" \
    '{holdId: $holdId, customerId: $customerId, amountMinor: $amountMinor, currency: "INR", paymentMethodToken: "pm_success"}'
)"

echo "Authorizing payment and confirming the hold..."
booking="$(
  curl --silent --show-error --fail-with-body \
    -X POST "${base_url}/api/bookings" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${booking_key}" \
    -d "$booking_request"
)"
booking_id="$(jq -er '.bookingId' <<<"$booking")"
jq -e '.status == "CONFIRMED"' <<<"$booking" >/dev/null

echo "Waiting for Kafka to update the event-service read model..."
projected=false
for attempt in {1..20}; do
  availability="$(
    curl --silent --fail "${base_url}/api/events/${event_id}/seats" \
      | jq -r --arg seatId "$seat_id" '.[] | select(.id == $seatId) | .availability'
  )"
  if [[ "$availability" == "BOOKED" ]]; then
    projected=true
    break
  fi
  sleep 1
done

if [[ "$projected" != "true" ]]; then
  fail "Booking succeeded, but the seat-map projection did not become BOOKED within 20 seconds"
fi

echo
echo "SeatSync smoke test passed."
echo "  holdId:    ${hold_id}"
echo "  bookingId: ${booking_id}"
echo "  seatId:    ${seat_id}"
echo "  final read-model availability: BOOKED"
