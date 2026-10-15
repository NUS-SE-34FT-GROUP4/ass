# WP10 pagination fix retest

Owner: CUI ZIJIAN. Executed October 14, 2026, approximately 15:57 UTC
(23:57 Singapore); documented October 15.

## Deployment and test conditions

PR #53 was merged as `d838bc101a85f2d9c0aabf430f6e69eba35a3c5a`.
[Deployment run 38592215283](https://github.com/NUS-SE-34FT-GROUP4/ass/actions/runs/38592215283)
completed staging deployment and its smoke check successfully before this run began.
While deployment was pending, the old endpoint still returned 10,001 items; those probes
were not counted as a test of the fix. Live pagination was then verified (see preflight.json).

Same public CloudFront URL, fixture file, client timeout (10 seconds), 2 VUs, 30-second
constant-VU scenario and one-second think time as the failed authenticated preflight.
The fixture contains 1,999 buyers and 10,000 product IDs. The endpoint reports **10,001**
matching products, confirming the improvement was not achieved by removing the dataset.
Traffic mix is random, so endpoint sample counts differ between runs. Generator is local
Docker k6 2.3.0, not an in-VPC load generator. No write/payment/chat operations were run.

```
BASE_URL=https://dxjzlpbh4ve2y.cloudfront.net
FIXTURES=./generated/staging-private/fixtures.json
MODE=smoke VUS=2 HOLD=30s
REVISION=d838bc101a85f2d9c0aabf430f6e69eba35a3c5a
DEPLOYMENT=staging-post-pr53-scaling-unverified
k6 run tests/load/marketplace.js
```

## Results

| Metric | Before #53 | After #53 |
|---|---:|---:|
| HTTP requests | 25 | 77 |
| Failed HTTP/business checks | 5 (20%) | 0 (0%) |
| Browse checks passed / failed | 0 / 5 | 11 / 0 |
| Browse P95 | 10006ms, timed out | 84.46ms |
| Search P95 | 1410.22ms | 624.21ms |
| Overall read median | See prior raw summary | 127.59ms |
| Overall read P95 | Failed 2000ms threshold | 536.28ms |
| All configured thresholds | Failed | Passed |

Post-fix all 77 checks passed (11 browse, 12 search, 16 detail, 16 similar, 16 personalised,
6 cart reads). Source: smoke-2vu.json. The generic ramp/down fields in the summary do not
apply to MODE=smoke, which uses constant VUs.

Independent preflight: first and second browse pages each returned 12 items, X-Total-Count
10001 and 6481 response bytes, in approximately 126ms. Previously the unpaged response
was 5,399,426 bytes and took 8425ms in an isolated request. Search returned 20 items for
size=20, with total 100 reflecting the existing Search candidate cap. Response workload
changed deliberately from the entire catalogue to a page; this comparison is evidence
that pagination works, not a claim of equally sized responses becoming 100x faster.

## Conclusion and remaining gates

The low-concurrency product-list blocker is resolved in this run. This is not a 200-VU
capacity test, an autoscaling acceptance result, or a payment/chat/oversell validation.
Actual ECS task counts and scaling policy state were not independently captured, so this
must not be described as a fixed-one-task baseline or evidence of 1-to-4 scale-out.

Next: refresh expired fixture tokens, verify current deployed revision and task/scaling
configuration, then progress through authenticated mixed-load checks and controlled load
stages. Retain CloudWatch and scaling activity windows for the fixed/scaling comparison.
The supplied tokens expired at October 15 01:05:26 Singapore; the successful run occurred
before that expiry. No credential files are included here.
