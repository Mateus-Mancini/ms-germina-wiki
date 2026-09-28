# Research: CI/CD Pipelines

**Feature**: `003-ci-cd` | **Date**: 2026-09-27

## R1. CI platform

- **Decision**: **GitHub Actions** in both repositories.
- **Rationale**: both repos are public on GitHub, where standard runners are free and unlimited (FR-012, SC-006). `ubuntu-latest` has Docker, so Testcontainers and the package smoke test run unchanged.
- **Alternatives considered**: AWS CodePipeline/CodeBuild (billed per minute beyond a small free tier, and a second place to look); GitLab CI (the code is on GitHub).

## R2. Backend cloud credentials (FR-008, SC-005)

- **Decision**: **GitHub OIDC → AWS IAM role** (`aws-actions/configure-aws-credentials@v4`, `role-to-assume`).
  - The role's trust policy accepts only tokens with `aud = sts.amazonaws.com` and `sub = repo:Mateus-Mancini@115586427/ms-germina-wiki@1365859536:environment:production`.
  - The GitHub **environment `production`** is restricted to the `main` branch, so no PR or other branch can obtain AWS credentials.
  - *Found at the first release*: GitHub's `sub` claim includes the immutable owner and repository ids (`owner@id/repo@id`). The name-only subject was rejected (`AccessDenied`, confirmed in CloudTrail). The id-based subject is also safer: a renamed or re-created repository can't inherit the trust.
  - Sessions last 1 h. No AWS keys exist anywhere.
- **Least privilege**, as a customer-managed policy in a bootstrap stack (`infra/github-oidc.yaml`):
  - CloudFormation on `stack/ms-germina-wiki/*`, plus `CreateChangeSet` on the SAM transform
  - S3 on the SAM artifacts bucket only (`aws-sam-cli-managed-default-samclisourcebucket-*`), plus read access to SAM's `aws-sam-cli-managed-default` stack used by `resolve_s3`
  - Lambda on `function:germinawiki-*`
  - IAM role management and `PassRole` only on `role/ms-germina-wiki-*` (the roles SAM generates)
  - Scheduler on `schedule/default/germinawiki-*`
  - SNS on `germinawiki-alerts`
  - Budgets on `germinawiki-monthly`
  - Logs on `/aws/lambda/germinawiki-*`
- **Privilege-escalation guard**: the deploy role must create IAM roles for the stack's functions and scheduler, so a malicious or mistaken template could otherwise create an admin role. The bootstrap defines a **permissions boundary** `germinawiki-workload-boundary` (logs, `GetMetricStatistics`, `ListFunctions`, get/put concurrency and invoke on `germinawiki-*`, SNS publish on the alert topic).
  - The deploy role may only `CreateRole`, `PutRolePolicy`, `AttachRolePolicy` or `PutRolePermissionsBoundary` when that boundary is set, and is denied `DeleteRolePermissionsBoundary`.
  - `template.yaml` applies the boundary to every generated role (`Globals.Function.PermissionsBoundary` and the ScheduleV2 `PermissionsBoundary`).
  - `lambda:*` stays scoped by resource to `function:germinawiki-*`.
- **Bootstrap**: the owner deploys `infra/github-oidc.yaml` once from their machine (`aws cloudformation deploy`). It creates the OIDC provider, the role and the policy. It's kept separate from the app stack, so the pipeline can't widen its own permissions.

## R3. Backend check pipeline (FR-001, FR-002, SC-002)

- **Decision**: a workflow `ci.yml` on `pull_request` and `workflow_call`. The release pipeline calls it, so a push to `main` isn't checked twice. It has one job `build-test-package` (ubuntu, Temurin 21, Maven cache, Python 3.13):
  - `./mvnw -B verify` (all tests, including Testcontainers on PG 18)
  - guard unit tests
  - `./mvnw -Plambda -DskipTests clean package`
  - `scripts/smoke-lambda-package.sh`
  - `sam validate --lint`

  It needs no secrets and no cloud access (FR-002). Expected duration is 4–6 min, mostly Maven and the container pulls.

## R4. Migration rehearsal on PRs (FR-004)

- **Decision**: a job `rehearse-migrations` in `ci.yml`, run only when the PR changes `src/main/resources/db/migration/**`, in GitHub **environment `neon-rehearsal`**, which has a **required reviewer (the owner)**. The job waits for approval before any secret is exposed.
- **Rationale**: a rehearsal needs a Neon API key, which can manage all branches, including production. Same-repo PRs receive secrets, so an unreviewed PR could exfiltrate the key. Approval-gating keeps the key away from unreviewed code, while fork PRs never get secrets at all (FR-010).
- **Credential reduction**: `db-migrate.sh rehearse` takes the branch's role, password and database from `neonctl connection-string` (the branch copies production's roles). The rehearsal therefore needs only `NEON_API_KEY` and `NEON_PROJECT_ID`, and no database password lives in the PR context. Owner usage is unchanged.

## R5. Backend release pipeline (FR-005–FR-007, FR-014, SC-003, SC-008)

- **Decision**: workflow `release.yml` on `push` to `main`, `concurrency: { group: production, cancel-in-progress: false }` (FR-007), environment `production`. The job `release` runs after the checks:
  1. **Checks**: reuses `ci.yml` via `workflow_call`, so a merge is re-verified with the same steps.
  2. **Migrations**: `DB_MIGRATE_CONFIRM=yes scripts/db-migrate.sh production`. `actions/checkout` puts the job on local branch `main`, so the script's main/clean/`HEAD == origin/main` guard applies unchanged. If a newer commit has landed, the guard refuses, and the newer run (queued by concurrency) deploys instead. On failure the job stops, and the API isn't deployed (FR-006).
  3. **Deploy**: record the current `live` version, then `sam deploy --no-confirm-changeset --no-fail-on-empty-changeset` with all five parameters from secrets and variables.
  4. **Verify**: `GET /health` up to 6 × 10 s. `200 ready` means success. `429` means the cost guard has stopped the API: this is reported, the job fails, and there is **no** rollback, since the new code isn't at fault.
  5. **Rollback** on any other result: `aws lambda update-alias --name live --function-version <recorded>`, write both versions to the job summary, and fail. The measured rollback time is ~2 s, so SC-008's 2 minutes is dominated by the verify retries.
- **Why reuse scripts**: FR-011. The owner and the pipeline run the exact same smoke test and migrations.

## R6. Web app hosting and static export (FR-009)

- **Decision**:
  - Next.js `output: 'export'` with `images: { unoptimized: true }`, producing `out/`.
  - `firebase.json` hosting from `out/`, with `cleanUrls: true`, and `.firebaserc` default project `germinawiki`.
  - Firebase Hosting **Spark** (free) plan.
- **Minimal-conflict rule**: only `next.config.ts` (two keys) is touched among Clara's likely files. `firebase.json`, `.firebaserc` and the workflows are new files.
- **Next.js 16 note**: per `AGENTS.md`, the static-export guide in `node_modules/next/dist/docs/` is read before changing the config.

## R7. Web app deploy credentials

- **Decision**: a dedicated Firebase service account `github-hosting-deploy` with **Firebase Hosting Admin**, **API Keys Viewer** and **Cloud Run Viewer**, created in the Cloud console. Its JSON key is stored as the **environment** secret `FIREBASE_SERVICE_ACCOUNT_GERMINAWIKI` in `production` (restricted to `main`). The deploy uses `FirebaseExtended/action-hosting-deploy@v0` with `channelId: live`.
  - *Changed during implementation*: `firebase init hosting:github` couldn't access the repository through its GitHub OAuth app, and it would have stored the key at repo level (readable by same-repo PR workflows). The manual route keeps the key environment-scoped.
- **Trade-off (documented)**: this is a long-lived key, although its permissions only cover publishing to Hosting and it's only readable by `main` releases. The keyless alternative (Workload Identity Federation) needs the `gcloud` CLI and a separate pool/provider setup, which isn't installed and isn't justified for a static site. SC-005 (no long-lived keys) applies to the backend cloud account, and holds there. Upgrade path: WIF, if the team ever installs `gcloud`.

## R8. Web app check pipeline (FR-001)

- **Decision**: `ci.yml` on `pull_request` and `workflow_call`: Node 22 LTS, `npm ci`, `npm run lint`, `npm run build` (static export, which fails on server-only features, enforcing the frontend baseline principle II). There's no test runner yet; when Clara's setup adds one, it gets a step here.
- **Release**: `release.yml` on push to `main` runs the checks (reused), then the Firebase deploy in environment `production`, with `concurrency: production`.

## R9. Required checks (FR-003, SC-001)

- **Decision**: add the check job names to each repo's `main` branch protection (`required_status_checks`, `strict: false`), using a `gh api` command the owner runs (admin action). The existing one-approval rule stays.
  - Backend: `build-test-package`
  - Web app: `lint-build`
