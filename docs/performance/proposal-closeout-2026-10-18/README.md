# Proposal closeout evidence — 18 October 2026

Repository: ass (5001). This is not the c2cTradePlatform acceptance report.

## A3: service-layer coverage

Source: [successful main CI run](https://github.com/NUS-SE-34FT-GROUP4/ass/actions/runs/39040174201), commit `c3f8a435ef26031fe1121c970db3a13acd6c4bc0`, JaCoCo artifact `12104311068`. Counts were extracted from package LINE counters in downloaded XML, including service/impl. Totals are weighted by executable lines, not averaged percentages. Machine-readable counts are in service-coverage.json.

| Module | Covered lines | Executable lines | Coverage |
|---|---:|---:|---:|
| core | 245 | 1911 | 12.82% |
| search | 159 | 177 | 89.83% |
| chat | 1 | 81 | 1.23% |
| Total (line-weighted) | 405 | 2169 | 18.67% |

**The proposal's 70% service-layer line-coverage target is not met.** Do not substitute whole-project coverage or a green SonarCloud gate for this target. The same run's Surefire XML contains 149 tests, zero failures, errors or skips. A passing test suite does not establish sufficient coverage.

Current parent pom configures JaCoCo prepare-agent and report, but no check goal enforcing 70%. The SonarCloud analysis-and-quality-gate step succeeded and its configuration-pending step was skipped. Presentation wording: coverage is measured and reported; a separate 70% service-layer CI gate is not configured. No frozen application code or gate was changed to conceal the deviation.

## Coverage gap detail

Largest uncovered service classes in the same CI artifact (diagnostic evidence, not a commitment to change frozen code):

| Class | Covered | Missed |
|---|---:|---:|
| `sg/edu/nus/iss/c2csectrade/service/ProductService` | 85 | 159 |
| `sg/edu/nus/iss/c2csectrade/service/impl/OrderServiceImpl` | 50 | 147 |
| `sg/edu/nus/iss/c2csectrade/service/ContentBasedRecommendationService` | 0 | 146 |
| `sg/edu/nus/iss/c2csectrade/service/BargainService` | 0 | 139 |
| `sg/edu/nus/iss/c2csectrade/service/ABTestService` | 0 | 115 |
| `sg/edu/nus/iss/c2csectrade/service/RealtimeRecommendationService` | 0 | 114 |
| `sg/edu/nus/iss/c2csectrade/service/MultiObjectiveOptimizationService` | 0 | 100 |
| `sg/edu/nus/iss/c2csectrade/service/RecommendationEngineService` | 0 | 94 |
| `sg/edu/nus/iss/c2csectrade/service/NCFRecommendationService` | 0 | 92 |
| `sg/edu/nus/iss/c2csectrade/service/impl/CreditScoreServiceImpl` | 0 | 91 |

### Suggested P20 wording

On the measured main revision, 149 tests passed. Service-layer line coverage was 18.67% across Core, Search and Chat, below the proposal target of 70%. Search achieved 89.83%; Core and Chat remained substantially under-covered. JaCoCo reports coverage without a 70% service-layer CI enforcement rule. The configured SonarCloud quality gate passed, but it is a distinct criterion and does not close this proposal deviation.

## C2: alarms

[PR #70](https://github.com/NUS-SE-34FT-GROUP4/ass/pull/70) merged on 17 October at 14:18:15 UTC. Current main includes service latency, 5xx, unhealthy-target and CPU alarms plus RDS CPU/storage alarms in infra/terraform/env/alarms.tf. The coordinator reports C2 complete. The CI run above completed staging and production deploy/smoke jobs. Application deployment success alone is not independent proof of Terraform apply or live alarm state; retain the owner's apply/CloudWatch evidence for presentation.

## A1: search-only scaling run — executed; observed 1 → 2 → 1 cycle

Use the existing marketplace.js MODE=search with SEARCH_PATH=/api/search. Its default /api/products route goes through Core and is unsuitable for proving Search-only isolation. Terraform routes /api/search directly to Search; the search controller returns the array contract expected by the load script. Search mode uses anonymous reads and does not require buyer tokens unless production access is additionally restricted.

Before execution: confirm production hostname, exclusive window, deployed revision, searchable test keywords, initial/maximum task counts and CloudWatch access. Do not rerun seed scripts or write to demo products. Begin with a small read-only precheck, then use an owner-agreed bounded burst with error/latency abort protection. Record actual achieved request rate: VU count alone does not demonstrate the per-target request threshold was crossed.

Collect UTC times, Search/Core/Chat desired and running tasks, scaling activities, ALB requests per target, latency, errors and CPU before/during/after. A passing HTTP run alone does not prove scale-out. The 200-VU five-minute ramp/fifteen-minute hold and fixed-one-task versus 1–4 comparison remain separate uncompleted promises, not covered by a search burst.

## A2: oversell — prepared, not executed

Existing tests/load/oversell.js prepares orders then concurrently pays from distinct funded buyers, requiring exactly INITIAL_STOCK paid orders and final stock zero. Use only a new dedicated staging product, never demo inventory; confirm ZHOU is not running overlapping tests. Fresh buyer credentials/payment passwords and a valid dedicated product are required. Existing shared fixture tokens have expired.

Verify at least two Core instances and correlate requests with instance/log evidence before claiming cross-instance validation. Preserve stock, order quantities, payment/transaction and balance audit evidence; API totals alone are not full accounting proof. Coordinate cleanup of unpaid and paid test orders with the owner. Do not treat authentication errors or timeouts as successful stock-conflict rejection.

## Outstanding external inputs

- Scaling activity read access to verify the exact policy cause.
- Staging disposable product, current funded-buyer fixture and no-overlap confirmation.
- Owner-assisted ledger and instance-routing evidence for oversell.

Initial preparation sent no live requests. A subsequent authorized production precheck is recorded below; no write requests were sent.

## Production search precheck — 18 October

After the user confirmed the production endpoint and no overlapping testers, a bounded anonymous search run used MODE=search, SEARCH_PATH=/api/search, two VUs, five-second ramp, twenty-second hold and five-second ramp-down. Both keywords were book/phone. It completed 45 requests/checks with zero HTTP or business errors; search median 50.32 ms and P95 487.75 ms. No orders, payments or seed scripts were executed.

This is connectivity/low-load evidence, not autoscaling or capacity evidence. AWS console access was subsequently restored and the controlled run below was completed. Metrics are retained in production-search-precheck.json.

## Production controlled search run — 18 October

Started 06:02:07 UTC (14:02:07 Singapore); 30-second ramp to 30 VUs, four-minute hold and fifteen-second ramp-down. The existing search-only workload used /api/search with anonymous book/phone queries. A wrapper aborted on HTTP/business error rate >=1% or read P95 >=2 seconds after a thirty-second grace period. It completed normally and the load container exited.

7,431 requests/checks passed, with zero HTTP/business errors. Average throughput was 26.00 requests/s; median search latency 28.09 ms, P95 85.54 ms, P99 413.78 ms and maximum 8,279.04 ms. The maximum must not be hidden by the low P95. This was not a 200-VU capacity run.

CloudWatch visually showed Search requests per target peaking around 1,750/minute. The initial graph observation ended around 06:06 UTC and still showed one task; this was an incomplete observation window, not proof that scaling failed. ECS service events subsequently showed one additional Search task started and registered at 06:07 UTC, followed by a steady state at 06:08 UTC. At 06:13 UTC, Search had two desired/running tasks and two healthy targets, using task definition revision 5. Core and Chat were each observed at one running task in the cluster view at 06:12 UTC.

This establishes an observed Search-only task-count increase from 1 to 2 following the burst. The operator did not change desired counts, policies or deployments. However, IAM denied `application-autoscaling:DescribeScalingActivities`, so the exact policy trigger/cause is not independently verified. The automatic-scaling page's incomplete display must not be interpreted as proof that no policy exists. Subsequent ECS events at 06:24 UTC show one task stopped, one target deregistered and the service returning to steady state. At 06:26 UTC, desired and running counts were both one, on the same task definition revision 5. The observed 1 → 2 → 1 cycle is therefore complete without operator scaling changes. The exact policy activity trail remains unavailable; no 1-to-4 or 200-VU capacity claim is made.

No production orders, payments or seed changes were made. A2 remains pending dedicated staging fixtures and cross-instance evidence.

### Evidence files

- `production-search-precheck.json`: low-load summary.
- `production-search-30vu.json`: controlled-run summary.
- `screenshots/search-baseline.jpg`: initial Search task-count graph.
- `screenshots/search-request-rate.jpg`: observed request-rate peak.
- `screenshots/search-2-running.jpg`: ECS Search desired/running count of two.
- `screenshots/search-scale-out-curve.jpg`: CloudWatch desired/running count transition from one to two, with a UTC axis.

At the 06:18 UTC ECS refresh, Search still had two desired/running tasks. No natural scale-in had been observed at that checkpoint. By 06:26 UTC, the ECS console confirmed one desired/running task after the 06:24 UTC stop event. See `screenshots/search-back-to-one.jpg`. The console also denied `application-autoscaling:DescribeScalableTargets`; owner-provided activity/target evidence can close this remaining audit gap.

### P20 module counts (same CI artifact)

These are whole-module JaCoCo LINE counters, distinct from the service-layer counters above.

| Module | Tests passed | Covered / executable lines | Whole-module line coverage |
|---|---:|---:|---:|
| Core | 84 | 483 / 3178 | 15.20% |
| Search | 40 | 247 / 276 | 89.49% |
| Chat | 25 | 190 / 382 | 49.74% |
| Total | 149 | 920 / 3836 | 23.98% |

All modules reported zero test failures, errors and skips. Neither the whole-module total nor the service-layer total meets 70%.

## P19/P20 rehearsal guide

The online presentation was updated on 18 October: P19 reports the observed Search 1 → 2 → 1 cycle and the search-burst metrics; P20 reports 149 passing tests and 18.67% service-layer line coverage. Both slides were visually checked and the editor confirmed cloud save.

### Evidence walkthrough (about 90 seconds)

1. Show `screenshots/search-baseline.jpg`: Search starts with one task.
2. Show `screenshots/search-request-rate.jpg`: the burst reaches approximately 1,750 requests per target per minute. This graph value is a visual approximation, not an exported exact peak.
3. Show `screenshots/search-scale-out-curve.jpg` and `screenshots/search-2-running.jpg`: desired/running tasks increase to two. Core and Chat were sampled at one each; this is not continuous proof of their counts throughout the window.
4. Show `screenshots/search-back-to-one.jpg`: Search returns to one. ECS events place the additional task start at 06:07 UTC and the task stop at 06:24 UTC. The observer did not change desired counts or deploy a new revision.
5. State the bounded result: 30 VUs, 7,431 requests, zero HTTP/business errors, P95 85.54 ms, maximum 8,279.04 ms. This is not a 200-VU capacity claim or a fixed-one-task versus autoscaling comparison.
6. On P20, distinguish 149 passing tests from service-layer coverage of 18.67%, below the 70% proposal target. A green SonarCloud gate uses a separate criterion.

Suggested narration: “On 18 October, our bounded production search burst was followed by a Search task-count change from one to two, then back to one after load stopped. The screenshots and ECS event times are recorded in PR 71. The precise scaling-policy cause is still awaiting activity-read access. We do not claim a 200-user capacity result.”

This walkthrough uses recorded screenshots, not a live test or a screen recording. No video has been captured. During rehearsal, show the existing evidence rather than restart production load without a fresh exclusive window. If a recording is needed, label a recording of this walkthrough as an evidence review; do not present it as footage captured during the original load run.

### Owner follow-up for the remaining activity trail

Request read-only `application-autoscaling:DescribeScalingActivities` and `application-autoscaling:DescribeScalableTargets` for the relevant ECS production Search target, or ask the owner to export the matching activity records. The console denied both actions. Capture cause, status and timestamps around 06:02–06:26 UTC on 18 October. Do not change scaling settings merely to obtain a screenshot.

## A2 readiness and execution handoff

Before any write request, obtain a new staging-only product with stock 1, its seller identity, and at least two distinct funded buyers who are not the seller. Each buyer must have a current JWT and a six-digit payment password. Keep these in a private local fixture, outside Git. Confirm the fixture product/keyword arrays are non-empty, as required by config.js. Confirm the staging hostname and a fresh no-overlap window; verify at least two healthy Core instances.

For a minimal two-buyer run, use the existing script with matching BUYERS and VUS values. This is a preparation example, not an executed test:

```sh
k6 run \
  -e BASE_URL=https://STAGING_HOST \
  -e FIXTURES=/absolute/private/fixtures.json \
  -e ENABLE_WRITES=true \
  -e PRODUCT_ID=DEDICATED_ID \
  -e INITIAL_STOCK=1 -e BUYERS=2 -e VUS=2 \
  --summary-export=/absolute/private/oversell-summary.json \
  tests/load/oversell.js
```

Replace the placeholders only after validating the supplied fixture and product. The script creates all orders first, then submits concurrent balance payments. It passes only if exactly one payment and persisted order are PAID, final stock is zero, and every unsuccessful payment is rejected specifically for stock. Authentication errors, timeouts and unrelated database errors do not count as correct stock rejection. The test does not automatically clean up orders, and setup failure can leave partial unpaid orders: record them privately and coordinate cleanup.

To claim cross-instance correctness, retain owner-provided routing/log evidence showing requests reached distinct Core instances and a ledger/stock audit showing one charge with no unintended balance loss. Having two running instances alone is insufficient. Publish sanitized aggregates; retain account, token, order and balance details privately. A2 remains unexecuted until these prerequisites arrive.
