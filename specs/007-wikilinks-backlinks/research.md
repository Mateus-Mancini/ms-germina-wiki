# Research: wikilinks-backlinks

**Feature**: [spec.md](spec.md)
**Date**: 2026-09-28

## Repository findings

- Application is a single Maven project on Java 21 and Spring Boot 4.1.1. Existing dependencies include Spring Web MVC, Spring Data JPA, PostgreSQL driver, Bean Validation and Springdoc OpenAPI; no new dependency is required by this design.
- Existing `Page` maps `pages.id`, `title`, `slug`, `content`, `version`, `folder_id`, `created_by`, `updated_by`, `created_at`, and `updated_at`. `slug` is unique and `version` uses JPA `@Version`.
- `PageRepository` currently extends `JpaRepository<Page, UUID>` without link queries. `PageService` currently creates, reads and lists pages; it does not yet expose page content update/delete operations. `PageController` currently has POST create and GET read/list routes.
- The `002-pages-api` data model documents an existing `page_links` relation through `source_page_id` and `target_page_id`, with source `ON DELETE CASCADE` and target `ON DELETE SET NULL`. No `page_links` DDL, primary key, unique constraint, entity, repository or tests exist in this repository.
- The local process has no `DATABASE_URL` and no `psql` client. A `.env` file exists, but it was not read. Exact `page_links` metadata must be verified in the provisioned PostgreSQL environment before mapping.

## Decisions

### 1. WikiLink syntax and target resolution

**Decision**: Recognize only the literal `[[slug]]` syntax selected for this feature. Resolve `slug` by exact equality against existing `Page.slug`; do not lowercase, trim, derive, normalize or generate a slug. Ignore matches inside inline code and fenced code. Preserve page Markdown exactly as submitted.

**Rationale**: This follows the accepted feature decision and the existing unique page slug. It scopes parsing to one explicit token form and does not change the stored content representation.

**Alternatives considered**: Markdown links, aliases (`[[slug|label]]`), title matching, fragments and generated slugs are outside the confirmed contract.

### 2. Persist directed links from page content

**Decision**: Treat source page Markdown as the textual source of truth and `page_links` as the existing directed relation used for queries. On each page content create/update, parse resolved tokens and synchronize that source page's distinct target set in the same database transaction as the page write. Repeated occurrences to the same target produce one logical source-target relation. Removing the last occurrence removes the relation. Unresolved tokens remain in content and create no relation.

When a new page is created, re-evaluate existing page contents for its slug and create newly resolvable relations. This also permits content that points to the same page's own slug to produce a self-link.

**Rationale**: The user selected content-derived links, unique logical pairs, automatic resolution when a target appears, and allowed self-links. Transactional synchronization prevents link state from reflecting a page write that did not commit.

**Alternatives considered**: Explicit link CRUD endpoints or treating each repeated token occurrence as a separate relation were not selected.

### 3. Reuse the existing `page_links` table; no schema creation

**Decision**: Reuse only the existing `source_page_id` and `target_page_id` relation documented in `002-pages-api`. Before JPA mapping, inspect the real table columns, primary key, unique constraints/indexes, FK actions and existing duplicate rows. Map using the actual key if compatible. Do not create a table, column, constraint, index, migration or DDL. If the physical schema cannot safely represent one logical pair without a new schema object, stop and report the incompatibility rather than altering the database.

**Rationale**: The source repo documents FK behavior but does not contain the DDL or table key. Assuming a composite key or unique constraint would invent schema. The documented FK actions remain database-owned: deleting a source cascades its outgoing rows; deleting a target nulls target IDs.

**Concurrency note**: Check whether the deployed schema enforces pair uniqueness. If it does, rely on it and translate actual duplicate races to idempotent synchronization. If it does not, implementation must coordinate API link synchronization transactionally (the existing FolderRepository demonstrates transaction-scoped PostgreSQL advisory locks) and confirm that this can uphold the logical one-pair rule without DDL. Direct writers that bypass that coordination remain outside this feature's guarantee.

### 4. Read-only outgoing and backlink endpoints

**Decision**: Provide `GET /api/pages/{pageId}/wikilinks` for outgoing targets and `GET /api/pages/{pageId}/backlinks` for incoming sources. Both return a list of summaries containing only existing Page fields `id`, `title`, and `slug`; return an empty array for no resolved relations, `404` for a missing requested page, and `400` for malformed UUID. No endpoint directly creates or removes a relation.

**Rationale**: These are the read operations selected for the feature; link mutation follows page content and existing `pages-api` writes.

### 5. Integrate with existing page lifecycle and test real PostgreSQL behavior

**Decision**: Add a feature-local Service/repository boundary and integrate link synchronization into existing Page content writes without changing the public CRUD contract. The feature depends on a page update/content-save transaction being available; current source has create but no update controller/service method, although `002-pages-api` specifies one. Do not introduce a new page content endpoint under this feature.

Use unit tests for scanner and set reconciliation, MVC tests for the two GET routes, and PostgreSQL integration tests for table mapping, pair de-duplication, transactions and FK effects. No test DDL/replacement schema and no new parser dependency are planned.

**References (local)**: `src/main/java/com/wikigerminare/pages/Page.java`, `PageRepository.java`, `PageService.java`, `PageController.java`; `specs/002-pages-api/data-model.md`; `src/main/java/com/wikigerminare/folders/FolderRepository.java` for the existing advisory transaction lock pattern.

## Resolved technical context

- No latency, scale, pagination or ordering guarantee was requested; endpoint result ordering is unspecified.
- Link summaries expose no Markdown occurrences/counts; repeated references collapse to one page pair.
- The exact physical key and pair uniqueness of `page_links` are environment facts, not assumed schema. Verify them read-only before entity/repository mapping.
