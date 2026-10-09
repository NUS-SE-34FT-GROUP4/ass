import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';
import { base, mode, data, options as configuredOptions } from './config.js';
export const options = configuredOptions;
const errors = new Rate('business_errors');
const operations = new Counter('business_operations');
const chatLatency = new Trend('chat_roundtrip_ms', true);
function request(method, path, user, body, name, validate = () => true) {
  const r = http.request(method, base + path, body === undefined ? null : JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', ...(user ? { Authorization: `Bearer ${user.token}` } : {}) },
    tags: { name, kind: method === 'GET' ? 'read' : 'write' }, timeout: '10s',
  });
  let value;
  try { value = r.json(); } catch (_) { value = null; }
  const ok = check(r, { [name]: () => r.status >= 200 && r.status < 300 && validate(value) });
  errors.add(!ok);
  return ok ? value : null;
}
function chat(user) {
  // Spring SockJS WebSocket transport: JSON arrays wrap STOMP frames.
  const url = base.replace(/^http/, 'ws') + `/ws/000/load-${__VU}-${__ITER}/websocket`;
  const marker = `load-${__VU}-${__ITER}-${Date.now()}`;
  let delivered = false;
  let sentAt = 0;
  const frame = (s) => JSON.stringify([s]);
  const response = ws.connect(url, { tags: { name: 'chat', kind: 'chat' } }, (socket) => {
    socket.on('message', (raw) => {
      if (raw === 'o') {
        socket.send(frame(`CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ${user.token}\n\n\0`));
      } else if (raw.startsWith('a')) {
        for (const msg of JSON.parse(raw.slice(1))) {
          if (msg.startsWith('CONNECTED')) {
            socket.send(frame('SUBSCRIBE\nid:load\ndestination:/user/queue/private\nreceipt:subscribed\n\n\0'));
          } else if (msg.startsWith('RECEIPT') && msg.includes('receipt-id:subscribed')) {
            sentAt = Date.now();
            socket.send(frame('SEND\ndestination:/app/chat.sendMessage\ncontent-type:application/json\n\n' +
              JSON.stringify({ recipient: user.username, content: marker }) + '\0'));
          } else if (msg.startsWith('MESSAGE') && msg.includes(marker)) {
            delivered = true;
            chatLatency.add(Date.now() - sentAt);
            socket.close();
          } else if (msg.startsWith('ERROR')) socket.close();
        }
      }
    });
    socket.setTimeout(() => socket.close(), 10000);
  });
  errors.add(!check(response, { 'chat received matching message': r => r && r.status === 101 && delivered }));
}
export function setup() {
  if (mode !== 'search' && data.users.some(u => !u.token || u.token.startsWith('REPLACE'))) {
    throw new Error('Replace example JWTs with valid staging test tokens');
  }
  if (mode === 'mixed' && __ENV.ENABLE_WRITES !== 'true') throw new Error('Mixed requires ENABLE_WRITES=true and disposable funded test data');
}
export default function () {
  const user = data.users[__VU - 1];
  const id = data.productIds[Math.floor(Math.random() * data.productIds.length)];
  const keyword = encodeURIComponent(data.keywords[Math.floor(Math.random() * data.keywords.length)]);
  const search = () => request('GET', `${__ENV.SEARCH_PATH || '/api/products/search'}?keyword=${keyword}&page=0&size=20`, null, undefined, 'search', Array.isArray);
  // Percentages are business-operation probabilities, not HTTP-request ratios.
  const roll = mode === 'search' ? 0.4 : Math.random();
  if (roll < 0.3) {
    operations.add(1, { operation: 'browse' });
    request('GET', '/api/products', null, undefined, 'browse', Array.isArray);
  } else if (roll < 0.6) {
    operations.add(1, { operation: 'search' }); search();
  } else if (roll < 0.85) {
    operations.add(1, { operation: 'detail_recommendation' });
    request('GET', `/api/products/${id}`, user, undefined, 'detail', v => v && Number(v.id) === Number(id));
    request('GET', `/api/recommendations/products/${id}/similar?limit=10`, user, undefined, 'similar', Array.isArray);
    request('GET', '/api/recommendations/for-you?limit=10', user, undefined, 'personalized', Array.isArray);
  } else if (mode === 'smoke') {
    operations.add(1, { operation: 'cart_read' });
    request('GET', '/api/cart/list', user, undefined, 'cart_list', Array.isArray);
  } else if (roll < 0.9) {
    operations.add(1, { operation: 'cart' });
    if (request('POST', '/api/cart/add', user, { productId: id, quantity: 1 }, 'cart_add', v => v && v.success === true)) {
      request('GET', '/api/cart/list', user, undefined, 'cart_list', Array.isArray);
      request('DELETE', `/api/cart/remove/${id}`, user, undefined, 'cart_remove', v => v && v.success === true);
    }
  } else if (roll < 0.95) {
    operations.add(1, { operation: 'chat' }); chat(user);
  } else {
    operations.add(1, { operation: 'purchase' });
    const order = request('POST', '/api/orders/buy-now', user, { productId: id, quantity: 1 }, 'buy_now', v => v && v.id);
    if (order) request('POST', `/api/orders/${order.id}/pay`, user,
      { paymentMethod: 'BALANCE', password: user.paymentPassword }, 'pay', v => v && v.status === 'PAID');
  }
  sleep(Number(__ENV.THINK_TIME || 1));
}
export function handleSummary(summary) {
  return { [__ENV.SUMMARY || './summary.json']: JSON.stringify({
    run: { mode, base, timestamp: new Date().toISOString(), revision: __ENV.REVISION || 'unknown',
      deployment: __ENV.DEPLOYMENT || 'unspecified', ramp: __ENV.RAMP || '5m', hold: __ENV.HOLD || (mode === 'smoke' ? '30s' : '15m'), vus: Number(__ENV.VUS || (mode === 'smoke' ? 2 : 200)) },
    ...summary,
  }, null, 2) };
}
