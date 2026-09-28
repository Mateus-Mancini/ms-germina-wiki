---

description: "Task list for 003-ci-cd"
---

# Tasks: CI/CD Pipelines

**Input**: Design documents from `/specs/003-ci-cd/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/pipelines.md, quickstart.md

**Tests**: Pipelines are validated with the quickstart drills. The existing test suites become required checks.

**Organization**: by user story. `BE:` = backend repo `ms-germina-wiki`, `FE:` = web app repo `GerminaWiki`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 BE: Create `infra/github-oidc.yaml` (CloudFormation): GitHub OIDC provider, role `germinawiki-github-deploy` trusting only `repo:Mateus-Mancini@115586427/ms-germina-wiki@1365859536:environment:production` (`aud` sts.amazonaws.com), least-privilege policy per research R2, output `RoleArn`
- [X] T002 BE: Change `scripts/db-migrate.sh rehearse` to take the role, password and database from `neonctl connection-string` of the rehearsal branch, so rehearsal needs only `NEON_API_KEY` or a local `neonctl` login, plus `NEON_PROJECT_ID` (research R4)

---

## Phase 2: Foundational (owner, one-time)

- [X] T003 BE: Write `docs/ci-cd.md` with the one-time setup as commands: deploy `infra/github-oidc.yaml`; create environments `production` (main-only) and `neon-rehearsal` (owner reviewer) with their secrets and variables via `gh`; Firebase `hosting:github` setup; required checks via `gh api` (contracts/pipelines.md)
- [X] T004 Owner runs the `docs/ci-cd.md` setup (AWS bootstrap stack, GitHub environments, secrets and variables, Firebase service account)
  - Verified 2026-09-28: bootstrap stack `germinawiki-github-oidc` CREATE_COMPLETE (trust subject `repo:Mateus-Mancini@115586427/ms-germina-wiki@1365859536:environment:production`); backend environments `production` (main-only; DB secrets; role, email and origin variables) and `neon-rehearsal` (owner reviewer; Neon key and project); web app environment `production` (main-only) holding the Hosting-only service-account key, with no repo-level secrets. Created manually (`firebase init hosting:github` couldn't access the repo).

**Checkpoint**: both repos have environments and secrets; the AWS deploy role exists

---

## Phase 3: User Story 1 - Every PR is checked automatically (Priority: P1) 🎯 MVP

- [X] T005 [P] [US1] BE: Create `.github/workflows/ci.yml` (`pull_request`, `workflow_call`): job `build-test-package` (Temurin 21, Maven cache, Python 3.13, SAM CLI): `./mvnw -B verify`, guard tests, `clean package -Plambda`, `scripts/smoke-lambda-package.sh`, `sam validate --lint`. No secrets
- [X] T006 [US1] BE: Add job `rehearse-migrations` to `ci.yml`: runs only when `src/main/resources/db/migration/**` changed (paths filter via `git diff` against the base), environment `neon-rehearsal`, runs `scripts/db-migrate.sh rehearse` with `NEON_API_KEY`
- [X] T007 [P] [US1] FE: Read the Next.js 16 static-export guide in `node_modules/next/dist/docs/`, then set `output: "export"` and `images: { unoptimized: true }` in `next.config.ts` (only those two keys)
- [X] T008 [P] [US1] FE: Create `.github/workflows/ci.yml` (`pull_request`, `workflow_call`): job `lint-build` (Node 22): `npm ci`, `npm run lint`, `npm run build`, and assert `out/index.html` exists
- [X] T009 [US1] Owner makes `build-test-package` (BE) and `lint-build` (FE) required checks on `main` (command in `docs/ci-cd.md`); validate per quickstart §1
  - Done by the owner (2026-09-28): `main` requires `build-test-package` (backend) and `lint-build` (web app), plus 1 approving review.

---

## Phase 4: User Story 2 - Merging the backend deploys it (Priority: P1)

- [X] T010 [US2] BE: Create `.github/workflows/release.yml` (`push` to `main`, `workflow_dispatch` with input `simulate_failed_verify`):
  - job `checks` calls `ci.yml`
  - job `release` (environment `production`, `concurrency: production`, `permissions: id-token: write, contents: read`) runs: OIDC credentials → `DB_MIGRATE_CONFIRM=yes scripts/db-migrate.sh production` → record `live` version → `sam deploy --no-confirm-changeset --no-fail-on-empty-changeset` → verify `/health` (6 × 10 s) → on failure other than 429, `update-alias` back and fail → job summary with both versions
- [X] T011 [US2] Validate per quickstart §3 (merge → healthy release) and §4 (rollback drill via `workflow_dispatch`)
  - Release run 36360271170 (merge of #8): the first attempt failed at OIDC (sub-claim format, fixed in #9) with nothing deployed; the re-run succeeded end to end: migrations up to date, live 6 → 7, `/health` 200 ready on attempt 1, all 3 stack roles now carry the permissions boundary. Rollback drill run 36360889315 (`simulate_failed_verify`): verify failed after 6 × 404, the rollback step ran 1 s later, the run was marked failed, and production stayed healthy on version 7 (SC-008).

---

## Phase 5: User Story 3 - Merging the web app publishes it (Priority: P2)

- [X] T012 [P] [US3] FE: Create `firebase.json` (`hosting.public: "out"`, `cleanUrls: true`, ignore patterns) and `.firebaserc` (default `germinawiki`)
- [X] T013 [US3] FE: Create `.github/workflows/release.yml` (`push` to `main`, `workflow_dispatch`): `checks` calls `ci.yml` → `deploy` (environment `production`, `concurrency: production`): build, then `FirebaseExtended/action-hosting-deploy@v0` with `channelId: live`, `projectId: germinawiki`
- [X] T014 [US3] Validate per quickstart §6 (`https://germinawiki.web.app` serves the build)
  - Web app release run 36360280827: `lint-build` 23 s + deploy 55 s (SC-004); https://germinawiki.web.app returns 200.

---

## Phase 6: Polish

- [X] T015 [P] BE: Update `README.md` (CI badge, pipelines section) and `docs/deployment.md` (releases are automatic on merge; manual deploy is the fallback); add a backward-compatible-migrations rule to `docs/database-migrations.md` (spec edge case: rollback restores code, not schema)
- [X] T016 [P] FE: Add a short "Deploy" section to the web app `README.md` pointing to the pipelines and `docs/ci-cd.md` in the backend repo
- [X] T017 Validate quickstart §2 (gated rehearsal), §7 (credentials) and §8 (cost)
  - §2: drill PR #10 (never merged) paused `rehearse-migrations` for owner approval; once approved, it migrated a disposable Neon copy v1 → v2 and deleted it; production stayed at v1. The drill also exposed a test pinning the latest schema version, which would have failed every migration PR (fixed in #11). §7: no repo-level secrets in either repo, 0 access keys for `mateus-admin` and root, deploy trust bound to the immutable-id subject. §8: both budgets show USD 0.00 actual spend; the release after #9 was a no-op thanks to reproducible builds ("No changes to deploy").

---

## Dependencies & Execution Order

- T001–T003 → T004 (owner setup) → the US1–US3 workflows can be merged, but release runs need T004 done
- FE work (T007, T008, T012, T013, T016) goes on one FE branch, in small commits, touching only `next.config.ts` among existing files
- Backend PR checks (T005) work before T004, since they need no secrets

## Implementation Strategy

1. BE: T001, T002, T003, T005, T006, T010, T015 on `003-ci-cd`; FE: T007, T008, T012, T013, T016 on `003-ci-cd` in the FE repo
2. Open both PRs; `ci.yml` validates itself on its own PR (T009 partially)
3. The owner does the T004 setup → merge BE → the first automated release (T011) → merge FE → first publish (T014)
4. T009 required checks, T017 remaining drills
