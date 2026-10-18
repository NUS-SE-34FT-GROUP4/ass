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

## A1: search-only scaling run — prepared, not executed

Use the existing marketplace.js MODE=search with SEARCH_PATH=/api/search. Its default /api/products route goes through Core and is unsuitable for proving Search-only isolation. Terraform routes /api/search directly to Search; the search controller returns the array contract expected by the load script. Search mode uses anonymous reads and does not require buyer tokens unless production access is additionally restricted.

Before execution: confirm production hostname, exclusive window, deployed revision, searchable test keywords, initial/maximum task counts and CloudWatch access. Do not rerun seed scripts or write to demo products. Begin with a small read-only precheck, then use an owner-agreed bounded burst with error/latency abort protection. Record actual achieved request rate: VU count alone does not demonstrate the per-target request threshold was crossed.

Collect UTC times, Search/Core/Chat desired and running tasks, scaling activities, ALB requests per target, latency, errors and CPU before/during/after. A passing HTTP run alone does not prove scale-out. The 200-VU five-minute ramp/fifteen-minute hold and fixed-one-task versus 1–4 comparison remain separate uncompleted promises, not covered by a search burst.

## A2: oversell — prepared, not executed

Existing tests/load/oversell.js prepares orders then concurrently pays from distinct funded buyers, requiring exactly INITIAL_STOCK paid orders and final stock zero. Use only a new dedicated staging product, never demo inventory; confirm ZHOU is not running overlapping tests. Fresh buyer credentials/payment passwords and a valid dedicated product are required. Existing shared fixture tokens have expired.

Verify at least two Core instances and correlate requests with instance/log evidence before claiming cross-instance validation. Preserve stock, order quantities, payment/transaction and balance audit evidence; API totals alone are not full accounting proof. Coordinate cleanup of unpaid and paid test orders with the owner. Do not treat authentication errors or timeouts as successful stock-conflict rejection.

## Outstanding external inputs

- Production endpoint and agreed search-burst window/limits.
- Live CloudWatch access and baseline/scaling evidence.
- Staging disposable product, current funded-buyer fixture and no-overlap confirmation.
- Owner-assisted ledger and instance-routing evidence for oversell.

No live load or write requests were sent while preparing this closeout.
