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

## A1: search-only scaling run — executed; scale-in evidence pending

Use the existing marketplace.js MODE=search with SEARCH_PATH=/api/search. Its default /api/products route goes through Core and is unsuitable for proving Search-only isolation. Terraform routes /api/search directly to Search; the search controller returns the array contract expected by the load script. Search mode uses anonymous reads and does not require buyer tokens unless production access is additionally restricted.

Before execution: confirm production hostname, exclusive window, deployed revision, searchable test keywords, initial/maximum task counts and CloudWatch access. Do not rerun seed scripts or write to demo products. Begin with a small read-only precheck, then use an owner-agreed bounded burst with error/latency abort protection. Record actual achieved request rate: VU count alone does not demonstrate the per-target request threshold was crossed.

Collect UTC times, Search/Core/Chat desired and running tasks, scaling activities, ALB requests per target, latency, errors and CPU before/during/after. A passing HTTP run alone does not prove scale-out. The 200-VU five-minute ramp/fifteen-minute hold and fixed-one-task versus 1–4 comparison remain separate uncompleted promises, not covered by a search burst.

## A2: oversell — prepared, not executed

Existing tests/load/oversell.js prepares orders then concurrently pays from distinct funded buyers, requiring exactly INITIAL_STOCK paid orders and final stock zero. Use only a new dedicated staging product, never demo inventory; confirm ZHOU is not running overlapping tests. Fresh buyer credentials/payment passwords and a valid dedicated product are required. Existing shared fixture tokens have expired.

Verify at least two Core instances and correlate requests with instance/log evidence before claiming cross-instance validation. Preserve stock, order quantities, payment/transaction and balance audit evidence; API totals alone are not full accounting proof. Coordinate cleanup of unpaid and paid test orders with the owner. Do not treat authentication errors or timeouts as successful stock-conflict rejection.

## Outstanding external inputs

- Scaling activity read access and natural scale-in evidence.
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

This establishes an observed Search-only task-count increase from 1 to 2 following the burst. The operator did not change desired counts, policies or deployments. However, IAM denied `application-autoscaling:DescribeScalingActivities`, so the exact policy trigger/cause is not independently verified. The automatic-scaling page's incomplete display must not be interpreted as proof that no policy exists. Natural scale-in and the complete policy activity trail remain pending; no 1-to-4 or full scale-out/scale-in claim is made.

No production orders, payments or seed changes were made. A2 remains pending dedicated staging fixtures and cross-instance evidence.

### Evidence files

- `production-search-precheck.json`: low-load summary.
- `production-search-30vu.json`: controlled-run summary.
- `screenshots/search-baseline.jpg`: initial Search task-count graph.
- `screenshots/search-request-rate.jpg`: observed request-rate peak.
- `screenshots/search-2-running.jpg`: ECS Search desired/running count of two.
- `screenshots/search-scale-out-curve.jpg`: CloudWatch desired/running count transition from one to two, with a UTC axis.

At the 06:18 UTC ECS refresh, Search still had two desired/running tasks. No natural scale-in had been observed at that checkpoint.

### P20 module counts (same CI artifact)

These are whole-module JaCoCo LINE counters, distinct from the service-layer counters above.

| Module | Tests passed | Covered / executable lines | Whole-module line coverage |
|---|---:|---:|---:|
| Core | 84 | 483 / 3178 | 15.20% |
| Search | 40 | 247 / 276 | 89.49% |
| Chat | 25 | 190 / 382 | 49.74% |
| Total | 149 | 920 / 3836 | 23.98% |

All modules reported zero test failures, errors and skips. Neither the whole-module total nor the service-layer total meets 70%.
