# Implementation Plan: CI/CD Pipelines

**Branch**: `003-ci-cd` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-ci-cd/spec.md`

## Summary

**GitHub Actions** in both repositories, with a check workflow on PRs and a release workflow on `main` that reuses the checks:
- **Backend release**: migrates with `scripts/db-migrate.sh production`, deploys with SAM, verifies `/health`, and **rolls back automatically**. It uses short-lived AWS credentials via **OIDC**, trusted only for the `production` environment, which is restricted to `main`.
- **Migration rehearsal on PRs**: runs on a disposable Neon branch, behind an owner-approved environment.
- **Web app**: builds a static SPA (Vite + React since research R10; originally a Next.js static export) and publishes it to **Firebase Hosting** (Spark plan) with a Hosting-only service account.
- **Owner setup**: one-time steps as commands in `docs/ci-cd.md`.

## Technical Context

**Language/Version**: YAML (GitHub Actions), Bash, CloudFormation. The app code is unchanged, apart from the web app's `next.config.ts` export settings.

**Primary Dependencies**: `actions/checkout@v4`, `actions/setup-java@v4`, `actions/setup-python@v5`, `actions/setup-node@v4`, `aws-actions/configure-aws-credentials@v4`, `aws-actions/setup-sam@v2`, `FirebaseExtended/action-hosting-deploy@v0`, `neonctl`

**Storage**: N/A (the Neon rehearsal branches are temporary)

**Testing**: the pipelines run the existing suites; this feature is validated through the quickstart drills (PR block, rehearsal, release, rollback drill, web publish)

**Target Platform**: GitHub-hosted `ubuntu-latest` runners; AWS `sa-east-1`; Firebase Hosting

**Project Type**: CI/CD for a web service plus a static web app (2 repositories)

**Performance Goals**: backend PR checks < 10 min; web < 5 min; backend release < 15 min; rollback < 2 min

**Constraints**: USD 0; no AWS keys; production only; minimal edits to shared frontend files (Clara's unpushed work)

**Scale/Scope**: 4 workflows, 1 bootstrap stack, 1 script change, 2 docs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| I. Spec as source of truth | ✅ | 14 FRs + the rollback clarification; nothing beyond them. |
| II. Layered architecture | ✅ N/A | No application code paths change. |
| III. REST contracts and DTOs | ✅ N/A | The pipeline contract is in `contracts/pipelines.md`. |
| IV. Automated tests | ✅ | The pipelines *enforce* principle IV: tests are required checks on every PR. The feature itself is verified by quickstart drills. |
| V. Simplicity | ✅ with justification | It reuses the owner scripts (FR-011) instead of re-implementing steps in YAML. Only well-known official actions are used. Web deploys use a Firebase SA key (Complexity Tracking). |
| Workflow (PR + review) | ✅ | Adds required checks on top of the existing one-approval rule. |
| Frontend baseline (outside git) II: static only | ✅ | `output: 'export'`; the build fails on server-only features. |

**Post-design re-check**: ✅.

## Project Structure

### Documentation (this feature)

```text
specs/003-ci-cd/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/pipelines.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code

```text
# ms-germina-wiki (backend)
.github/workflows/ci.yml          # PR checks (+ gated migration rehearsal); reusable
.github/workflows/release.yml     # main: checks → migrate → deploy → verify → rollback
infra/github-oidc.yaml            # bootstrap: OIDC provider + least-privilege deploy role
scripts/db-migrate.sh             # rehearse: credentials from neonctl (no DB secret in PR context)
docs/ci-cd.md                     # one-time setup commands + how releases work

# GerminaWiki (web app)
.github/workflows/ci.yml          # PR: lint + static build; reusable
.github/workflows/release.yml     # main: checks → Firebase Hosting live
firebase.json, .firebaserc        # hosting config (new files)
frontend/vite.config.ts           # (since R10) React plugin, dist output, port 3000; was next.config.ts
```

**Structure Decision**: this Spec Kit feature lives in the backend repo, where Spec Kit is set up; the web app gets no `.specify/` until Clara's push, to avoid conflicts. The web app commits reference this spec by path.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Long-lived Firebase service-account key (web app) | The Firebase deploy action needs Google credentials; the key is limited to Hosting publishing and stored only in the `production` environment (`main`-only). | Workload Identity Federation (keyless) needs the `gcloud` CLI plus pool/provider setup, which the owner doesn't have installed; for a static site the added setup outweighs the risk reduction. Kept as an upgrade path. |
| Two GitHub environments in the backend (`production`, `neon-rehearsal`) | The Neon API key could delete production data, so it must not reach unreviewed PR code; approval-gating does that. | A single secret at repo level would be readable by any same-repo PR workflow. Skipping PR rehearsal loses FR-004. |
