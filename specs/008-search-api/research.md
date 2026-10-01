# Research: search-api

**Feature**: [spec.md](spec.md)
**Date**: 2026-09-30

## Repository findings

- The backend is a single Maven project using Java 21, Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL, and Springdoc OpenAPI. No new library is needed for the specified feature (`pom.xml`).
- V1 defines `idx_pages_full_text_search` as a GIN index over `to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(content, ''))` (`src/main/resources/db/migration/V1__initial_schema.sql`). It also indexes `folders(parent_folder_id)` and `pages(folder_id)`. This index directly supports matching only when the query uses a compatible vector expression and the same `english` configuration.
- `pages.folder_id` is nullable and references `folders.id` with `ON DELETE SET NULL`. `folders.parent_folder_id` is a nullable self-reference with `ON DELETE CASCADE` (`V1__initial_schema.sql`).
- `PageRepository` currently has no search query. `PageService` maps `Page` to `PageResponse` under read-only transaction patterns. `FolderRepository` supports `findById`; `FolderService` validates parent changes and prevents service-mediated hierarchy cycles.
- `PageExceptionHandler` returns `{ "error": "..." }` for mapped validation and not-found errors. It is currently a general `@RestControllerAdvice` and maps `FolderNotFoundException` to 404. `FolderExceptionHandler` is scoped to FolderController, so it will not handle the new search endpoint.
- Existing test styles include MVC slices (`PageControllerTest`), service unit tests (`PageServiceTest`), and PostgreSQL integration with Testcontainers and JDBC (`PageLinkPostgresIntegrationTest`). PostgreSQL-specific matching and recursive CTE behavior need PostgreSQL integration coverage, not H2.

## Decisions

### 1. Use the existing full-text index expression

**Decision**: Match with the existing `english` vector expression over title and content and a plain-text query parser, `plainto_tsquery('english', :q)`. Rank matching pages with `ts_rank` descending and order equal scores by `pages.id` ascending.

**Rationale**: The existing GIN expression index already matches the required searchable fields and language configuration. Keeping the same vector expression/configuration avoids DDL and gives the planner the opportunity to use the existing index. Plain-text parsing avoids requiring clients to send PostgreSQL query syntax. UUID ordering makes ties repeatable.

**Alternatives considered**: A different search engine, a new index/dictionary, or substring matching would either add infrastructure/schema or fail to reuse the current full-text index. Exposing raw `to_tsquery` syntax would add a client syntax and escaping contract not requested.

### 2. Expand folder scope with a recursive query

**Decision**: For direct scope filter `pages.folder_id = :folderId`. For descendant scope, seed a recursive CTE with the selected folder and follow child rows by `child.parent_folder_id = ancestor.id`; filter pages to the resulting folder IDs. Use `UNION` to deduplicate visited folder IDs. With no folder parameter, omit the folder predicate entirely.

**Rationale**: This follows the current adjacency-list relationship, includes the selected folder itself, and supports arbitrary hierarchy depth. Omitting the global folder predicate also preserves matching pages whose `folder_id` is null. The existing parent and page folder indexes support the traversal/filter operations.

**Alternatives considered**: Loading the full folder tree into Java would duplicate traversal and fetch unrelated rows; restricting to one descendant level would violate the feature requirement. `UNION ALL` without cycle protection is unnecessary because normal service-mediated moves prevent cycles and could recurse indefinitely on malformed historical data.

### 3. Validate folder existence before querying

**Decision**: When `folderId` is supplied, SearchService calls the existing `FolderRepository.findById` and throws `FolderNotFoundException` if absent. Existing advice can provide the 404 body. Do not treat a missing folder as an empty result.

**Rationale**: This follows the explicit contract and existing Page/Folder exception convention, while distinguishing nonexistent folders from existing folders without matching pages.

**Alternatives considered**: Returning an empty list for an invalid folder would hide a client error and contradict the approved specification.

### 4. Keep response and integration within existing conventions

**Decision**: SearchRepository returns matching Page entities in score order; SearchService maps them to the existing `PageResponse` fields. Add a dedicated search package and a search-scoped advice only if shared page advice cannot cleanly handle blank-query validation without duplicated exception handlers. Document the endpoint in a feature-local OpenAPI contract.

**Rationale**: This preserves the approved response shape and Controller → Service → Repository separation, without changing PageController or the persisted model.

### 5. Use layered tests with a real PostgreSQL integration test

**Decision**: Add MVC tests for query binding/status/body behavior, service tests for validation and folder existence/delegation, and Testcontainers PostgreSQL integration tests for full-text, score/tie ordering, direct and recursive scopes, unrelated folders, and unfiled global results. Build valid user/folder/page fixtures using the existing database constraints. Do not create test DDL or replace PostgreSQL with H2.

**Rationale**: Query binding and exception mapping are HTTP behaviors; folder validation is a service rule; `tsvector`, GIN compatibility and recursive CTE semantics are PostgreSQL behaviors. Each is most reliably verified at its existing layer.

## English configuration and Portuguese content

V1 explicitly indexes title/content with PostgreSQL's `english` text-search configuration. That configuration applies English stop-word and stemming rules, so Portuguese content may have less intuitive recall for inflectional variants and some tokens may be normalized/ignored according to English rules. Keep current behavior for this feature as directed; do not alter the configuration, index, or migration. The limitation is recorded in [spec.md](spec.md) and [quickstart.md](quickstart.md).

## References

- `src/main/resources/db/migration/V1__initial_schema.sql`: current schema, foreign keys, B-tree indexes and full-text GIN index.
- `src/main/java/com/wikigerminare/pages/Page.java`, `PageRepository.java`, `PageService.java`, `PageController.java`, `PageExceptionHandler.java`, `pages/dto/PageResponse.java`: existing page mapping, endpoint, read-only service and error/response conventions.
- `src/main/java/com/wikigerminare/folders/Folder.java`, `FolderRepository.java`, `FolderService.java`, `FolderNotFoundException.java`, `FolderExceptionHandler.java`: hierarchy, lookup and error conventions.
- `src/test/java/com/wikigerminare/pages/PageControllerTest.java`, `PageServiceTest.java`, `src/test/java/com/wikigerminare/pages/wikilinks/PageLinkPostgresIntegrationTest.java`, and `src/test/java/com/wikigerminare/TestcontainersConfiguration.java`: current MVC, service and PostgreSQL test patterns.

## Resolved technical context

- No unresolved technical-context questions remain.
- No performance SLA or corpus size was supplied. The plan reuses the current index and makes no numeric latency claim.
- The approved response contract uses the existing PageResponse field set and does not add pagination or snippets.
