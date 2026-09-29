# Tasks: wikilinks-backlinks

**Input**: Design documents in `specs/007-wikilinks-backlinks/`

**Owner**: Not specified

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/wikilinks-backlinks.openapi.json`, `quickstart.md`

**Tests**: Included because Constitution Principle IV requires automated tests. Author tests before implementing the corresponding story behavior.

**Organization**: Tasks are grouped by the three user stories in `spec.md`. Database work is limited to read-only introspection and use of existing `page_links`; do not add DDL, migrations, constraints, columns or replacement tables.

## Phase 1: Setup

**Purpose**: Verify schema and dependencies before mapping or implementation.

- [x] T001 Inspect the deployed `page_links` table read-only; record columns, primary key, unique constraints/indexes, existing duplicates and FK actions in `specs/007-wikilinks-backlinks/data-model.md`. Confirm whether one source-target row can be mapped/enforced without schema changes; stop and report incompatibility rather than adding DDL.
- [x] T002 Verify the `pages-api` content-write path is available for both create and PATCH before enabling full synchronization; current source has create but no update route. Record readiness and the existing PostgreSQL/auth prerequisites in `specs/007-wikilinks-backlinks/quickstart.md`; do not create a new page CRUD endpoint in this feature.

## Phase 2: Foundational

**Purpose**: Add persistence/query foundations and the narrow WikiLink parser.

- [x] T003 Map the existing relation in `src/main/java/com/wikigerminare/pages/wikilinks/PageLink.java` using only the real `page_links` columns and key verified by T001; map `source_page_id` and `target_page_id`, and do not invent an identifier, column or constraint. If no usable JPA entity key exists, stop and report before code rather than creating schema.
- [x] T004 Create the relation repository in `src/main/java/com/wikigerminare/pages/wikilinks/PageLinkRepository.java` for outgoing/incoming queries and source-link reconciliation, using the verified key/constraints and the existing page table.
- [x] T005 Create the focused scanner in `src/main/java/com/wikigerminare/pages/wikilinks/WikiLinkParser.java` for exact `[[slug]]` tokens outside inline/fenced code; return distinct exact slug strings and leave input content untouched.

**Checkpoint**: The real table mapping is verified; parser and repository foundations use no new schema or dependency.

## Phase 3: User Story 1 - Synchronize WikiLinks from Page Content (Priority: P1, MVP)

**Goal**: Maintain one directed relation per source-target pair whenever existing Page content is created or updated, and resolve pending slugs when a target page is created.

**Independent Test**: Save content with links to existing, missing, repeated and self slugs; assert raw Markdown is unchanged, one relation exists per resolved pair, missing targets remain unresolved, and creating a previously missing target activates matching links.

### Tests for User Story 1

- [x] T006 [P] [US1] Add scanner unit tests for exact `[[slug]]`, repeated tokens, malformed/unknown text, inline code and fenced code exclusion, and self-slug tokens in `src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkParserTest.java`.
- [x] T007 [P] [US1] Add Service tests for resolving existing slugs, leaving missing slugs unresolved, one logical edge for repeated occurrences, allowing source=target, removing a relation after its last occurrence is removed, and preserving content exactly in `src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkServiceTest.java`.
- [x] T008 [P] [US1] Add PostgreSQL integration tests for relation mapping, unique-pair behavior under the verified schema, transaction rollback with page write failure, and automatic resolution when a new target slug is created in `src/test/java/com/wikigerminare/pages/wikilinks/PageLinkPostgresIntegrationTest.java`.

### Implementation for User Story 1

- [x] T009 [US1] Implement the scanner in `src/main/java/com/wikigerminare/pages/wikilinks/WikiLinkParser.java`; ignore inline and fenced code, recognize only `[[slug]]`, perform no slug transformation, and never rewrite Markdown.
- [x] T010 [US1] Add exact `findBySlug`/page-content lookup support in `src/main/java/com/wikigerminare/pages/PageRepository.java` for target resolution and re-scanning existing source content; reuse existing Page columns only.
- [x] T011 [US1] Implement `WikiLinkService` in `src/main/java/com/wikigerminare/pages/wikilinks/WikiLinkService.java` to reconcile each source's distinct resolved targets transactionally: retain desired pairs, insert missing pairs, delete obsolete pairs, ignore unresolved slugs, allow self-links, and keep one logical pair per source-target even with repeated tokens.
- [x] T012 [US1] Integrate `WikiLinkService` into page creation/content-update transactions in `src/main/java/com/wikigerminare/pages/PageService.java`; after a target page is created, re-evaluate existing page contents for its slug and resolve pending references. Preserve the established Page response/content/ETag contracts and do not add page endpoints.
- [x] T013 [US1] If T001 finds no database uniqueness constraint for `(source_page_id, target_page_id)`, coordinate all feature-owned reconciliation/target-created scans using a transaction-scoped PostgreSQL lock in `src/main/java/com/wikigerminare/pages/wikilinks/PageLinkRepository.java`; use an independent lock key and add no DDL. If the schema cannot guarantee the logical pair under supported writers without a schema change, stop and document the incompatibility.

**Checkpoint**: Page create/content update and target creation synchronize relation state atomically; source Markdown is unchanged.

## Phase 4: User Story 2 - Query Outgoing WikiLinks (Priority: P1)

**Goal**: Return resolved target page summaries for an existing source page.

**Independent Test**: Query outgoing links for a page with multiple targets, repeats and self-link; each distinct target appears once. A page with none returns `[]`; a missing page returns `404`.

### Tests for User Story 2

- [x] T014 [P] [US2] Add Service/repository tests for outgoing target lookup, empty results, self-link and duplicate-row suppression in `src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkServiceTest.java`.
- [x] T015 [P] [US2] Add MVC tests for `GET /api/pages/{pageId}/wikilinks`, covering `200`, empty array, malformed UUID `400`, missing Page `404`, and one summary per target in `src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkControllerTest.java`.

### Implementation for User Story 2

- [x] T016 [US2] Create `LinkedPageSummary` in `src/main/java/com/wikigerminare/pages/wikilinks/dto/LinkedPageSummary.java` with existing Page fields `id`, `title`, and `slug` only.
- [x] T017 [US2] Add outgoing-link query behavior to `WikiLinkService` and expose `GET /api/pages/{pageId}/wikilinks` in `src/main/java/com/wikigerminare/pages/wikilinks/WikiLinkController.java`; verify the requested Page exists and return a distinct list of linked target summaries or `404`.

**Checkpoint**: Outgoing endpoint matches the OpenAPI contract and returns `200 []` for an existing page with no resolved links.

## Phase 5: User Story 3 - Query Backlinks (Priority: P1)

**Goal**: Return the distinct source pages that link to an existing target page.

**Independent Test**: Query backlinks for a target with multiple source pages and repeated occurrences; each source appears once. Empty result is `[]`; missing target is `404`.

### Tests for User Story 3

- [x] T018 [P] [US3] Add Service/repository tests for incoming-link lookup, empty results, self-link, distinct source pages, and target-null rows excluded from active backlinks in `src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkServiceTest.java`.
- [x] T019 [P] [US3] Add MVC tests for `GET /api/pages/{pageId}/backlinks`, covering `200`, empty array, malformed UUID `400`, missing Page `404`, and one summary per source in `src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkControllerTest.java`.
- [x] T020 [P] [US3] Add PostgreSQL integration coverage that source deletion cascades outgoing relations and target deletion nulls `target_page_id`, without application-side cascade, in `src/test/java/com/wikigerminare/pages/wikilinks/PageLinkPostgresIntegrationTest.java`.

### Implementation for User Story 3

- [ ] T021 [US3] Add incoming-link lookup to `WikiLinkService` and expose `GET /api/pages/{pageId}/backlinks` in `src/main/java/com/wikigerminare/pages/wikilinks/WikiLinkController.java`; verify Page existence and return distinct source summaries, excluding relations whose target was set null by PostgreSQL.

**Checkpoint**: Backlink endpoint returns the inverse of active directed links and conforms to the OpenAPI contract.

## Phase 6: Polish and Cross-Cutting Validation

- [ ] T022 Verify Springdoc `/v3/api-docs` matches `specs/007-wikilinks-backlinks/contracts/wikilinks-backlinks.openapi.json` and record validation commands/results in `specs/007-wikilinks-backlinks/quickstart.md`.
- [ ] T023 Run focused parser/Service/MVC tests and PostgreSQL integration tests from `specs/007-wikilinks-backlinks/quickstart.md`; record actual results and explicitly report any PostgreSQL schema or pages-api PATCH prerequisite that remains unavailable.
- [ ] T024 Review the final diff against `specs/007-wikilinks-backlinks/spec.md` and `plan.md`; confirm only `src/main/java/com/wikigerminare/pages/wikilinks/`, the required Page repository/service integration, matching tests, and the five feature docs changed. Confirm there is no DDL, migration, dependency addition, new Page API endpoint, or out-of-scope API edit.

## Dependencies and Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 and T002 are independent read-only environment checks.
- **Foundational (Phase 2)**: T003 follows T001; T004 follows the mapping decision; T005 can be written in parallel after the syntax is fixed.
- **US1 (Phase 3)**: Requires T002 content-write readiness and Phase 2 completion. T006-T008 can be authored in parallel; implementation follows T009 -> T010 -> T011 -> T012. T013 is required only if T001 confirms no existing pair uniqueness constraint.
- **US2 (Phase 4)**: Begins after US1 synchronization. T014 and T015 can be prepared in parallel; T016 precedes T017.
- **US3 (Phase 5)**: Begins after US1 relation storage. T018-T020 can be authored in parallel; T021 follows.
- **Polish (Phase 6)**: Begins after desired story checkpoints; PostgreSQL tasks require the actual existing schema.

### User Story Dependencies

- **US1 (P1)**: Foundational parser/relation storage plus existing Page content create/update lifecycle. MVP for relation synchronization.
- **US2 (P1)**: Depends on persisted directed relations from US1.
- **US3 (P1)**: Depends on persisted directed relations from US1; shares the summary DTO/controller.

### Parallel Opportunities

- Setup checks T001/T002.
- US1 tests T006/T007/T008 and parser tests T006 can run independently after agreeing on fixture prerequisites.
- US2 endpoint/service tests T014/T015.
- US3 endpoint/service/FK tests T018/T019/T020.

## Parallel Examples

### User Story 1

```text
Task T006: WikiLinkParserTest in src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkParserTest.java
Task T007: WikiLinkServiceTest in src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkServiceTest.java
Task T008: PageLinkPostgresIntegrationTest in src/test/java/com/wikigerminare/pages/wikilinks/PageLinkPostgresIntegrationTest.java
```

### User Story 2

```text
Task T014: Outgoing service tests in src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkServiceTest.java
Task T015: Outgoing controller tests in src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkControllerTest.java
```

### User Story 3

```text
Task T018: Backlink service tests in src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkServiceTest.java
Task T019: Backlink controller tests in src/test/java/com/wikigerminare/pages/wikilinks/WikiLinkControllerTest.java
Task T020: FK behavior test in src/test/java/com/wikigerminare/pages/wikilinks/PageLinkPostgresIntegrationTest.java
```

## Implementation Strategy

### MVP First (US1)

1. Complete read-only schema and pages-api lifecycle checks.
2. Add parser/synchronization tests and then implement relation synchronization within Page content-write transactions.
3. Validate raw Markdown preservation, one relation per target pair, broken-link activation on target creation and PostgreSQL FK behavior.

### Incremental Delivery

1. Deliver outgoing WikiLinks query.
2. Deliver backlinks query using inverse relation lookup.
3. Run all feature tests and quickstart against the existing PostgreSQL schema; do not apply DDL.

## Task Format Validation

All tasks use `- [ ] T###`, include `[P]` only for independent work, include story labels only in story phases, and name exact source/test/documentation paths. No task creates or changes database schema.
