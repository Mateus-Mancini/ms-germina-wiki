# Tasks: folders-api

**Input**: Design documents in `specs/001-folders-api/`

**Owner**: Camilla

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/folders-api.openapi.json`, `quickstart.md`

**Tests**: Included because Constitution Principle IV requires automated tests. Write story tests before that story's implementation and confirm the expected failures first.

**Organization**: Tasks are grouped by the three user stories in `spec.md`. Every task names the affected path; no database schema or out-of-scope API work is included.

## Phase 1: Setup

**Purpose**: Satisfy the required validation dependency and verify external database prerequisites.

- [x] T001 [P] Add `spring-boot-starter-validation` to `pom.xml` so Jakarta Bean Validation constraints have a provider.
- [ ] T002 [P] Verify the deployed `folders` and `users` column types, identifier/timestamp defaults, FK actions, and a valid test creator UUID; record verified details in `specs/001-folders-api/data-model.md` without adding or changing DDL.

## Phase 2: Foundational

**Purpose**: Map the existing table and provide persistence required by every story.

- [ ] T003 Map `Folder` in `src/main/java/com/wikigerminare/folders/Folder.java` to the verified existing schema: `id` is UUID primary key; `name` is required VARCHAR(150); `parent_folder_id` is a nullable FK to `folders.id`; `created_by` is a required UUID FK to `users`; `created_at` and `updated_at` are required TIMESTAMPTZ. Use no JPA delete cascade or orphan removal.
- [ ] T004 Add the basic Spring Data CRUD repository for `Folder` in `src/main/java/com/wikigerminare/folders/FolderRepository.java`; do not add schema-generation or migration code.

**Checkpoint**: The mapped entity and repository validate against the pre-existing PostgreSQL schema; the current branch is already `001-folders-api`.

## Phase 3: User Story 1 - Create and Consult Folders (Priority: P1, MVP)

**Goal**: Create a root or child folder and retrieve it by UUID.

**Independent Test**: With an existing authenticated principal and PostgreSQL schema, create a root and a child folder, retrieve both by ID, and verify the parent reference and creator.

### Tests for User Story 1

- [ ] T005 [P] [US1] Add `FolderServiceTest` cases for creating root/child folders, resolving `createdBy` from the supplied principal UUID, rejecting a missing parent, and returning not-found for an absent folder in `src/test/java/com/wikigerminare/folders/FolderServiceTest.java`.
- [ ] T006 [P] [US1] Add MVC tests for `POST /api/folders` and `GET /api/folders/{id}`, including `201`, `200`, `400`, `404`, request validation and principal propagation, in `src/test/java/com/wikigerminare/folders/FolderControllerTest.java`.
- [ ] T007 [P] [US1] Add PostgreSQL-backed create/read mapping tests using a creator UUID that exists in the test `users` table in `src/test/java/com/wikigerminare/folders/FolderPostgresIntegrationTest.java`.

### Implementation for User Story 1

- [ ] T008 [US1] Create `CreateFolderRequest` and `FolderResponse` DTOs in `src/main/java/com/wikigerminare/folders/dto/CreateFolderRequest.java` and `src/main/java/com/wikigerminare/folders/dto/FolderResponse.java`, plus `FolderNotFoundException` in `src/main/java/com/wikigerminare/folders/FolderNotFoundException.java`; enforce `name` as required, non-blank, maximum 150 characters, and keep `createdBy` out of the request body.
- [ ] T009 [US1] Implement transactional create and get-by-ID operations in `src/main/java/com/wikigerminare/folders/FolderService.java`; obtain the creator UUID from the caller's authenticated principal, validate the optional parent exists, and populate UUID/timestamps consistently with the verified schema.
- [ ] T010 [P] [US1] Implement `POST /api/folders` and `GET /api/folders/{id}` using DTOs in `src/main/java/com/wikigerminare/folders/FolderController.java`; pass the principal UUID to the Service without implementing authentication.
- [ ] T011 [P] [US1] Add feature-local translation for request validation failures to `400` and `FolderNotFoundException` to `404` in `src/main/java/com/wikigerminare/folders/FolderExceptionHandler.java`.

**Checkpoint**: US1 passes its service, MVC and PostgreSQL tests without requiring US2 or US3.

## Phase 4: User Story 2 - List, Update, and Delete Folders (Priority: P1)

**Goal**: List folders, partially update name/parent, prevent self-parenting and cycles, and delete according to the configured FK actions.

**Independent Test**: List existing folders; rename and reparent one; verify omitted fields stay unchanged and explicit null makes it a root; reject self/cycle updates without changes; verify delete follows PostgreSQL FK behavior.

### Tests for User Story 2

- [ ] T012 [P] [US2] Add Service tests for list, name-only update, parent update, omitted versus explicit-null fields, empty update rejection, missing parent, self-parenting, ancestor-cycle rejection, and no mutation on conflict in `src/test/java/com/wikigerminare/folders/FolderServiceTest.java`.
- [ ] T013 [P] [US2] Add MVC tests for `GET /api/folders`, `PATCH /api/folders/{id}`, and `DELETE /api/folders/{id}`, covering `200`, `204`, `400`, `404`, and `409` responses in `src/test/java/com/wikigerminare/folders/FolderControllerTest.java`.
- [ ] T014 [P] [US2] Add PostgreSQL integration tests for actual FK delete behavior and concurrent reparent attempts that must not persist a cycle in `src/test/java/com/wikigerminare/folders/FolderHierarchyIntegrationTest.java`.

### Implementation for User Story 2

- [ ] T015 [P] [US2] Create a presence-aware `UpdateFolderRequest` in `src/main/java/com/wikigerminare/folders/dto/UpdateFolderRequest.java`: supplied `name` must be non-blank and at most 150 characters; omitted properties remain unchanged; omitted `parentFolderId` differs from explicit null; at least one property is required.
- [ ] T016 [P] [US2] Add a repository operation in `src/main/java/com/wikigerminare/folders/FolderRepository.java` that acquires the fixed PostgreSQL transaction-scoped advisory lock used by every parent-changing operation in this API.
- [ ] T017 [US2] Implement list, partial update, and delete in `src/main/java/com/wikigerminare/folders/FolderService.java` and add `FolderConflictException` in `src/main/java/com/wikigerminare/folders/FolderConflictException.java`; for parent changes, acquire the lock before reading ancestry, reject self/descendant cycles, track visited UUIDs, and change `updated_at` only on successful updates.
- [ ] T018 [US2] Implement `GET /api/folders`, `PATCH /api/folders/{id}`, and `DELETE /api/folders/{id}` in `src/main/java/com/wikigerminare/folders/FolderController.java`, preserving the specified status codes and DTO boundaries.
- [ ] T019 [US2] Extend `src/main/java/com/wikigerminare/folders/FolderExceptionHandler.java` to translate hierarchy conflicts and PostgreSQL FK SQLSTATE `23503` on delete to `409 Conflict`, without treating every integrity exception as an FK conflict or adding API-owned cascades.

**Checkpoint**: US2 passes its tests with the real database FK actions and does not create a cycle under concurrent API reparenting.

## Phase 5: User Story 3 - Consult the Folder Tree (Priority: P2)

**Goal**: Return the complete forest as nested DTO nodes without serializing JPA entities.

**Independent Test**: With roots, children and grandchildren present, verify every folder occurs exactly once beneath the correct parent and leaf `children` arrays are empty; an empty table returns an empty array.

### Tests for User Story 3

- [ ] T020 [P] [US3] Add Service tests for multiple roots, nested descendants, leaf nodes, empty result and one occurrence per folder in `src/test/java/com/wikigerminare/folders/FolderServiceTest.java`.
- [ ] T021 [P] [US3] Add MVC tests for `GET /api/folders/tree`, including the recursive `children` shape and empty-array response, in `src/test/java/com/wikigerminare/folders/FolderControllerTest.java`.
- [ ] T022 [P] [US3] Add PostgreSQL integration coverage confirming the flat folder projection assembles correct parent-child relationships in `src/test/java/com/wikigerminare/folders/FolderHierarchyIntegrationTest.java`.

### Implementation for User Story 3

- [ ] T023 [US3] Add one flat repository projection containing folder response fields and parent UUID in `src/main/java/com/wikigerminare/folders/FolderRepository.java`; avoid per-node parent/child fetches.
- [ ] T024 [US3] Add `FolderTreeNodeResponse` and assemble roots/children from the flat projection by UUID in `src/main/java/com/wikigerminare/folders/dto/FolderTreeNodeResponse.java` and `src/main/java/com/wikigerminare/folders/FolderService.java`; do not serialize JPA entities.
- [ ] T025 [US3] Expose `GET /api/folders/tree` returning root DTO nodes in `src/main/java/com/wikigerminare/folders/FolderController.java`.

**Checkpoint**: US3 returns the complete forest from one flat read and passes Service, MVC, and PostgreSQL integration tests.

## Phase 6: Polish and Cross-Cutting Validation

- [ ] T026 Run the full Maven test suite and each scenario in `specs/001-folders-api/quickstart.md` against the provisioned PostgreSQL schema; record actual commands and outcomes in `specs/001-folders-api/quickstart.md`.
- [ ] T027 Review the final change set against `specs/001-folders-api/plan.md` and `specs/001-folders-api/spec.md`; confirm `pom.xml`, the `folders` package and its tests are the only implementation paths changed, with no DDL/migration or out-of-scope API changes.

## Dependencies and Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 and T002 are independent and can run in parallel.
- **Foundational (Phase 2)**: T003 follows T002 because entity mapping must use verified schema metadata; T004 follows T003.
- **US1 (Phase 3)**: Begins after T004. T005-T007 can be authored in parallel; implementation follows the tests.
- **US2 (Phase 4)**: Begins after the US1 checkpoint. T012-T014 can be authored in parallel; T015-T016 can run in parallel after tests; T017 follows both; T018-T019 follow T017.
- **US3 (Phase 5)**: Begins after the US2 checkpoint because it extends the same Service, Repository and Controller files. T020-T022 can be authored in parallel; T023 follows those tests, then T024 and T025.
- **Polish (Phase 6)**: Begins after all desired stories are complete.

### User Story Dependencies

- **US1 (P1)**: Depends only on the shared schema mapping and repository. This is the MVP.
- **US2 (P1)**: Builds on the US1 Service/Controller/exception handling and introduces mutation locking.
- **US3 (P2)**: Uses the same folder persistence/API layers and follows US2 to avoid concurrent edits to shared source files.

### Parallel Opportunities

- Setup: T001 with T002.
- US1 tests: T005, T006, T007.
- US2 tests: T012, T013, T014; then T015 with T016.
- US3 tests: T020, T021, T022.
- US1 endpoint and exception handler: T010 with T011 after T009.

## Parallel Examples

### User Story 1

```text
Task T005: FolderServiceTest in src/test/java/com/wikigerminare/folders/FolderServiceTest.java
Task T006: FolderControllerTest in src/test/java/com/wikigerminare/folders/FolderControllerTest.java
Task T007: FolderPostgresIntegrationTest in src/test/java/com/wikigerminare/folders/FolderPostgresIntegrationTest.java
```

### User Story 2

```text
Task T012: Service maintenance tests in src/test/java/com/wikigerminare/folders/FolderServiceTest.java
Task T013: MVC maintenance tests in src/test/java/com/wikigerminare/folders/FolderControllerTest.java
Task T014: PostgreSQL hierarchy tests in src/test/java/com/wikigerminare/folders/FolderHierarchyIntegrationTest.java
```

### User Story 3

```text
Task T020: Tree service tests in src/test/java/com/wikigerminare/folders/FolderServiceTest.java
Task T021: Tree endpoint tests in src/test/java/com/wikigerminare/folders/FolderControllerTest.java
Task T022: Tree PostgreSQL tests in src/test/java/com/wikigerminare/folders/FolderHierarchyIntegrationTest.java
```

## Implementation Strategy

### MVP First (US1)

1. Complete Setup and Foundational phases, including PostgreSQL schema/test-fixture verification.
2. Write and run US1 tests; then implement DTOs, Service, Controller and exception translation.
3. Validate US1 independently with the existing schema and an authenticated test principal.

### Incremental Delivery

1. Add US2 maintenance and cycle-prevention behavior; re-run US1 tests plus US2 tests.
2. Add US3 full-tree response; run tree tests and all prior story tests.
3. Run the quickstart and complete Maven suite against PostgreSQL before review.

## Task Format Validation

All tasks use `- [ ] T###`, include `[P]` only for independent work, include `[US1]`/`[US2]`/`[US3]` only in story phases, and name the exact source, test, or documentation path. Tests are included to satisfy Constitution Principle IV.
