# Tasks: pages-api

**Input**: Design documents in `/specs/003-pages-api/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/pages-api.openapi.json`

**Tests**: Included because the project constitution requires automated tests for new behavior. Write the tests before the corresponding implementation and confirm they fail for the expected reason.

**Organization**: Tasks are grouped by user story. Shared persistence prerequisites are in the Foundational phase; later stories extend the feature-local service, controller, and exception handling.

## Phase 1: Setup

**Purpose**: Confirm the external database prerequisite without changing database objects.

- [ ] T001 Verify the provisioned PostgreSQL `pages`, `folders`, and `users` schema, version column/default, and stated FK delete actions against `specs/003-pages-api/data-model.md`; record any discrepancy in `specs/003-pages-api/research.md`. Do not run DDL or migrations.

---

## Phase 2: Foundational

**Purpose**: Add the persistence boundary required by all page operations. No user-story implementation can start before schema verification and this mapping are complete.

- [ ] T002 Map the existing `pages` table in `src/main/java/com/wikigerminare/pages/Page.java`, including all specified columns, UUID/`Instant` fields, nullable `folderId`/`updatedBy`, and `Integer @Version`; preserve the database schema and do not increment the version manually.
- [ ] T003 Add `src/main/java/com/wikigerminare/pages/PageRepository.java` for page persistence and lookup, reusing the existing folder repository where needed without changing `folders-api`.

**Checkpoint**: The schema has been verified and the entity/repository can represent persisted pages without generating or changing database objects.

---

## Phase 3: User Story 1 - Criar e consultar páginas (Priority: P1) - MVP

**Goal**: Create a page in an existing folder and retrieve it by UUID, preserving Markdown, client-provided slug, creator, timestamps, and initial version.

**Independent Test**: With an existing folder and authenticated principal, create a page and retrieve it by ID; verify all response fields, raw Markdown, unchanged slug, and version `1`. Also verify missing folder/page and duplicate slug errors without partial persistence.

### Tests for User Story 1

- [ ] T004 [P] [US1] Add service tests for page creation, folder existence, missing page, required title/content, and raw Markdown behavior in `src/test/java/com/wikigerminare/pages/PageServiceTest.java`.
- [ ] T005 [P] [US1] Add MVC tests for create/get status codes, request/response DTOs, principal-derived `createdBy`, and ETag headers in `src/test/java/com/wikigerminare/pages/PageControllerTest.java`.
- [ ] T006 [P] [US1] Add PostgreSQL integration tests for existing-schema mapping, version seed `1`, raw Markdown round-trip, unknown-folder rejection, and duplicate-slug constraint handling in `src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java`.

### Implementation for User Story 1

- [ ] T007 [P] [US1] Create `src/main/java/com/wikigerminare/pages/dto/CreatePageRequest.java` with required nonblank title (maximum 255), required client-supplied slug (1-300 characters), required content (empty string allowed), and required folder UUID validation.
- [ ] T008 [P] [US1] Create `src/main/java/com/wikigerminare/pages/dto/PageResponse.java` exposing ID, title, unchanged slug, raw content, nullable folderId and updatedBy, createdBy, timestamps, and numeric version.
- [ ] T009 [US1] Implement create and get-by-ID operations in `src/main/java/com/wikigerminare/pages/PageService.java`; validate the folder through `FolderRepository`, derive no slug, preserve content exactly, and let the existing UNIQUE constraint reject duplicate slugs.
- [ ] T010 [US1] Implement `POST /api/pages` and `GET /api/pages/{id}` in `src/main/java/com/wikigerminare/pages/PageController.java`; read creator UUID from `Principal.getName()` and emit strong page-specific ETags on create and individual read.
- [ ] T011 [US1] Add feature-local validation, not-found, and duplicate-slug response mapping in `src/main/java/com/wikigerminare/pages/PageExceptionHandler.java`, returning the contract statuses without exposing persistence internals.

**Checkpoint**: Create and read work independently, including the specified not-found, validation, and slug-conflict behavior.

---

## Phase 4: User Story 2 - Listar e atualizar páginas (Priority: P1)

**Goal**: List all pages and update title and/or Markdown only when the caller's strong `If-Match` ETag represents the current version.

**Independent Test**: List existing pages (and an empty collection), then submit two updates with the same ETag; at most one succeeds, and a stale update returns `412` plus the current version/ETag without overwriting the accepted change. Missing and malformed preconditions return `428` and `400` respectively.

### Tests for User Story 2

- [ ] T012 [P] [US2] Add service tests for empty/nonempty listing, editable fields, version increments, stale versions, and validation without partial updates in `src/test/java/com/wikigerminare/pages/PageServiceTest.java`.
- [ ] T013 [P] [US2] Add MVC tests for list responses and PATCH ETag success, missing/malformed/stale `If-Match`, forbidden `slug`/`folderId`, and current-version error details in `src/test/java/com/wikigerminare/pages/PageControllerTest.java`.
- [ ] T014 [P] [US2] Add PostgreSQL tests for `@Version` increments and competing updates in independent transactions using the same ETag in `src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java`.

### Implementation for User Story 2

- [ ] T015 [P] [US2] Create `src/main/java/com/wikigerminare/pages/dto/UpdatePageRequest.java` allowing one or both of title/content while excluding slug, folderId, version, and empty updates.
- [ ] T016 [US2] Extend `src/main/java/com/wikigerminare/pages/PageService.java` with list and transactional update operations; compare the expected version and flush before responding, rely on JPA `@Version` for atomic concurrency, and do not write `updatedBy` without a specified rule.
- [ ] T017 [US2] Extend `src/main/java/com/wikigerminare/pages/PageController.java` with `GET /api/pages` and `PATCH /api/pages/{id}`; require exactly one strong page-specific `If-Match`, reject missing with `428` and malformed/weak/list/wildcard values with `400`, and return the new ETag after success.
- [ ] T018 [US2] Handle optimistic-lock races after transaction rollback in `src/main/java/com/wikigerminare/pages/PageExceptionHandler.java`; read the latest version in a new transaction and return `412` with the current ETag and version.

**Checkpoint**: List and conditional update satisfy the API contract, including atomic stale-write rejection.

---

## Phase 5: User Story 3 - Excluir páginas (Priority: P2)

**Goal**: Delete a page while relying on PostgreSQL's existing CASCADE and SET NULL foreign-key actions.

**Independent Test**: Delete an existing page with the specified related records and confirm `204`; verify source references cascade, destination links become null, and an unknown page returns `404`. Return `409` only when PostgreSQL reports an actual blocking FK.

### Tests for User Story 3

- [ ] T019 [P] [US3] Add service tests for successful deletion and missing page in `src/test/java/com/wikigerminare/pages/PageServiceTest.java`.
- [ ] T020 [P] [US3] Add MVC tests for `DELETE /api/pages/{id}` success, missing page, and FK-conflict status mapping in `src/test/java/com/wikigerminare/pages/PageControllerTest.java`.
- [ ] T021 [P] [US3] Add PostgreSQL integration tests confirming configured CASCADE/SET NULL behavior and that only a real blocking FK produces conflict in `src/test/java/com/wikigerminare/pages/PagePostgresIntegrationTest.java`.

### Implementation for User Story 3

- [ ] T022 [US3] Implement page deletion in `src/main/java/com/wikigerminare/pages/PageService.java` without application-side cascades or reassignment of related rows.
- [ ] T023 [US3] Add `DELETE /api/pages/{id}` returning `204 No Content` or page `404` in `src/main/java/com/wikigerminare/pages/PageController.java`.
- [ ] T024 [US3] Map only an actual PostgreSQL integrity violation to `409 Conflict` in `src/main/java/com/wikigerminare/pages/PageExceptionHandler.java`; do not treat the specified CASCADE/SET NULL references as conflicts.

**Checkpoint**: Page deletion delegates referential actions to the existing database schema and returns contract statuses.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run the documented verification and close remaining contract gaps.

- [ ] T025 Review `specs/003-pages-api/contracts/pages-api.openapi.json` against the implemented DTOs, headers, status codes, and nullability; update the contract only if implementation discrepancies reveal a documentation mismatch with `specs/003-pages-api/spec.md`.
- [ ] T026 Run `./mvnw.cmd test` from the repository root and execute the applicable scenarios in `specs/003-pages-api/quickstart.md`; record any PostgreSQL-only checks blocked by unavailable provisioned database access.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No source-code dependencies; schema verification must precede entity mapping.
- **Foundational (Phase 2)**: Depends on T001; T002 precedes T003 and blocks all user stories.
- **User Story 1 (Phase 3)**: Depends on T002-T003; delivers the MVP create/read flow.
- **User Story 2 (Phase 4)**: Depends on the shared page model and the US1 service/controller/DTO foundations; extends the same feature-local files.
- **User Story 3 (Phase 5)**: Depends on the shared page model and the US1 service/controller foundations; extends the same feature-local files.
- **Polish (Phase 6)**: Depends on all desired stories being implemented.

### User Story Dependencies

- **US1 (P1)**: Starts after the Foundational phase; no dependency on another story.
- **US2 (P1)**: Builds on the page representation and service/controller established in US1.
- **US3 (P2)**: Builds on the page lookup and service/controller established in US1; it is otherwise independent of US2 behavior.

### Parallel Opportunities

- T004-T006 can be developed in parallel after the foundational entity/repository exist; they touch separate test files.
- T007 and T008 are independent DTO files; T012-T014 and T019-T021 each touch separate test files and can be parallelized within their phase.
- T002 and T003, each story's service/controller work, and extension-handler work are dependency-ordered where noted and should not be started before their prerequisites.
- US2 and US3 implementation are not parallelized in this sequence because they extend the same `PageService`, `PageController`, and `PageExceptionHandler` files.

## Parallel Examples

### User Story 1

```text
After T002-T003:
T004 PageServiceTest.java
T005 PageControllerTest.java
T006 PagePostgresIntegrationTest.java

After tests are in place:
T007 CreatePageRequest.java
T008 PageResponse.java
```

### User Story 2

```text
In parallel, after US1 is complete:
T012 PageServiceTest.java
T013 PageControllerTest.java
T014 PagePostgresIntegrationTest.java
```

### User Story 3

```text
In parallel, after US1 is complete:
T019 PageServiceTest.java
T020 PageControllerTest.java
T021 PagePostgresIntegrationTest.java
```

## Implementation Strategy

### MVP First

1. Verify the existing PostgreSQL schema and complete the foundational entity/repository work.
2. Implement US1 and validate create/read independently; this is the MVP.
3. Add US2 conditional listing and updates, then validate the same-version race against PostgreSQL.
4. Add US3 deletion and verify the database's configured FK actions.
5. Run the documented Maven tests and quickstart scenarios.

### Incremental Delivery

Deliver each story as a testable increment. Keep database schema changes, migrations, authentication, other APIs, pagination, filtering, and application-side cascade logic out of scope.
