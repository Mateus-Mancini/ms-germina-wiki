---

description: "Task list for 002-database-migrations"
---

# Tasks: Database Migrations

**Input**: Design documents from `/specs/002-database-migrations/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included (constitution principle IV).

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [ ] T001 Add `spring-boot-starter-flyway` and `org.flywaydb:flyway-database-postgresql` in **test** scope in `pom.xml` (research R3: never on the runtime classpath)
- [ ] T002 Add `flyway-maven-plugin` (Boot-managed version) in `pom.xml`, with plugin dependencies `flyway-database-postgresql` and the PostgreSQL driver, and fixed settings `cleanDisabled=true`, `outOfOrder=false`, `connectRetries=10` (contracts/migrations.md)
- [ ] T003 [P] Switch the Testcontainers image to `postgres:18-alpine` in `src/test/java/com/wikigerminare/TestcontainersConfiguration.java`, `src/test/java/com/wikigerminare/HealthIntegrationTest.java`, `src/test/java/com/wikigerminare/lambda/SnapStartPrimingTest.java` and `scripts/smoke-lambda-package.sh` (FR-007)

---

## Phase 2: Foundational

- [ ] T004 Create `src/main/resources/db/migration/V1__initial_schema.sql` from the team's `db-script`, SQL verbatim plus a header comment crediting the original script. Commit with `Co-authored-by: CamillaMorenoA <178440498+CamillaMorenoA@users.noreply.github.com>` (FR-001, FR-002, FR-012)

**Checkpoint**: `./mvnw test` migrates every Testcontainers database to V1

---

## Phase 3: User Story 2 - Tests run against the real schema (Priority: P1) 🎯 MVP

**Goal**: every DB-backed test starts from the migrated schema on PostgreSQL 18

**Independent Test**: quickstart §1, including the temporary broken migration

- [ ] T005 [US2] Write `src/test/java/com/wikigerminare/SchemaMigrationTest.java` (`@SpringBootTest` + Testcontainers). It asserts the catalog in data-model.md: 8 tables, enum `user_role` (`admin`,`member`) and `comment_status` (`OPEN`,`RESOLVED`), extension `pgcrypto`, the 4 `*_updated_at` triggers, the GIN index `idx_pages_full_text_search`, and `flyway_schema_history` latest version `1` with `success = true`
- [ ] T006 [US2] Run quickstart §1 (full suite green; temporary `V999__broken.sql` fails the suite, then removed) and §2 (local run migrates); record the results in the PR

**Checkpoint**: MVP: the schema is versioned, and every test uses it

---

## Phase 4: User Story 1 - Production has the team's schema, applied safely (Priority: P1)

**Goal**: V1 is rehearsed on a disposable Neon branch, then applied to production from `main`

**Independent Test**: quickstart §3–§4 plus the catalog comparison

- [ ] T007 [US1] Create `scripts/db-migrate.sh` with the `rehearse`, `production` and `info` subcommands per contracts/migrations.md:
  - derive the direct URL by stripping `-pooler`
  - `rehearse` creates a Neon branch with `--expires-at now+1h`, runs `flyway info/migrate/info` against its direct endpoint, and deletes the branch on exit
  - `production` checks that it's on `main`, the tree is clean and `HEAD == origin/main`, shows `info`, and requires `yes` (or `DB_MIGRATE_CONFIRM=yes`)
  - secrets are never echoed
- [ ] T008 [US1] Add `NEON_PROJECT_ID` to the owner's secrets file (owner action; documented in `docs/deployment.md` prerequisites)
- [ ] T009 [US1] Rehearse V1 per quickstart §3 and record the output in the PR
- [ ] T010 [US1] **After this feature is merged**: apply V1 to production from `main` per quickstart §4, re-run to prove idempotency (SC-002), and compare the production catalog with data-model.md (SC-001)

**Checkpoint**: production is at schema version 1

---

## Phase 5: User Story 3 - The team can evolve the schema safely (Priority: P2)

**Goal**: a documented, enforced convention for new migrations

**Independent Test**: quickstart §5 (branch guard, checksum guard)

- [ ] T011 [P] [US3] Write `docs/database-migrations.md`: naming convention, immutability rule, add → test → rehearse → PR → owner applies from `main`, what to do when a migration fails, and "fix forward" instead of editing (FR-011)
- [ ] T012 [US3] Validate the guards per quickstart §5: production refused from a feature branch; an edited V1 rejected by checksum on a rehearsal branch (SC-004). This needs T010 done, so that V1 is recorded on `main`

---

## Phase 6: Polish

- [ ] T013 [P] Update `README.md` (layout, the migrations link) and `docs/deployment.md` (the `NEON_PROJECT_ID` prerequisite, running migrations before deploying code that needs them)
- [ ] T014 Verify quickstart §6: the Lambda zip contains no Flyway, the smoke test passes, and the API's `/health` is unaffected (FR-008, SC-007)

---

## Dependencies & Execution Order

- Setup → Foundational (V1) → US2 → US1 → US3 → Polish
- **T010 and T012 run after the PR merges**, because of the main-only rule (clarification). All other tasks happen on the feature branch
- T003, T011 and T013 touch separate files and can run in parallel with neighbouring tasks

## Implementation Strategy

1. T001–T006: versioned schema, and tests on it (MVP, fully local)
2. T007–T009: script and rehearsal on a Neon branch (no production change)
3. Merge the PR → T010 applies to production → T012 guard checks
4. T011, T013, T014: docs and final verification
