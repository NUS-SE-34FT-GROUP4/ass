# WP10 staging preflight and anonymous smoke — 2026-10-14

Owner: CUI ZIJIAN. This is preliminary evidence, not full mixed-load or autoscaling acceptance.

## Environment and revision

- Public URL: https://dxjzlpbh4ve2y.cloudfront.net (GitHub staging environment APP_URL).
- AWS region: ap-southeast-1; ECS cluster: c2csectrade-staging.
- Source inspected: main `5946cbdf0d4db20ab61458213c9e199802349c10`.
- GitHub run [38539688046](https://github.com/NUS-SE-34FT-GROUP4/ass/actions/runs/38539688046)
  reports successful staging deploy and smoke; production skipped. ECS image digests, actual
  task counts and active scaling policy configuration were not independently read from AWS.
- Generator: Docker k6 2.3.0 on the contributor's local computer, through public CloudFront.
  Generator egress region is not verified. This is not an in-VPC benchmark.

## Initial checks and script correction

`preflight.json` contains four sequential anonymous GET results, timestamps and response
metadata only (no user payloads or credentials).

- `/api/products`: 200 JSON array, **one product**, 536 bytes at preflight.
- Old `/api/products/search?keyword=book&page=0&size=20`: 500.
- Current controller exposes keyword search through `/api/products?keyword=...`. The old
  path is treated as the `/{id}` route, not a search endpoint. Both k6 scripts now default
  to `/api/products`; `SEARCH_PATH` still supports verified deployment-specific overrides.
- Public `/actuator/health/liveness`: 200 **HTML**, same size as the frontend document.
  This response cannot establish backend liveness. Use the deployment's verified service
  health checks, not status code alone on the frontend origin.

## Smoke parameters and result

```
BASE_URL=https://dxjzlpbh4ve2y.cloudfront.net
KEYWORD=lamp
k6 run tests/load/public-smoke.js
```

Two VUs for 30 seconds; each iteration browses and searches, then sleeps one second.
No login, cart, payment, chat, fixture import or infrastructure modification was performed.
Exact run-end timestamp and metric values are retained in `public-smoke-summary.json`.

| Metric | Observed |
|---|---:|
| Requests | 104 |
| HTTP failures | 0 |
| Contract checks | 104 passed / 0 failed |
| Empty result arrays | 0 |
| Median HTTP duration | 35.57 ms |
| P95 HTTP duration | 128.93 ms |
| Maximum HTTP duration | 2360.12 ms |
| Request throughput | 3.37/s |

The smoke thresholds passed. Its small dataset and low concurrency are not comparable to
October 12's 10,000-product local baseline. They do not establish that its 4034ms read P95
problem has been fixed. Search responses alone also do not distinguish Elasticsearch from
Core's database fallback; logs/metrics must verify this during the full run.

## Slow-list investigation (source evidence, not a measured root cause)

`ProductMapper.selectWithFilters` has no LIMIT. `ProductService.listProductsWithFilters`
queries media once per product; conversion then queries the seller once per product.
Consequently an unfiltered list can issue approximately 1 + 2N database queries, plus return
all products. This is a concrete candidate for the old browse P95 of 5219ms. Verify with the
same 10,000-product dataset and SQL/latency evidence before claiming causation. Pagination
and batch media/seller hydration should be coordinated with the Core owner; increasing ECS
tasks alone does not remove this database query amplification.

## Redis service-prefix audit

At the inspected revision Core uses business prefixes without a service namespace:

| Area | Examples |
|---|---|
| Captcha | `captcha:` |
| Cache / lock | `cache:product:`, `cache:user:`, `cache:hot:products`, `lock:` |
| History / profile | `history:user:`, `profile:user:` |
| Recommendations | `recommend:item-cf:`, `recommend:content:`, `recommend:popularity` |
| Realtime / model | `realtime:score:`, `realtime:session:`, `realtime:trending`, `ncf:embedding:`, `ncf:prediction:` |
| Experiments | `abtest:config:`, `abtest:user:`, `abtest:metrics:` |

`RedisConfig` serializes keys as plain strings, and captcha uses StringRedisTemplate directly;
there is no common service prefix wrapper. No Redis API usage was found in Chat/Search Java
source. Thus there is a gap against the proposal, but no demonstrated cross-service key
collision. YU/CUI should agree whether to document current Core ownership or introduce a
service namespace with an expiry/cache/lock transition plan. This audit does not rename keys
or flush Redis.

## Full-run prerequisites and remaining work

1. AWS access to inspect ECS tasks, CloudWatch metrics and scaling activities; record running
   image digests and deployed policy values. Terraform defaults scaling to production only
   unless `enable_autoscaling` is explicitly enabled, so code presence is not proof it is on
   in staging.
2. A dedicated load dataset and valid per-user tokens: target 2,000 users / 10,000 products,
   funded buyers, separate seller and dedicated oversell product. Existing local fixture
   credentials are not assumed to be valid for AWS. No shared data is reset by this run.
3. Authenticated low-load preflight, then controlled mixed load (200 VUs / 15-minute hold),
   fixed-task vs scaling comparison and search-only burst with matching images/data.
4. Capture latency by endpoint, errors, CPU/memory, desired/running tasks, scaling activities,
   policy targets/cooldowns and accounting evidence. Only then propose measured parameter
   changes with YU and repeat for comparison.

Offline script behavior and fixture-generator tests passed after the route correction.
