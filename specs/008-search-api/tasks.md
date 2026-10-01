# Tasks: search-api

**Input**: Design documents from `specs/008-search-api/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/search-api.openapi.json`, `quickstart.md`

**Tests**: Included because `spec.md` FR-015 explicitly requires automated tests for the search and folder-scope behaviors.

**Organization**: Tasks are grouped by the two P1 user stories in `spec.md`. US2 extends the endpoint delivered by US1, so it follows US1.

## Phase 1: Setup

**Purpose**: Project initialization and shared infrastructure.

No setup task is required: the project already has the Spring/JPA/PostgreSQL dependencies, V1 schema, GIN and folder indexes, Testcontainers PostgreSQL support, and OpenAPI contract. This feature must not add a dependency or migration.

## Phase 2: Foundational

**Purpose**: Blocking prerequisites shared by the stories.

No separate foundational task is required. The shared query-validation error handling is introduced with US1 and reused by US2; no new global middleware or infrastructure is needed.

## Phase 3: User Story 1 - Global page search (Priority: P1) 🎯 MVP

**Goal**: Search all pages by title and content, including unfiled pages, and return results by relevance with deterministic ties.

**Independent Test**: Search PostgreSQL fixtures with title-only and content-only matches, different relevance scores, equal scores, and no matches. Verify the endpoint returns the expected PageResponse list, empty arrays, and 400 for missing or blank `q`.

### Tests for User Story 1

- [X] T001 [P] [US1] Add MockMvc tests for `GET /api/search` success, PageResponse list shape, missing `q`, blank `q`, and `{ "error": ... }` 400 responses in `src/test/java/com/wikigerminare/search/SearchControllerTest.java`.
- [X] T002 [P] [US1] Add SearchService tests that reject null, empty, and whitespace-only `q`, and verify valid query results map to the existing PageResponse fields in `src/test/java/com/wikigerminare/search/SearchServiceTest.java`.
- [X] T003 [P] [US1] Add PostgreSQL integration tests for title matching, content matching, relevance-descending ordering, UUID-ascending tie ordering, and empty results in `src/test/java/com/wikigerminare/search/SearchPostgresIntegrationTest.java`.

### Implementation for User Story 1

- [X] T004 [P] [US1] Add `SearchValidationException` and search-scoped `SearchExceptionHandler` that map search validation and missing-parameter errors to HTTP 400 with the established `{ "error": "..." }` body in `src/main/java/com/wikigerminare/search/SearchValidationException.java` and `src/main/java/com/wikigerminare/search/SearchExceptionHandler.java`.
- [X] T005 [P] [US1] Create the global PostgreSQL full-text query in `SearchRepository`, matching with `to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(content, ''))` and `plainto_tsquery('english', :q)`, ordering by `ts_rank` descending and page UUID ascending, in `src/main/java/com/wikigerminare/search/SearchRepository.java`.
- [X] T006 [US1] Create `SearchService` to reject missing/blank `q` with `SearchValidationException`, call the global repository query, map results to `PageResponse`, and run reads as read-only transactions in `src/main/java/com/wikigerminare/search/SearchService.java` (depends on T004-T005).
- [X] T007 [US1] Create `SearchController` with required query parameter `q` at `GET /api/search`, delegate to `SearchService`, and return HTTP 200 with the ordered list in `src/main/java/com/wikigerminare/search/SearchController.java`.

**Checkpoint**: US1 works without folder filters; global searches include pages with nullable `folder_id`, and invalid queries return the documented 400 response.

## Phase 4: User Story 2 - Folder-scoped page search (Priority: P1)

**Goal**: Limit matching pages to a selected folder, optionally including every descendant, while rejecting nonexistent folders.

**Independent Test**: With the US1 endpoint and fixtures, search an existing folder using false/default and true descendant modes. Verify direct pages, nested descendants, unrelated folders, global unfiled pages, malformed values, and a nonexistent folder all follow the contract.

### Tests for User Story 2

- [X] T008 [P] [US2] Add MockMvc tests for omitted/default `includeSubfolders=false`, explicit true, malformed `folderId`, invalid boolean, and nonexistent-folder 404 handling in `src/test/java/com/wikigerminare/search/SearchControllerTest.java`.
- [X] T009 [P] [US2] Add SearchService tests that validate an existing `folderId`, throw the existing `FolderNotFoundException` for an unknown UUID, and ignore `includeSubfolders` when `folderId` is absent in `src/test/java/com/wikigerminare/search/SearchServiceTest.java`.
- [X] T010 [US2] Extend the PostgreSQL integration fixture and tests for direct folder filtering with `includeSubfolders=false` and its omitted default, recursive selected-folder plus nested-descendant results with `includeSubfolders=true`, unrelated-folder exclusion, and unfiled-page inclusion in an unscoped search even when true is supplied in `src/test/java/com/wikigerminare/search/SearchPostgresIntegrationTest.java`.

### Implementation for User Story 2

- [X] T011 [US2] Extend `SearchRepository` with direct-folder filtering and a recursive CTE seeded by the selected folder and traversing `folders.parent_folder_id`; use `UNION` for distinct visited UUIDs and apply the page folder filter in `src/main/java/com/wikigerminare/search/SearchRepository.java`.
- [X] T012 [US2] Extend `SearchService` to validate a supplied folder using `FolderRepository.findById`, throw `FolderNotFoundException` when absent, and pass folder scope/descendant mode to the repository while leaving global scope unfiltered in `src/main/java/com/wikigerminare/search/SearchService.java`.
- [X] T013 [US2] Extend `SearchController` with optional UUID `folderId` and boolean `includeSubfolders` defaulting to false, and delegate both values to `SearchService` in `src/main/java/com/wikigerminare/search/SearchController.java`.

**Checkpoint**: US2 returns direct or recursive scoped results as specified; folder-not-found remains distinct from an existing folder with no matches.

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verify the completed endpoint and its PostgreSQL-specific behavior against the contract.

- [X] T014 Run the feature tests and full regression suite using the commands in `specs/008-search-api/quickstart.md`, then reconcile any contract discrepancy in `specs/008-search-api/contracts/search-api.openapi.json` and document the unchanged English-configuration limitation in `specs/008-search-api/quickstart.md`.

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No setup work is required; existing dependencies, schema and test infrastructure are reused.
- **Foundational (Phase 2)**: No separate foundation task; no story is blocked on new infrastructure.
- **US1 (Phase 3)**: First deliverable and MVP; independent after the existing application setup.
- **US2 (Phase 4)**: Depends on US1's route, service and global query, then adds folder validation and scope.
- **Polish (Phase 5)**: Depends on US1 and US2 implementation and their feature tests.

### User Story Dependencies

- **US1 (P1)**: No dependency on another feature story; it delivers a complete global-search increment.
- **US2 (P1)**: Extends US1's endpoint and repository/service flow with the optional folder parameters; its folder scenarios require US1's global search implementation.

### Within Each User Story

- Write MVC, service, and PostgreSQL behavior tests before implementing the corresponding story.
- US1 repository query precedes service orchestration, which precedes controller routing.
- US2 extends the repository scope query before the service passes validated folder inputs; controller binding follows those service parameters.
- Keep PostgreSQL full-text and recursive-CTE assertions on PostgreSQL/Testcontainers; do not replace those checks with H2.

## Parallel Opportunities

- **US1 tests**: T001, T002, and T003 edit different test files and can be prepared in parallel.
- **US1 implementation**: T004 and T005 edit separate production files and can proceed in parallel after the tests are in place; T006 follows both T004 and T005, and T007 follows T006.
- **US2 tests**: T008 and T009 edit separate files and can proceed in parallel; T010 edits the same integration test file as T003 and therefore follows it.
- **US2 implementation**: T011 precedes T012, which precedes T013 because the repository method and service signature must exist before controller binding is connected.

## Implementation Strategy

### MVP First (US1)

1. Complete T001-T003 and confirm the new global-search tests fail for the missing endpoint/query.
2. Complete T004-T007.
3. Validate US1 independently: title/content matches, relevance and tie order, empty result, and invalid/missing `q`.
4. Stop for review or deliver the global-search MVP before adding folder scope.

### Incremental Delivery

1. Deliver US1 global search.
2. Add US2 direct-folder and recursive-folder scope, with folder existence and binding errors.
3. Complete T014 and verify the whole API contract and regression suite.

## Notes

- `[P]` marks tasks that can proceed in parallel without editing the same file or depending on unfinished work.
- Every task is unchecked and has a sequential ID, required user-story label where applicable, and concrete repository file path(s).
- No entity, migration, DDL, dependency, PageController change, or new search engine is planned.
- The full-text expression/configuration remains `english`; Portuguese morphology limitations remain documented and unchanged.
