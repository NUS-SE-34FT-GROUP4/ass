# Staging load tests

WP10 deliverable for CUI ZIJIAN. Scripts target actual current controllers and
can be reused after service extraction. Run from repository root. k6 v2.3.0 was
used for offline validation. Requires k6, and Node.js for fixture generation.

## Data preparation

`generate-fixtures.mjs` emits SQL for the existing schema plus a JSON fixture.
Defaults: 2,000 users (one seller, 1,999 buyers), 10,000 products at price 10,
10,000 units each and buyer balance 10,000,000. Reserved IDs start at 1,000,000;
plain INSERT rejects collisions. Use a disposable load-test database initialized
with the project's schema and roles. The generator does not apply SQL or reset
any database. High stock is intentional for sustained-load runs; oversell uses
a separate low-stock product. Confirm seed cardinality and seller separation.

Supply BCrypt hashes via `LOAD_PASSWORD_HASH` and `LOAD_PAYMENT_HASH`, and the
matching six-digit `LOAD_PAYMENT_PASSWORD`. Generate with:

```text
node tests/load/generate-fixtures.mjs
```

Apply `tests/load/generated/seed.sql` with your environment's database tooling.
Synchronize the search index through the current admin/manual indexing process,
then verify the keywords return results. Confirm recommendation preparation
with its owner. Never include fixture secrets or raw run output in a commit.

Authentication requires captcha. Load runs intentionally exclude login and
registration: provision valid JWTs in the fixture before testing. An environment
owner can optionally supply `JWT_SECRET` locally to the generator to produce
HS512 tokens matching current `JwtTokenProvider`, valid for two hours. Do not
share that secret or expose it in CLI arguments. This is fixture provisioning,
not a production authentication change. Without it, tokens remain placeholders.
Use tokens from the matching deployment and regenerate before they expire.

## Run

First run read-only smoke (two distinct users) to validate routing and tokens:

```text
k6 run -e BASE_URL=http://localhost -e FIXTURES=./generated/fixtures.json -e MODE=smoke tests/load/marketplace.js
```

Full mixed workload:

```text
k6 run -e BASE_URL=https://STAGING_HOST -e FIXTURES=./generated/fixtures.json -e MODE=mixed -e ENABLE_WRITES=true -e VUS=200 -e RAMP=5m -e HOLD=15m -e DEPLOYMENT=fixed-1 -e REVISION=COMMIT_SHA -e SUMMARY=summary-fixed.json tests/load/marketplace.js
```

`FIXTURES` resolves relative to `config.js`; summary path resolves from the working
directory. Writes modify balances, stock, cart and chat records. Reset the
disposable dataset between runs so comparison conditions match. Record actual
dataset counts, image digests, task sizes, region and load-generator location.

60% browse/search (30/30); 25% detail + similar/personalized recommendations;
10% cart/chat (5/5); 5% buy-now + simulated payment. Percentages describe business
operations; multi-request flows do not produce those exact HTTP percentages.
Each VU owns a separate buyer. Chat uses SockJS WebSocket transport and STOMP
authentication/subscription receipt, sends to the same test user and requires
the matching message back. This exercises persistence and delivery on one
connection; cross-instance recipient delivery needs a separate paired-user test.
Smoke excludes writes/chat. Think time defaults to one second per operation.

Search-only burst (Core/Chat fixed at one task each):

```text
k6 run -e BASE_URL=https://STAGING_HOST -e MODE=search -e FIXTURES=./generated/fixtures.json -e DEPLOYMENT=search-scaling tests/load/marketplace.js
```

Current search route is `/api/products/search`. Once extracted, set
`SEARCH_PATH=/api/search/...` to the verified route; do not assume the proposed
route already exists. Thresholds: read median <1s, read P95 <2s, HTTP and business
error rates <1%. HTTP 200 with `success:false` is a business failure. Chat failures
also contribute to business errors. These are request/message-level error rates;
operation counters provide the workload breakdown. Search returns an array;
empty results need separate preflight verification against seeded keywords.

## Oversell

Prepare a dedicated product with exactly INITIAL_STOCK units, price 10 and a
seller distinct from all buyers; make every buyer funded with a valid payment
password. Do not run mixed load on that product concurrently.

```text
k6 run -e BASE_URL=https://STAGING_HOST -e FIXTURES=./generated/fixtures.json -e ENABLE_WRITES=true -e PRODUCT_ID=DEDICATED_ID -e INITIAL_STOCK=1 -e BUYERS=20 tests/load/oversell.js
```

Setup creates all orders before concurrent payment. Acceptance requires exactly
INITIAL_STOCK successful payments and persisted PAID orders, and final stock 0.
Payment rejections remain visible in HTTP metrics; they are expected in this
special test and are not given the mixed workload's <1% HTTP error gate. Inspect
server logs to distinguish stock rejection from unrelated errors. Timeout leaves
the result uncertain; persisted orders are audited in teardown. Repeat on multiple
Core instances. Retain DB evidence for product stock, order_items quantities,
transactions and buyer balances before/after; API checks alone do not prove full
accounting consistency. Unpaid rejected orders can be cleaned up by expiry.

## AWS comparison and CloudWatch evidence

Existing `infra/terraform/env/autoscaling.tf` already defines independent target
tracking policies; coordinate changes with YU CHENGLIN instead of duplicating it.
Run the same seed/image/configuration with scaling disabled at one task per
service, then enable 1–4 tasks. Finally run search-only traffic with Core/Chat
fixed. Record UTC start/end; CloudWatch period 60s; collect per-service running
and desired task count (Container Insights), CPU, memory, ALB request count,
requests per target, target response time, target/ALB 5xx and unhealthy targets.
Retain scaling activities and policy targets/cooldowns. Compare throughput,
latency, failures and time to first/maximum scale-out. Fixed staging smoke cannot
prove AWS scaling. No numerical performance result is claimed before a real run.

## Current handoff

Offline behavior verification: `node tests/load/verify-offline.mjs` exercises all
six operation branches, SockJS/STOMP receipt/message handling, HTTP-200 business
failure detection, write gating, smoke read-only behavior and search-only routing.
It uses mock k6 modules and is not a live protocol/integration test. Actual k6
script parsing was checked with `k6 inspect` for both scripts. Fixture cardinality
and HS512 signatures were checked with synthetic inputs; generated SQL has not
been applied and password hashes have not been validated against a database.

Validation limitations: attempted localhost HTTP smoke/oversell runs could not
connect to the local mock server (ETIMEDOUT), so no live HTTP/WebSocket success
is claimed. Maven/backend tests and real CloudWatch metrics remain unverified by this test-script change.

Remaining external inputs: deployed revision/routes, disposable test database and tokens, and AWS access. Meeting agenda
assigns AWS to YU CHENGLIN; it gives no DigitalOcean staging URL or explicit owner.
Proposal assigns both AWS environments to WP6. Clarify the selected environment
with YU CHENGLIN. No load was sent to a shared environment and no database was
changed by preparing these files.

References: [k6 WebSockets](https://grafana.com/docs/k6/latest/using-k6/protocols/websockets/),
[JaCoCo Maven](https://www.jacoco.org/jacoco/trunk/doc/maven.html),
[SonarCloud Maven](https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven).

## Public smoke on 9 October 2026

`public-smoke.js` runs anonymous browse/search only: 2 VUs, 30 seconds.
Run `k6 run -e BASE_URL=https://dxjzlpbh4ve2y.cloudfront.net tests/load/public-smoke.js`.
Observed 116 requests, no HTTP/contract failures, median 24.02 ms, P95 64.17 ms.
All responses were empty arrays. This is endpoint availability evidence, not
seeded-data or scalability acceptance. Authenticated recommendations returned
401 in separate preflight. CloudFront origin/cache behavior was not verified.
