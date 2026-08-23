import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const won = new Counter('seat_hold_won');
const unavailable = new Counter('seat_hold_unavailable');
const unexpected = new Rate('unexpected_response');
const holdLatency = new Trend('hold_latency', true);

export const options = {
  scenarios: {
    same_seat_contention: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 500),
      iterations: 1,
      maxDuration: '90s',
    },
  },
  thresholds: {
    seat_hold_won: ['count==1'],
    unexpected_response: ['rate==0'],
    hold_latency: ['p(95)<2000'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8082';
const eventId = '10000000-0000-0000-0000-000000000001';
const seatId = __ENV.SEAT_ID || '20000000-0000-0000-0000-000000000001';

export default function () {
  const customer = `30000000-0000-0000-0000-${String(exec.vu.idInTest).padStart(12, '0')}`;
  const started = Date.now();
  const response = http.post(`${baseUrl}/api/reservations/holds`, JSON.stringify({ eventId, seatId, customerId: customer }), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `contention-${Date.now()}-${exec.vu.idInTest}` },
  });
  holdLatency.add(Date.now() - started);
  if (response.status === 201) won.add(1);
  else if (response.status === 409) unavailable.add(1);
  else unexpected.add(1);
  check(response, { 'expected contention outcome': r => r.status === 201 || r.status === 409 });
}
