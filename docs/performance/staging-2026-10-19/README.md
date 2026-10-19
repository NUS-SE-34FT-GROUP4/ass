# Staging mixed-load regression — 19 October 2026

CUI; ass (5001). The user confirmed the testing window and supplied a fresh private fixture with 1,999 buyers and 10,000 dedicated products. Tokens remained valid. Fifty buyer identities/balances and three product samples were checked before mixed load. No credentials or private time-series URLs are published.

## Deployment and method

GitHub run 39212398092 deployed staging revision e520b21d51db6c1237d1b7a0ed482b8392d7b06c at 07:00:21 UTC, followed by a successful smoke job at 07:00:33. Because this followed an earlier precheck, a new 2-VU/30-second read-only precheck was run: 67 requests/checks, zero failures, read median 138.38 ms and P95 680.50 ms. Image digests and ECS task counts were not independently captured for this run; revision attribution is from the deployment workflow.

Local Docker k6 2.3.0 used the previously reviewed marketplace workload with 1-second think time and default /api/products keyword-search route (through Core, not the direct /api/search isolation workload). Planned stages: 60-second ramp to 50 VUs, 120-second hold, 15-second descent. A local wrapper preserved the workload and added early abort after a 30-second grace period: HTTP/business errors <1%, read median <1 second, read P95 <2 seconds. No application code or thresholds were relaxed.

## Result — latency gate failed

Observed point window: 07:02:07–07:04:43 UTC / 15:02:07–15:04:43 Singapore. Duration 155.88 seconds; peak 50 VUs reached. k6 exited 99 on the read-P95 threshold and the container terminated. The full planned hold/descent was not completed. No 200-VU test was started.

| Metric | Result |
|---|---:|
| Completed HTTP requests | 4,756 |
| Average HTTP throughput | 30.51 requests/s |
| Passed checks | 4,920; zero failed |
| HTTP/business error rates, completed operations | 0% / 0% |
| Overall read median / P95 / P99 / maximum | 395.98 / 2,199.52 / 2,381.22 / 2,704.29 ms |
| Search P95 | 2,319.63 ms |
| Browse P95 | 415.91 ms |
| Similar / personalized recommendation P95 | 1,489.23 / 1,392.46 ms |
| Detail / cart-list P95 | 597.80 / 699.00 ms |

There were 141 successful order-creation checks and 140 successful payment checks. The aborted workload can leave unfinished orders/operations; these counts are not proof of complete ledger reconciliation. There were 164 successful self-recipient chat round trips (P95 53.70 ms); this does not validate paired-user or cross-instance message delivery. Do not count interrupted work as passed. No automatic fixture reset or cleanup was performed.

## Interpretation and next action

The 17 October mixed run also failed the two-second read-P95 gate. This run again identifies keyword search as the slowest measured read, but the revision differs and no causal fix or regression is isolated. Inspect Core-to-Search timeout/fallback, Search/index health, ECS and database resource metrics around 07:02–07:05 UTC before retrying or escalating. A latency near two seconds is not by itself proof of a timeout/fallback cause.

The latest workflow remained 39212398092 during/following the test; its staging deployment completed before the run. Production deployment ran separately during the window. No new staging deployment was observed through these workflow checks; continuous infrastructure/deployment monitoring was not captured. This run does not establish scaling effectiveness or infrastructure capacity.

The generator is stopped. Private fixtures, raw time series and pre-run balance snapshots are retained locally outside Git. Summary files contain aggregate measurements only. Review test-order side effects with the environment owner before reseeding. No higher-load or fixed-versus-autoscaling run is justified until this failure is investigated.
