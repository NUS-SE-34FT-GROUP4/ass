# Staging mixed load retest on 19 October 2026

This record publishes the previously retained k6 summaries for the final SWE5001 report. No new load was generated on 6 November. Credentials, private fixtures and raw time series are excluded.

Deployed revision: `1b223755576dead76601856ccc7d17e473625512`, including PR #75. Deployment: [run 39235704681](https://github.com/NUS-SE-34FT-GROUP4/ass/actions/runs/39235704681). The operator confirmed an exclusive staging window with no deployments.

| Run | HTTP requests | Achieved HTTP requests/s | Read median ms | Read P95 ms | Search P95 ms | HTTP error rate | Result |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 2 VU precheck | 84 | 2.70 | See summary | 299.23 | 490.83 | 0% | Completed |
| 50 VU mixed | 6,725 | 34.35 | 303.45 | 1,009.17 | 688.34 | 0% | Completed and passed |
| Target 200 VU | 795 | 25.26 | See summary | 5,041.87 | 3,245.75 | 0.377% | Aborted during ramp, sampled peak 104 VUs |

The 50-VU run completed a 60-second ramp, 120-second hold and 15-second descent with one-second think time. Duration was 195.775 seconds. UTC window: 11:57:27.464 to 12:00:43.233 on 19 October. Gates were read median <1 second, read P95 <2 seconds and HTTP/business errors <1%. Read maximum was 5,307.78 ms, so passing P95 does not mean every request was fast. Business error rate was zero. It passed 206 payment checks, 216 cart sequences and 212 self-message WebSocket checks, without a ledger audit or paired-user chat validation.

The 200-target run lasted 31.47 seconds, 12:05:56-12:06:27 UTC. `vus.max` is 104; `vus_max` is the configured allocation of 200. It did not reach or hold 200 VUs. There were three HTTP failures in 795 requests and three business failures in 825 samples. Some payment calls timed out; their server outcome was not audited and cannot be equated with no debit. The run stopped on its read-latency protection gate. This is not a measured steady-state capacity limit or an autoscaling acceptance result.

The earlier 19 October morning run (documented in the parent README) stopped with read P95 2,199.52 ms and search P95 2,319.63 ms. The retest's improvement is associated with a later revision, including PR #75. Random workload choice and other changes prevent attributing the entire difference to that PR or claiming that connection-pool exhaustion was proven.

## Original machine-readable summaries

- [Precheck](smoke-summary.json)
- [Completed 50-VU run](mixed50-summary.json)
- [Aborted 200-target run](mixed200-summary.json)

Use `http_req_duration{kind:read}` for the aggregate read latency. k6 rate metrics count true samples in `passes`: for `http_req_failed`, those are failures, not successful requests. `http_reqs` is a request counter; check totals may differ because operations contain multiple checks.

## Diagnostic boundary

The retained investigation recorded Core CPU at 12:06 UTC as average 63.1%, maximum 100%, minimum 0.41%, with memory maximum 31.3%. A Core task had recently started. This supports investigating pressure and warm-up, not claiming uniform sustained saturation. Candidate enrichment and recommendation lookups can amplify work; no SQL timing or CPU profile in these summaries identifies a proven hot method. Further profiling and a staged retest would be required. AWS has since been shut down.
