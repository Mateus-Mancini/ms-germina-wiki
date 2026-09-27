# Tasks: pages-api

**Input**: Design documents in `specs/002-pages-api/`


**Tests**: Included because Constitution Principle IV requires automated tests. For each story, author tests before that story's behavior and confirm the expected failures before implementation.

**Organization**: Tasks are grouped by the three user stories in `spec.md`. Page schema setup is a read-only inspection of the existing PostgreSQL schema; do not add DDL, migrations, replacement tables, or out-of-scope API work.


**Purpose**: Confirm the database and integration-test prerequisites without changing schema.

- [ ] T001 Compare the deployed PostgreSQL schema read-only with these supplied facts and record any environment discrepancy plus the slug/spec blocker in `specs/002-pages-api/plan.md`: `title VARCHAR(255) NOT NULL`; `content TEXT NOT NULL DEFAULT ''` (empty allowed, no schema length limit); `slug VARCHAR(300) NOT NULL UNIQUE` (no default); `version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)`; nullable `folder_id` with `ON DELETE SET NULL`; required `created_by` with `ON DELETE RESTRICT`; nullable `updated_by` with `ON DELETE SET NULL`; timestamps default to `CURRENT_TIMESTAMP`; inbound page references use the supplied CASCADE/SET NULL actions. Version name/type/default are known; verify deployed rows/defaults and Hibernate can start at `1` without manual increments. Do not change the database or `spec.md` in this task.
- [ ] T002 Verify a PostgreSQL test/development database with the existing `pages`, `folders`, and `users` schema is reachable using the team's environment configuration; document prerequisites and observed readiness in `specs/002-pages-api/quickstart.md` without recording credentials or adding a schema fixture.

## Phase 2: Foundational
**Purpose**: Map the verified existing page schema and persistence used by all stories.

- [ ] T003 Create `Page` in `src/main/java/com/wikigerminare/pages/Page.java` mapping exactly `id`, `title`, `slug`, `content`, `version`, `folder_id`, `created_by`, `updated_by`, `created_at`, and `updated_at`. Map `version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)` as `Integer @Version`; provider manages increments, with no manual increment. Map `title VARCHAR(255) NOT NULL`; `content TEXT NOT NULL DEFAULT ''` with empty string allowed and no artificial limit; `slug VARCHAR(300) NOT NULL UNIQUE` with no default; nullable `folder_id`/`ON DELETE SET NULL`; required scalar `created_by`/`ON DELETE RESTRICT`; nullable scalar `updated_by`/`ON DELETE SET NULL`; and supplied timestamp defaults. Reuse `Folder`, add no `User` entity, DDL or application cascade. `slug` is absent from `spec.md`; do not invent its API source or add it to a DTO.
- [ ] T004 Create the basic Spring Data repository in `src/main/java/com/wikigerminare/pages/PageRepository.java` after the `Page` mapping; support UUID lookup and the complete list required by the contract without pagination/filtering.

**Checkpoint**: `Page`/Repository validate against the existing PostgreSQL schema. No source mapping work proceeds until the version column details are verified.

## Phase 3: User Story 1 - Create and Consult Pages (Priority: P1, MVP)

**Goal**: Create and retrieve a page with raw Markdown, folder, creator, timestamps, numeric version and ETag.


### Tests for User Story 1

- [ ] T005 [P] [US1] Add Service tests for page creation, creator UUID propagation, existing/missing folder, title validation at 255 characters, empty content and exact Markdown preservation in `src/test/java/com/wikigerminare/pages/PageServiceTest.java`. Do not test slug derivation; creation remains blocked until its source/rule is resolved in `spec.md`.
- [ ] T006 [P] [US1] Add MVC tests for `POST /api/pages` and `GET /api/pages/{id}`, including `201`, `200`, `400`, `404`, response ETag, numeric version and principal propagation, in `src/test/java/com/wikigerminare/pages/PageControllerTest.java`.
- [ ] T007 [P] [US1] Add PostgreSQL-backed entity/Repository tests for all supplied column mappings, version starting at `1`, JPA-managed increments, existing folder FK and exact Markdown round-trip in `src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java`. A persistence fixture may set a unique slug directly; do not infer API slug behavior.

### Implementation for User Story 1

- [ ] T008 [US1] Create `CreatePageRequest` and `PageResponse` in `src/main/java/com/wikigerminare/pages/dto/CreatePageRequest.java` and `src/main/java/com/wikigerminare/pages/dto/PageResponse.java`, plus `PageNotFoundException` in `src/main/java/com/wikigerminare/pages/PageNotFoundException.java`; require a non-blank title of at most 255 characters, require content but allow an empty string with no artificial length limit, and omit `createdBy`/version from request fields. Do not add slug to the contract.
- [ ] T009 [US1] Implement transactional create and get-by-ID in `src/main/java/com/wikigerminare/pages/PageService.java`; validate the required creation `folderId` using the existing `FolderRepository`, persist raw Markdown, set timestamps per schema and return version `1` on creation. Do not complete/persist the API create path until `spec.md` resolves the required `slug` column (`VARCHAR(300) NOT NULL UNIQUE`, no default); do not add slug to the DTO or derive it by assumption.
- [ ] T010 [US1] Implement `POST /api/pages` and `GET /api/pages/{id}` with DTOs and strong page-specific ETags in `src/main/java/com/wikigerminare/pages/PageController.java`; parse creator UUID from `Principal.getName()` and pass it to Service without implementing auth.
- [ ] T011 [P] [US1] Add `PageExceptionHandler` in `src/main/java/com/wikigerminare/pages/PageExceptionHandler.java`; translate page/folder absence to `404` and request validation failures to `400`.

**Checkpoint**: US1 can create/retrieve a page independently and passes service, MVC and PostgreSQL tests.

## Phase 4: User Story 2 - List and Optimistically Update Pages (Priority: P1)

**Goal**: List all pages and update title/content only when the supplied strong ETag matches the page version read by the client.

**Independent Test**: List pages, PATCH with the current ETag and verify the new version/ETag; PATCH again with the prior ETag and verify `412`, current version in the response, and no overwrite.

### Tests for User Story 2

- [ ] T012 [US2] Extend `src/test/java/com/wikigerminare/pages/PageServiceTest.java` with list, partial title/content update, title maximum 255, empty content, content with no artificial limit, empty patch rejection, unchanged `folderId`, JPA-managed version increment and stale-version/no-mutation tests.
- [ ] T013 [US2] Extend `src/test/java/com/wikigerminare/pages/PageControllerTest.java` with list/PATCH contract tests for valid `If-Match`, missing header `428`, malformed/unsupported tag `400`, stale tag `412`, latest ETag/version, and `404`.
- [ ] T014 [P] [US2] Add a PostgreSQL concurrency test using two independent transactions that load the same `INTEGER` version and attempt updates; assert initial version `1`, at most one concurrent update commits, JPA increments without manual logic, and latest state/version are verified after rollback in `src/test/java/com/wikigerminare/pages/PageOptimisticLockIntegrationTest.java`.

### Implementation for User Story 2

- [ ] T015 [US2] Create a presence-aware `UpdatePageRequest` in `src/main/java/com/wikigerminare/pages/dto/UpdatePageRequest.java`; allow only title/content, require at least one field, preserve omitted-vs-explicit-null semantics, enforce title maximum 255, allow empty content with no artificial maximum, and never accept `folderId`, `slug` or version in the body.
- [ ] T016 [US2] Implement list and transactional title/content updates in `src/main/java/com/wikigerminare/pages/PageService.java`; compare expected version, throw a typed stale-version exception with expected/current versions, rely on `@Version Integer` and the positive check for provider-managed atomic increments, advance `updatedAt` only on successful changes, flush before returning, and do not change `folderId`.
- [ ] T017 [US2] Implement `GET /api/pages` and `PATCH /api/pages/{id}` in `src/main/java/com/wikigerminare/pages/PageController.java`; require one strong page-specific `If-Match`, emit current ETag/version on success, return `428` if missing, `400` if invalid, and `412` if stale.
- [ ] T018 [US2] Extend `src/main/java/com/wikigerminare/pages/PageExceptionHandler.java` and `src/main/java/com/wikigerminare/pages/PageService.java` to handle provider optimistic-lock failures after write-transaction rollback; fetch current version in a separate read-only transaction and return `412` with the current ETag/version.

**Checkpoint**: US2 passes stale-precondition and concurrent-write tests against real PostgreSQL, including a current-version response after rollback.

## Phase 5: User Story 3 - Delete Pages (Priority: P2)

**Goal**: Delete a page while respecting existing PostgreSQL FK actions and preserving referential integrity.

**Independent Test**: Delete a page with related images/comments/tags/source links and expect PostgreSQL CASCADE; target links become null; missing page returns `404`. The supplied relationships are not a required `409` case.

### Tests for User Story 3

- [ ] T019 [P] [US3] Add Service tests for delete success and missing page in `src/test/java/com/wikigerminare/pages/PageServiceTest.java`.
- [ ] T020 [P] [US3] Add MVC tests for `DELETE /api/pages/{id}`, asserting `204` for successful deletion and `404` for a missing page in `src/test/java/com/wikigerminare/pages/PageControllerTest.java`; do not require `409` for the supplied dependent relationships.
- [ ] T021 [P] [US3] Add PostgreSQL integration tests in `src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java` verifying page delete removes `page_images`, `comments`, `page_tags` and source links by CASCADE, sets target links' `target_page_id` to null, and deleting a folder sets page `folder_id` to null. Assert no application-owned cascade; only test `409` for a separately verified FK that actually rejects deletion.

### Implementation for User Story 3

- [ ] T022 [US3] Implement Page deletion in `src/main/java/com/wikigerminare/pages/PageService.java` and `src/main/java/com/wikigerminare/pages/PageController.java`; let PostgreSQL perform supplied CASCADE/SET NULL actions and add no application cascade. Retain generic SQLSTATE `23503` -> `409` only for a real FK violation; the supplied dependent relationships are not an expected conflict.

**Checkpoint**: US3 follows the deployed FK actions and passes Service, MVC and PostgreSQL tests.

## Phase 6: Polish and Cross-Cutting Validation

- [ ] T023 Verify Springdoc `/v3/api-docs` matches `specs/002-pages-api/contracts/pages-api.openapi.json` for CRUD routes, response fields, ETags and `400`/`404`/`412`/`428`/`409` statuses; record any contract adjustment in `specs/002-pages-api/quickstart.md`.
- [ ] T024 Run `./mvnw.cmd test` and all scenarios in `specs/002-pages-api/quickstart.md` against the provisioned existing PostgreSQL schema; record actual commands/results in `specs/002-pages-api/quickstart.md`.
- [ ] T025 Review `git diff` against `specs/002-pages-api/spec.md` and `plan.md`; confirm implementation changes are confined to `pom.xml` only if a validated dependency becomes necessary, `src/main/java/com/wikigerminare/pages/`, and `src/test/java/com/wikigerminare/pages/`, with no DDL/migrations or out-of-scope API edits.

## Dependencies and Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 and T002 are independent environment/documentation checks and can run in parallel.
- **Foundational (Phase 2)**: T003 follows T001 because entity mapping requires exact schema metadata; T004 follows T003.
- **US1 (Phase 3)**: Begins after T004. T005-T007 are independent tests and can be authored in parallel; implementation then proceeds T008 -> T009 -> T010, with T011 parallel to T010 after T009.
- **US2 (Phase 4)**: Begins after US1 checkpoint. T012 and T013 touch established test files and proceed serially with their corresponding US1 test additions; T014 can be authored independently. Then T015 -> T016 -> T017 -> T018.
- **US3 (Phase 5)**: Begins after US2 checkpoint. T019-T021 use separate test files/classes and can run in parallel; T022 follows.
- **Polish (Phase 6)**: T023-T025 begin after all desired stories; T024 requires the provisioned PostgreSQL environment.

### User Story Dependencies

- **US1 (P1)**: Depends only on verified schema mapping and the shared repository. This is the MVP.
- **US2 (P1)**: Builds on US1 response/Service/controller and uses the numeric version mapping.
- **US3 (P2)**: Uses the same page persistence and error mapping, so follows US1/US2 to avoid conflicting edits.

### Parallel Opportunities

- Setup: T001 with T002.
- US1 tests: T005, T006, T007.
- US2 concurrency integration test T014 can be authored while service/controller tests T012-T013 are prepared.
- US3 tests: T019, T020, T021.
- US1: T010 with T011 after T009.

## Parallel Examples

### User Story 1

```text
Task T005: PageServiceTest in src/test/java/com/wikigerminare/pages/PageServiceTest.java
Task T006: PageControllerTest in src/test/java/com/wikigerminare/pages/PageControllerTest.java
Task T007: PagePostgresIntegrationTest in src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java
```

### User Story 2

```text
Task T012: Service update tests in src/test/java/com/wikigerminare/pages/PageServiceTest.java
Task T013: MVC update tests in src/test/java/com/wikigerminare/pages/PageControllerTest.java
Task T014: PostgreSQL concurrency test in src/test/java/com/wikigerminare/pages/PageOptimisticLockIntegrationTest.java
```

### User Story 3

```text
Task T019: Delete service tests in src/test/java/com/wikigerminare/pages/PageServiceTest.java
Task T020: Delete MVC tests in src/test/java/com/wikigerminare/pages/PageControllerTest.java
Task T021: PostgreSQL delete test in src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java
```

## Implementation Strategy

### MVP First (US1)

1. Complete Setup and Foundational phases, including the read-only PostgreSQL schema inspection.
2. Author US1 tests and confirm expected failures, then implement DTOs, Service, Controller and exception mapping.
3. Validate create/get, principal propagation, Markdown round-trip, initial version and ETag independently.

### Incremental Delivery

1. Add listing and conditional updates; verify stale ETag and real concurrent race behavior without lost updates.
2. Add delete using only deployed FK behavior.
3. Run quickstart and full Maven suite against the existing PostgreSQL schema before review.

## Task Format Validation

All tasks use `- [ ] T###`, include `[P]` only for independent work, include story labels only in user-story phases, and name exact source/test/documentation paths. Automated tests are included to satisfy Constitution Principle IV.
