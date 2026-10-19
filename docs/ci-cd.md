# CI/CD

How a change gets from a pull request to production, and how to roll one back.
The workflows are in `.github/workflows/`: `ci.yml` calls `cd.yml`, which
calls `deploy.yml` once per environment.

## On every pull request

| Check | What it does |
|---|---|
| backend | Maven build and unit tests for common, core, search and chat; SonarCloud analysis and quality gate |
| load-scripts | Tests the k6 load-test scripts in `tests/load` |
| frontend | ESLint without autofix, then the production build |
| gitleaks | Secrets anywhere in the git history |
| trivy | Vulnerable Maven/npm dependencies and Dockerfile/Terraform misconfigurations; fails on CRITICAL |
| images (core, search, chat) | Builds each service image and scans it with Trivy; fails on CRITICAL |

The pull request page shows nine checks: the six jobs, with images once per
service, plus SonarCloud's own check. `cd` is skipped: it runs on main only.

main is protected. A pull request merges only when backend, frontend,
gitleaks, trivy and the three images checks have passed.

Accepted Trivy findings, each with a reason and an expiry date, are in
`.trivyignore.yaml`.

## After a merge to main

CI runs again on main. When every job passes, the same run continues into CD:

1. **release**: takes the commit SHA as the release tag.
2. **images**: builds core, search and chat and pushes them to ECR as
   `c2csectrade/<service>:<sha>`. Tags are immutable, so a SHA is built once.
3. **frontend**: builds the Vue app once for both environments.
4. **staging**: deploys the images to ECS, uploads the frontend to S3,
   invalidates CloudFront, then smoke-tests the frontend, Core and Search.
5. **production**: the same, once a required reviewer of the production
   Environment approves.

The run's Summary lists the images pushed and, per environment, each
service's task definition revision before and after.

GitHub authenticates to AWS with OIDC: no AWS keys are stored in GitHub, and
the deploy roles can be assumed from main and its Environments only.

If a new task never becomes healthy, the ECS deployment circuit breaker rolls
the service back to its previous revision, and the deploy job fails.

## Rolling back

Images are never overwritten, so an earlier release's images are still in ECR.

1. Find the commit to go back to: Actions → CI, a green run on main. Copy its
   full 40-character SHA.
2. Actions → CD → **Run workflow**, branch `main`, `image_tag` = that SHA.
3. The run skips the build and redeploys those images to staging, then to
   production after approval.

A rollback redeploys the services only, not the frontend. To roll back the
frontend as well, revert the commit on main and let the pipeline release it.

Schema changes are not rolled back: the database scripts (`init.sql`,
`infra/db/grants.sql`) are applied by hand.
