# Feature Specification: CI/CD Pipelines

**Feature Branch**: `003-ci-cd`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Task infra-cicd: pipelines for build, lint, test and deploy on push/merge, for both the backend API (ms-germina-wiki) and the web app (GerminaWiki). Pull requests must be checked automatically; merges to main must deploy to production (the only environment), including database migrations for the backend. Must stay free and must not store long-lived cloud credentials where avoidable."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every pull request is checked automatically (Priority: P1)

A teammate opens a pull request in either repository. Within minutes the pull request shows whether the change builds, passes lint and tests, and would package and deploy correctly, without touching production. A failing check blocks the merge.

**Why this priority**: With only production and a one-approval review rule, automated checks are the main safety net. They also make the team's quality practices visible on every PR.

**Independent Test**: Open a PR with a passing change and see green checks; open one with a failing test or lint error and see a red check that blocks merging.

**Acceptance Scenarios**:

1. **Given** a PR to the backend, **When** it's opened or updated, **Then** it's built, all automated tests run (including database-backed ones), the deployment package is built and smoke-tested, and the infrastructure definition is validated. Nothing is deployed.
2. **Given** a PR to the web app, **When** it's opened or updated, **Then** it's linted and built as a static site. Nothing is deployed.
3. **Given** any required check fails, **When** someone tries to merge normally, **Then** the merge is blocked.
4. **Given** a backend PR that adds or changes database migrations, **When** checks run, **Then** the migrations are also rehearsed against a disposable copy of production, and the copy is removed afterwards.

---

### User Story 2 - Merging the backend deploys it to production (Priority: P1)

When a backend PR is merged into `main`, the pipeline applies any pending database migrations and then deploys the API to production. It confirms the API is healthy afterwards. Nobody runs deploy commands by hand anymore.

**Why this priority**: Manual deploys depend on one person's machine and memory. An automated path makes every production change traceable to a reviewed merge.

**Independent Test**: Merge a trivial backend change and watch the pipeline apply migrations (none pending → no-op), deploy a new version, and report a healthy `/health` check.

**Acceptance Scenarios**:

1. **Given** a merge to `main`, **When** the pipeline runs, **Then** it re-runs the checks, applies pending migrations, deploys, and verifies the readiness check returns ready.
2. **Given** the post-deploy readiness check fails, **When** the pipeline ends, **Then** it's marked failed, and the documented rollback procedure applies.
3. **Given** two merges land close together, **When** both pipelines start, **Then** deploys run one at a time, in order, never concurrently.
4. **Given** the pipeline's cloud access, **When** it's inspected, **Then** it uses short-lived credentials limited to deploying this project, and no long-lived cloud key is stored in the repository settings.

---

### User Story 3 - Merging the web app publishes it (Priority: P2)

When a web app PR is merged into `main`, the pipeline builds the static site and publishes it to the live web address.

**Why this priority**: The web app has no feature code yet (the app shell is pending from a teammate), but the pipeline must be ready so the first real merge goes live automatically.

**Independent Test**: Merge a change to the web app and load the live address to see it.

**Acceptance Scenarios**:

1. **Given** a merge to `main` in the web app repository, **When** the pipeline runs, **Then** the static site is built and published to the production web address.
2. **Given** the site is published, **When** a browser loads it, **Then** it's served over HTTPS from the free hosting plan.

---

### Edge Cases

- A migration fails during a production pipeline: the API isn't deployed, the pipeline fails visibly, and the database is left at the last successful migration.
- The API is stopped by the cost guard when a deploy runs: the deploy still succeeds, and the stop stays in place. The post-deploy check reports "stopped" (429) as a distinct, non-healthy outcome.
- The database is suspended when checks or migrations run: they wait for it to wake up instead of failing.
- A PR from a fork, or from a contributor without access: checks run, but secrets and production credentials are never exposed to it.
- Pipeline minutes: runs must stay within the free allowance for the repositories' visibility.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every PR to either repository MUST trigger automated checks. Backend: build, all tests, package smoke test, infrastructure validation. Web app: lint and static build.
- **FR-002**: PR checks MUST NOT change production (no deploys, no production migrations).
- **FR-003**: The checks MUST be required for merging into `main` in both repositories.
- **FR-004**: Backend PRs that add or change migrations MUST rehearse them against a disposable copy of production, which is removed afterwards.
- **FR-005**: A merge to backend `main` MUST, in order: re-run the checks, apply pending migrations, deploy the API, and verify readiness.
- **FR-006**: The API MUST NOT be deployed if migrations fail.
- **FR-007**: Production deployments MUST NOT run concurrently in the same repository.
- **FR-008**: The backend pipeline MUST authenticate to the cloud provider with short-lived credentials, restricted to what deploying this project needs, and usable only from the backend repository's `main` branch.
- **FR-009**: A merge to web app `main` MUST build the static site and publish it to the production web address.
- **FR-010**: Secrets (database credentials, deploy credentials) MUST be stored only in the repositories' encrypted secret stores, never exposed to PRs from forks, and never printed in logs.
- **FR-011**: The pipelines MUST reuse the same scripts the owner uses by hand (smoke test, migrations), so there is a single deployment procedure.
- **FR-012**: All pipelines MUST stay within free usage limits.
- **FR-013**: The one-time setup (cloud trust, repository secrets, required checks) MUST be documented as commands.

### Key Entities

- **Check pipeline**: runs on PRs; produces pass/fail statuses that gate merging.
- **Release pipeline**: runs on merges to `main`; ordered steps with a single-flight lock per repository.
- **Deploy identity**: a short-lived, narrowly scoped identity the release pipeline assumes; trusted only for one repository's `main` branch.
- **Repository secrets and variables**: encrypted values (credentials) and plain configuration (URLs, identifiers) available to pipelines.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of PRs in both repositories show automated check results before merge; a PR with a failing test or lint error can't be merged without an admin override.
- **SC-002**: Backend PR checks finish in under 10 minutes; web app PR checks in under 5 minutes.
- **SC-003**: A backend merge reaches a healthy production API in under 15 minutes, with no manual step.
- **SC-004**: A web app merge is live at the production address in under 5 minutes.
- **SC-005**: No long-lived cloud access key for the backend deployment exists in repository settings.
- **SC-006**: The pipelines add USD 0.00 to monthly cost.
- **SC-007**: A deliberately failing migration on `main` stops the pipeline before the API is deployed, in 100% of attempts.

## Assumptions

- Both repositories are public on GitHub, so pipeline minutes are free.
- The web app is deployed as a static export to the existing free-plan hosting project (`germinawiki`). Its build output location and static-export configuration are set up here with minimal changes, so they don't conflict with a teammate's unpushed app-shell work.
- Web app hosting may use a narrowly scoped service-account key if keyless authentication isn't feasible without extra tooling. This is documented as a known trade-off (see plan).
- The backend's existing manual procedures (`docs/deployment.md`, `scripts/db-migrate.sh`, `scripts/smoke-lambda-package.sh`) are the source of truth; the pipelines call them.
- Preview deployments of the web app per PR are out of scope: the API only allows the production web origin and localhost, and there's only one environment.
