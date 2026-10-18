# CUI rehearsal card

Evidence as of 18 October 2026. All results below belong to ass (5001).

## Numbers to quote

| Evidence | Result | Meaning / limit |
|---|---|---|
| Pagination pre-flight | 25 requests, 5 failures; one response 10,001 items / 5,399,426 bytes / 8,425 ms | Unbounded product list; single response time differs from browse P95 10,006 ms |
| Pagination retest, 2 VUs | 77 checks passed; browse P95 84 ms; search P95 624 ms; overall read median 128 ms / P95 536 ms | Low-load pagination validation, not single-user or capacity evidence |
| Staging 50 VUs, 17 Oct | Stopped after about 124 s; read P95 2,078 ms; search P95 2,307 ms; HTTP/business errors 0 | Latency threshold failed; cause was not established |
| Production search burst, 18 Oct | 30 VUs; 7,431 requests; errors 0; P95 85.54 ms; max 8,279.04 ms | Different environment and workload; not a controlled improvement over the 50-VU mixed run |
| Production Search tasks | 1 → 2 → 1; extra task start 14:07, task stop 14:24 Singapore | Sampled Core/Chat counts stayed at 1; exact policy-cause records await permissions |
| CI tests | 149 passed: Core 84 / Search 40 / Chat 25 | Zero failures, errors, skips on measured revision c3f8a43 |
| Service-layer line coverage | 18.67% overall; Core 12.82%, Search 89.83%, Chat 1.23% | 70% proposal target not met; not enforced by a separate CI rule |
| Whole-module line coverage | 23.98% overall | Different denominator from service-layer coverage |

## One-line live demonstration command

Run from the repository root with k6 installed, only after the current demonstration window is agreed. This is a short anonymous read-only search demonstration. It needs no token and uses the non-secret example fixture's book/phone keywords. It does not read or mutate the fixture product IDs in search mode.

```sh
k6 run -e BASE_URL=https://d28v51gtznejrx.cloudfront.net -e MODE=search -e SEARCH_PATH=/api/search -e VUS=2 -e RAMP=5s -e HOLD=20s -e DOWN=5s --summary-export=/tmp/ass-demo-search-summary.json tests/load/marketplace.js
```

The scheduled ramp/hold/down lasts 30 seconds; graceful completion may take longer. This command retains the script's end-of-run thresholds; it does not add early-abort protection. If the demo fails, stop with Ctrl+C and use saved evidence. Do not increase VUs or retry repeatedly on stage. Two VUs are not expected to demonstrate scale-out: show the previously captured 1 → 2 → 1 evidence separately. No new production run was executed while preparing this card.

## 80-second speaking sequence

- 0–15 s: “We first found an unbounded product list at only two concurrent users. Pagination fixed that specific defect; the retest passed 77 checks.”
- 15–45 s: Show baseline, request-rate peak, two-task state and return-to-one screenshots. “The production search-only burst was followed by a 1 → 2 → 1 task-count cycle. We made no manual scaling or deployment changes.”
- 45–65 s: Show the short command and its output if a live window is agreed. “This illustrates the script, not system capacity, and this small run is not expected to trigger scaling.”
- 65–80 s: “The separate 50-VU staging mixed run exceeded the latency threshold. We have not completed the proposed 200-VU test or fixed-task comparison. Coverage is measured honestly: 18.67% service-layer coverage against a 70% target.”

## Questions and precise answers

- Can it handle 200 concurrent users? Not established. The proposed five-minute ramp / fifteen-minute hold and fixed-one-task comparison remain uncompleted.
- Did all three services scale? We observed Search 1 → 2 → 1; Core and Chat were each 1 in sampled observations. Do not claim all three scaled or that Search reached 4.
- Why did 50 VUs fail but 30 VUs pass? The environment and workload differed. We have not isolated a causal performance improvement.
- Does zero error rate mean the 50-VU run passed? No. Read P95 exceeded 2 seconds, so the latency criterion failed.
- Does a green SonarCloud gate mean 70% coverage? No. The measured service coverage is below target and the gate is a separate criterion.
- Is cross-instance oversell verified? Yes, in one two-buyer staging test on 18 October. Owner-reported ALB records show distinct Core targets and about 31 ms of overlapping processing. One payment succeeded, one was stock-rejected, final stock was zero, and the owner confirmed one transaction with no rejected-buyer debit. This is not a high-concurrency result. The rejection returned HTTP 500; error-status semantics remain a separate limitation.

## Evidence and handoff

Report and screenshots: https://github.com/NUS-SE-34FT-GROUP4/ass/pull/71

CI source: https://github.com/NUS-SE-34FT-GROUP4/ass/actions/runs/39040174201

Screenshot sequence in this directory: search-baseline.jpg → search-request-rate.jpg → search-scale-out-curve.jpg → search-2-running.jpg → search-back-to-one.jpg (all under screenshots/).

No original load-test video was captured. A retrospective walkthrough may show these real screenshots, but must be labelled as such. A new live capture requires another coordinated window. No seed script should be rerun against demo products with existing orders.

Final operator/user-flow speaker assignment belongs to the team. CUI owns only the load/scaling narration here. Do not overwrite teammates' current assignments based on older scripts.
