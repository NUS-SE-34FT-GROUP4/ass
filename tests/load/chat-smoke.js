import { chat } from './marketplace.js';
import { data } from './config.js';
export const options = {
  scenarios: { chat: { executor: 'per-vu-iterations', vus: 2, iterations: 5, maxDuration: '1m' } },
  thresholds: { business_errors: ['rate==0'] },
};
export function setup() {
  if (__ENV.ENABLE_WRITES !== 'true') throw Error('Chat persists messages; ENABLE_WRITES=true required');
  if (data.users.length < 2 || data.users.slice(0,2).some(u => !u.token || u.token.startsWith('REPLACE'))) throw Error('Two valid fixture buyers required');
}
export default function () { chat(data.users[__VU-1]); }
