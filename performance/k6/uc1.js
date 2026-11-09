// UC1 - order details: GET /api/orders/{id}
// docker compose --profile performance run --rm -e VUS=100 -e ITERATIONS=500 k6 run /scripts/uc1.js

import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 1),
};

const baseUrl = __ENV.BASE_URL || 'http://gateway-service:8080';
const orderId = __ENV.ORDER_ID || 1;

export default function () {
  const res = http.get(`${baseUrl}/api/orders/${orderId}`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}