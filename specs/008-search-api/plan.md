# Implementation Plan: search-api

**Branch**: `008-search-api` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: Approved feature specification in `specs/008-search-api/spec.md`

## Summary

Add `GET /api/search` in the dedicated `com.wikigerminare.search` package. Keep HTTP handling in a SearchController, parameter validation and existing-folder validation in a SearchService, and PostgreSQL search/scope retrieval in a SearchRepository. Match title and Markdown content with the expression/configuration already used by `idx_pages_full_text_search`, rank by PostgreSQL full-text relevance, and break ties by page UUID ascending. For recursive scope, traverse `folders.parent_folder_id`, including the requested folder. Reuse the existing PageResponse fields. No new dependency or migration is expected because the current schema already contains the compatible GIN index and folder/page indexes.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL driver, and Springdoc OpenAPI. No new dependency planned.

**Storage**: Existing PostgreSQL `pages` and `folders` tables. Reuse `idx_pages_full_text_search`, `idx_folders_parent`, and `idx_pages_folder` from V1. No DDL or migration planned.

**Testing**: Existing JUnit 5, Spring Boot MVC/JPA test support, Mockito, AssertJ, and Testcontainers PostgreSQL. Use controller and service tests plus a PostgreSQL integration test; no H2 substitute for PostgreSQL full-text or recursive-query behavior.

**Target Platform**: JVM 21 REST service, deployable through the existing Spring Boot/AWS Lambda packaging.

**Project Type**: Single Spring Boot REST backend.

**Performance Goals**: No numeric latency, corpus size, or throughput target is specified. Preserve use of the existing GIN index for full-text matching and existing B-tree folder indexes for scope traversal. Do not add pagination in this version.

**Constraints**: Required nonblank `q`; optional UUID `folderId`; `includeSubfolders=false` by default; missing folder returns 404; bad parameter conversion returns 400. Global searches include pages with null `folder_id`. Folder search includes only the selected folder unless descendant inclusion is requested. Relevance descending and UUID ascending tie-break. Dedicated Controller → Service → Repository layering and package. Reuse PageResponse fields. English text search remains unchanged and may be less effective for Portuguese morphology. No search engine, new dependency, schema change, or migration.

**Scale/Scope**: One read-only GET endpoint and two P1 journeys (global search and folder-scoped search); no new persisted entity or lifecycle behavior.

## Constitution Check

*Initial gate before research: PASS. Post-design gate recheck: PASS.*

- **Specification as source of truth**: PASS. Query, folder, ranking, error and response behaviors are fixed by the approved specification; no unresolved clarification markers remain.
- **Layered architecture**: PASS. SearchController handles HTTP; SearchService validates the query and folder existence and coordinates the use case; SearchRepository owns database querying. Search behavior stays out of PageController.
- **REST contracts and DTOs**: PASS. OpenAPI specifies the GET parameters, list response using existing PageResponse shape, and 400/404 outcomes.
- **Automated tests**: PASS at design level. Planned MVC, service, and PostgreSQL integration tests cover requested scenarios and regressions.
- **Simplicity and scope**: PASS. Reuse existing database index, tables, PageResponse, FolderRepository and error conventions. No new dependency, migration, auth feature or page API modification.
- **Schema compatibility**: PASS based on checked-in V1. The GIN index expression matches the planned vector expression. `folders.parent_folder_id` and nullable `pages.folder_id` support recursive and direct scope. No migration is warranted.
- **Portuguese content consideration**: PASS. Record current English dictionary limitations; changing the dictionary or index is explicitly out of scope.

## Design Decisions

1. **Use the indexed full-text vector as written in V1.** Apply `to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(content, ''))` for matching and ranking so the query expression aligns with `idx_pages_full_text_search`. Parse ordinary user text with `plainto_tsquery('english', :q)`, avoiding a requirement for raw PostgreSQL query syntax. Use `ts_rank` descending and `id` ascending for deterministic results.
2. **Use a recursive PostgreSQL query for descendant scope.** Seed the hierarchy with the requested folder, then join child folders by `child.parent_folder_id = current.id`. Use `UNION` to deduplicate IDs and prevent unbounded traversal if historical or externally written data contains a cycle. Direct mode uses exact `pages.folder_id`; global mode omits the folder filter, retaining NULL-folder pages.
3. **Validate folder existence in the service before search.** Reuse `FolderRepository.findById` and `FolderNotFoundException`, preserving the existing 404 convention and distinguishing an unknown folder from a known folder with zero matching pages.
4. **Keep the search contract separate from page CRUD.** Introduce a dedicated search package, return the established PageResponse fields, and document the new public route in its own OpenAPI contract. No changes to PageController or PageResponse are anticipated.

## Project Structure

### Documentation (this feature)

```text
specs/008-search-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── search-api.openapi.json
```

`tasks.md` is intentionally left for the separate Spec Kit tasks phase.

### Source Code (repository root)

```text
src/main/java/com/wikigerminare/
├── pages/
│   ├── Page.java                         # Existing; read-only reuse
│   ├── PageRepository.java               # Existing; unchanged
│   └── dto/PageResponse.java             # Existing response shape; unchanged
├── folders/
│   ├── Folder.java                       # Existing hierarchy mapping; unchanged
│   ├── FolderRepository.java             # Existing folder lookup; reuse
│   └── FolderNotFoundException.java      # Existing 404 exception; reuse
└── search/                               # New feature boundary
    ├── SearchController.java
    ├── SearchService.java
    ├── SearchRepository.java
    ├── SearchValidationException.java
    └── SearchExceptionHandler.java       # Maps search validation and parameter conversion to {error: ...}

src/test/java/com/wikigerminare/search/
├── SearchControllerTest.java
├── SearchServiceTest.java
└── SearchPostgresIntegrationTest.java
```

**Structure Decision**: Keep the single Maven backend. Search owns a dedicated controller/service/repository boundary as required; existing Page and Folder persistence classes are reused without expanding their API responsibilities. A search-scoped exception advice maps blank/missing query and parameter conversion errors to the established `{ "error": "..." }` shape. Reuse `FolderNotFoundException` and its existing global 404 mapping; do not add a competing folder exception mapping.

## Implementation Sequence

1. Add MVC contract tests for required/blank `q`, malformed UUID/boolean conversion, default `includeSubfolders=false`, and the success list shape/status.
2. Add service tests for blank-query rejection, folder existence validation, delegation of global/direct/recursive options, and mapping to existing page response fields.
3. Add PostgreSQL integration fixtures with valid users, folders and pages. Assert title matching, content matching, relevance ordering, stable tie order, exact-folder filtering, recursive descendants, unrelated-folder exclusion, unfiled global results, and empty results. Exercise folder-not-found through the endpoint/service layer.
4. Add the dedicated controller/service/repository implementation, a search validation exception, and scoped advice for consistent 400 responses. Keep all folder/query business rules in Service and database-specific SQL in Repository.
5. Verify the existing full-text index remains usable with the matching expression and run the feature tests plus the existing regression suite. Do not modify V1 or add a migration.

## Risks and Boundaries

- PostgreSQL's `english` text-search configuration is not tailored to Portuguese stemming, so Portuguese word variants may match less intuitively. The feature documents this and does not change the current configuration.
- Recursive folder traversal relies on `folders-api` to prevent cycles during normal reparenting. `UNION` also prevents repeated IDs from causing recursion loops if unexpected cycles exist.
- `q` terms are interpreted as ordinary text by the selected parser; users do not supply raw tsquery operators. This is consistent with the approved assumption.
- No explicit search latency/corpus target was supplied. Index reuse is planned, but no numeric SLA is claimed.
- Search returns full PageResponse fields, including Markdown content, as specified; no result snippet or separate ranking field is added.

## Complexity Tracking

No Constitution violations require justification.
