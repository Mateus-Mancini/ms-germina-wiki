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

- [X] T001 BE: Create `infra/github-oidc.yaml` (CloudFormation): GitHub OIDC provider, role `germinawiki-github-deploy` trusting only `repo:Mateus-Mancini/ms-germina-wiki:environment:production` (`aud` sts.amazonaws.com), least-privilege policy per research R2, output `RoleArn`
- [X] T002 BE: Change `scripts/db-migrate.sh rehearse` to take the role, password and database from `neonctl connection-string` of the rehearsal branch, so rehearsal needs only `NEON_API_KEY` or a local `neonctl` login, plus `NEON_PROJECT_ID` (research R4)

---

## Phase 2: Foundational (owner, one-time)

- [ ] T003 BE: Write `docs/ci-cd.md` with the one-time setup as commands: deploy `infra/github-oidc.yaml`; create environments `production` (main-only) and `neon-rehearsal` (owner reviewer) with their secrets and variables via `gh`; Firebase `hosting:github` setup; required checks via `gh api` (contracts/pipelines.md)
- [ ] T004 Owner runs the `docs/ci-cd.md` setup (AWS bootstrap stack, GitHub environments, secrets and variables, Firebase service account)

**Checkpoint**: both repos have environments and secrets; the AWS deploy role exists

---

## Phase 3: User Story 1 - Every PR is checked automatically (Priority: P1) 🎯 MVP

- [X] T005 [P] [US1] BE: Create `.github/workflows/ci.yml` (`pull_request`, `workflow_call`): job `build-test-package` (Temurin 21, Maven cache, Python 3.13, SAM CLI): `./mvnw -B verify`, guard tests, `clean package -Plambda`, `scripts/smoke-lambda-package.sh`, `sam validate --lint`. No secrets
- [X] T006 [US1] BE: Add job `rehearse-migrations` to `ci.yml`: runs only when `src/main/resources/db/migration/**` changed (paths filter via `git diff` against the base), environment `neon-rehearsal`, runs `scripts/db-migrate.sh rehearse` with `NEON_API_KEY`
- [ ] T007 [P] [US1] FE: Read the Next.js 16 static-export guide in `node_modules/next/dist/docs/`, then set `output: "export"` and `images: { unoptimized: true }` in `next.config.ts` (only those two keys)
- [ ] T008 [P] [US1] FE: Create `.github/workflows/ci.yml` (`pull_request`, `workflow_call`): job `lint-build` (Node 22): `npm ci`, `npm run lint`, `npm run build`, and assert `out/index.html` exists
- [ ] T009 [US1] Owner makes `build-test-package` (BE) and `lint-build` (FE) required checks on `main` (command in `docs/ci-cd.md`); validate per quickstart §1

---

## Phase 4: User Story 2 - Merging the backend deploys it (Priority: P1)

- [ ] T010 [US2] BE: Create `.github/workflows/release.yml` (`push` to `main`, `workflow_dispatch` with input `simulate_failed_verify`):
  - job `checks` calls `ci.yml`
  - job `release` (environment `production`, `concurrency: production`, `permissions: id-token: write, contents: read`) runs: OIDC credentials → `DB_MIGRATE_CONFIRM=yes scripts/db-migrate.sh production` → record `live` version → `sam deploy --no-confirm-changeset --no-fail-on-empty-changeset` → verify `/health` (6 × 10 s) → on failure other than 429, `update-alias` back and fail → job summary with both versions
- [ ] T011 [US2] Validate per quickstart §3 (merge → healthy release) and §4 (rollback drill via `workflow_dispatch`)

---

## Phase 5: User Story 3 - Merging the web app publishes it (Priority: P2)

- [ ] T012 [P] [US3] FE: Create `firebase.json` (`hosting.public: "out"`, `cleanUrls: true`, ignore patterns) and `.firebaserc` (default `germinawiki`)
- [ ] T013 [US3] FE: Create `.github/workflows/release.yml` (`push` to `main`, `workflow_dispatch`): `checks` calls `ci.yml` → `deploy` (environment `production`, `concurrency: production`): build, then `FirebaseExtended/action-hosting-deploy@v0` with `channelId: live`, `projectId: germinawiki`
- [ ] T014 [US3] Validate per quickstart §6 (`https://germinawiki.web.app` serves the build)

---

## Phase 6: Polish

- [ ] T015 [P] BE: Update `README.md` (CI badge, pipelines section) and `docs/deployment.md` (releases are automatic on merge; manual deploy is the fallback); add a backward-compatible-migrations rule to `docs/database-migrations.md` (spec edge case: rollback restores code, not schema)
- [ ] T016 [P] FE: Add a short "Deploy" section to the web app `README.md` pointing to the pipelines and `docs/ci-cd.md` in the backend repo
- [ ] T017 Validate quickstart §2 (gated rehearsal), §7 (credentials) and §8 (cost)

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
