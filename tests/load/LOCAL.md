## Split-service baseline update (2026-10-12)

The current runner starts Core, Search, Chat and an nginx gateway on localhost:18080. Search uses /api/search; /api/chat and /ws route to Chat. Use a new WP10_PROJECT and archive the old generated directory before seeding. The previous recorded baseline below describes the old Core-only revision, not this runner.

Seed indexing loads a snapshot of the isolated MySQL fixture rows directly into the local index. It does not prove RabbitMQ event propagation. All load traffic uses the gateway; search and chat must pass separately. No AWS services are contacted.

# WP10: reproducible local baseline

Requirements: Docker Desktop/Engine with Compose, Python 3, internet for the first
build, about 8 GB available to Docker. Java 17, Maven, Node 22 and k6 run in
containers. Run commands from the repository root. This environment is named
`ass-wp10-local`, uses dedicated named volumes, exposes only the gateway on
`127.0.0.1:18080`, and never connects to staging or AWS.

```sh
python3 tests/load/local.py build
python3 tests/load/local.py up
python3 tests/load/local.py seed
python3 tests/load/local.py check
python3 tests/load/local.py run --mode smoke --vus 2 --hold 30s
python3 tests/load/local.py run --mode mixed --vus 20 --ramp 30s --hold 2m --down 15s
python3 tests/load/local.py audit
```

`build` runs Maven `clean install` (including tests, packaging and JaCoCo), the Node
checks, and builds MinIO from a pinned upstream release. The old MinIO prebuilt
image download is no longer available; see the [upstream source-build guidance](https://github.com/minio/minio).
The chosen release matches a historical local development dependency, not a
claim that this environment is suitable for production. Docker image IDs and
runtime resources are captured with each run. Search and Chat run in their own containers behind the gateway. This baseline
measures one instance per service with MySQL, Redis, RabbitMQ, Elasticsearch
and MinIO, and does not establish AWS scaling performance.

`seed` imports exactly 2,000 load users (one seller, 1,999 buyers) and 10,000
products in reserved ID ranges. Existing schema seed accounts remain present.
The importer refuses occupied ranges, and plain SQL INSERTs abort collisions.
The database schema is initialized only when Docker creates a fresh local MySQL
volume. Never run the repository's destructive `init.sql` in a shared database.
The commands above do not reset or delete an existing database or volume.

The generator creates random login and six-digit payment passwords, hashes them
with BCrypt cost 10, verifies each hash, and writes private local credentials.
You can provide LOAD_PASSWORD and LOAD_PAYMENT_PASSWORD instead. External hash
overrides require matching plaintext values and are rejected on mismatch.
A missing ROLE_USER aborts import instead of silently producing unusable users.

`check` verifies database counts and role assignments, refreshes signed local JWTs
for two hours, provisions a one-use captcha in this environment's Redis, and
logs in through the real authentication controller to validate the imported
BCrypt hash. This is test setup; production authentication is unchanged. It then
runs the application's index sync, checks the ES document count, requires actual
results for every keyword, and checks authenticated read routes. Recommendation
responses are checked for their array contract; recommendation quality is not
measured. The preflight is not part of timed k6 results.

`run` preserves summaries, stdout, pre/post database aggregates, image IDs,
resource limits and backend log tails under `tests/load/results/`. The workload
uses seeded distinct buyers, 1s think time, actual HTTP and SockJS/STOMP, cart
writes and simulated balance payments. `audit` checks every buyer's persisted
balance, product stock vs paid quantities, one matching successful transaction
per paid order, and absence of negative stock. Payment method is lowercase
`balance`, as required by the current backend.

The 20-VU, 2-minute hold is a short local baseline. For the planned larger test,
use `--vus 200 --ramp 5m --hold 15m --down 1m` on a suitably provisioned machine.
Do not claim that a short run satisfies that longer acceptance scenario. Each
mixed run modifies the dataset. Retain the first baseline's evidence and use
separate fresh volumes for controlled repeat comparisons. A failed k6 threshold
returns a nonzero exit code while retaining evidence; it must not be described
as a pass.

To stop while retaining data:

```sh
docker compose -f tests/load/compose.local.yaml stop
```

The generated credentials, JWT key, fixtures, results and node_modules are
ignored by Git. Keep local.env with the volumes; generating a different signing
key invalidates existing tokens until refreshed. Do not publish generated
credentials or raw application logs. No reset is performed automatically.

## CI and SonarCloud

JaCoCo runs in all Maven modules on `verify`/`install`; XML and HTML reports are
in each module's `target/site/jacoco/` and uploaded by CI. A module with no tests
or execution data has no per-module report. Common classes exercised by Core
are not rolled into an aggregate report. No arbitrary coverage threshold is
introduced; report the measured coverage, including low coverage.

Create a SonarCloud project bound to this GitHub repository and disable automatic
analysis in that project when using CI analysis. Configure repository Actions
variables `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION`, and Actions secret
`SONAR_TOKEN`. Never commit a token. The current workflow targets the EU
`https://sonarcloud.io` endpoint; a US-region project needs the corresponding
SonarCloud region configuration. The backend job installs the full reactor
before invoking the pinned Maven scanner, allowing inter-module resolution.
Standard `target/site/jacoco/jacoco.xml` reports are auto-discovered per module.
The scanner waits for the quality gate; fork PRs do not receive the token.
If keys are absent, CI explicitly reports configuration pending and does not
claim to have analyzed or passed the SonarCloud gate. Backend coverage is the
scope of this change; it does not add frontend coverage analysis.

References: [JaCoCo Maven](https://www.jacoco.org/jacoco/trunk/doc/maven.html),
[SonarScanner for Maven](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/scanners/sonarscanner-for-maven).

Optional isolated oversell validation, after the mixed baseline:

```sh
python3 tests/load/local.py oversell
```

This creates one extra listing (ID 2,000,000) with stock 1 and runs 20 concurrent
payments after preparing all 20 orders. Acceptance requires one persisted paid
order, final stock 0, consistent balances/transactions, and all other rejections
to have an explicit stock-conflict message. Those rejected HTTP requests remain
failures in the HTTP metric; they are expected only in this special scenario.
The runner refuses to reuse an existing oversell listing. This validates a
single Core instance, not cross-instance coordination.

For a separate fresh run without removing retained volumes, set `WP10_PROJECT`
to a new project name before all runner commands; stop the previous stack first
to free port 18080. The final baseline uses `ass-wp10-baseline-v2`:

```sh
export WP10_PROJECT=ass-wp10-baseline-v2
python3 tests/load/local.py up
# Do not seed again: this dataset is already present.
```

Use `docker compose -p "$WP10_PROJECT" -f tests/load/compose.local.yaml stop`
to stop that stack. The initial `ass-wp10-local` pilot's volumes are also retained,
but its mixed run is invalid due to a bind-mounted artifact being repackaged;
see the final results report. The corrected backend image contains a copied jar
and is unaffected by later builds on the host. Never rebuild or replace the
application image during a measurement.

Chat uses a 200ms subscription settling delay because Spring's simple broker
[does not implement STOMP receipts](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html).
Only a returned MESSAGE matching the unique payload is accepted; there is no
retry that could conceal lost messages. Roundtrip timing starts at SEND and
excludes that settling delay. Per-route read trends are included to locate
latency bottlenecks. The earlier baseline-v1 run exposed the old unsupported
receipt wait and is retained as a failed diagnostic run, not the final baseline.
