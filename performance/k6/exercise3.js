// docker compose run --rm k6 run -u 1 -d 60s /scripts/exercise3.js
import http from 'k6/http';

const body = JSON.stringify({
  customerId: 'C-1001',
  items: [
    { sku: 'SKU-1001', quantity: 2 },
    { sku: 'SKU-4001', quantity: 1 },
  ],
});

function italianTime(d) {
  return new Date(d.getTime() + 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
}

export function setup() {
  const now = new Date();
  console.log(`start: ${Math.floor(now / 1000)} ${italianTime(now)}`);
}

export function teardown() {
  const now = new Date();
  console.log(`end: ${Math.floor(now / 1000)} ${italianTime(now)}`);
}

export default function () {
  http.post('http://gateway-service:8080/api/quotes', body, {
    headers: { 'Content-Type': 'application/json' },
  });
}