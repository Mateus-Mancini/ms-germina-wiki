# Implementation Plan: wikilinks-backlinks

**Branch**: `007-wikilinks-backlinks` | **Date**: 2026-09-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification in `specs/007-wikilinks-backlinks/spec.md`

## Summary

Recognize `[[slug]]` in existing Page Markdown, maintain one directed relation per source/target pair in the existing PostgreSQL `page_links` table, and expose outgoing WikiLinks and incoming backlinks as read-only endpoints. Link synchronization is part of the same transaction as Page content writes; raw Markdown remains unchanged. Before mapping the relation, inspect the real table key/unique constraints because repository documentation confirms only its two page foreign keys.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, PostgreSQL driver, Bean Validation and Springdoc OpenAPI. No new library is planned.

**Storage**: Existing `pages` and `page_links` tables in PostgreSQL. `page_links.source_page_id` references `pages.id ON DELETE CASCADE`; `target_page_id` references `pages.id ON DELETE SET NULL`, as documented by feature 002. The actual primary key, additional columns, and pair-unique constraint are not present in repository DDL and must be inspected read-only before mapping. No migrations or DDL.

**Testing**: Existing JUnit and Spring Boot MVC/JPA test modules. Parser/service unit tests, MVC tests, and PostgreSQL integration tests against the actual schema.

**Target Platform**: JVM 21 server

**Project Type**: Single Spring Boot REST backend

**Performance Goals**: No latency, scale, paging, or ordering SLA is specified. Target-page creation may require scanning existing page content to resolve pending links.

**Constraints**: Preserve Markdown verbatim; recognize only `[[slug]]` outside inline/fenced code; exact slug matching; one logical relation per source-target pair; no direct relation mutation endpoints; integrate sync into existing page-content writes; no new schema objects or authentication changes.

**Scale/Scope**: Two read endpoints: page outgoing WikiLinks and backlinks; synchronize existing relation rows on Page create/content update and re-resolve pending references when a target page is created.

## Constitution Check

*Initial gate before research: PASS. Post-design gate recheck: PASS, subject to schema and pages-api prerequisites below.*

- **Java/platform**: PASS. Java 21, Spring Boot, PostgreSQL, REST and DTOs follow the current Constitution.
- **Layering**: PASS. Controller handles route/path/status; Service parses and synchronizes WikiLinks; Repository queries/persists PageLink relations.
- **Contracts**: PASS. OpenAPI defines outgoing and backlink GET routes, DTO summaries, empty arrays, malformed IDs and not-found behavior.
- **Automated tests**: PASS at design level. Parser, service, MVC, persistence, concurrency and FK behavior have planned coverage.
- **Simplicity/scope**: PASS. Reuse Page/slug and existing `page_links`; no auth feature, direct link CRUD, new library, DDL or migrations.
- **Schema gate**: Docs for 002 confirm `source_page_id` and `target_page_id` plus CASCADE/SET NULL FKs, but no local DDL exists. Before mapping, inspect actual columns, primary key, unique constraints/indexes and duplicate rows. Stop and report if unique logical pairs cannot be maintained without schema changes; do not add DDL.
- **Page lifecycle gate**: Current checked-in `PageService` has create/get/list only and `PageController` has no update route, while pages-api 002 specifies content updates. Link removal-on-edit and transactional sync require the 002 page-content update flow to exist first. Do not create a separate content endpoint in this feature.
- **Database environment**: No process `DATABASE_URL` or `psql` client was available; `.env` was not read. PostgreSQL integration tests require the provisioned existing schema.
- **Branch**: Current branch is `007-wikilinks-backlinks`.

## Implementation Sequence

1. Read-only introspect `page_links`: list columns, primary key, constraints, indexes, FK actions and existing duplicate source-target pairs. Compare with the documented two FK columns and record verified facts in `data-model.md`; no database modification.
2. Confirm the `pages-api` content-write flow from feature 002 (create and PATCH) is available on the integration baseline. Do not implement or change page CRUD routes in this feature. If PATCH is not available, hold content-edit synchronization until 002 provides it.
3. Map the existing relation using its actual key and columns. Use a `PageLink` entity only if the real key supports JPA identity; otherwise use verified repository queries/projections. Rely on an existing pair-unique constraint if present. If absent, coordinate feature-owned sync transactions and prove logical uniqueness without DDL; stop if the requirement cannot be met safely.
4. Implement a focused scanner for exact `[[slug]]`, ignoring inline/fenced code and preserving Markdown. Resolve targets by exact existing `Page.slug`; collect distinct targets, including a self-target.
5. Synchronize source relations transactionally with Page create/update: remove obsolete pairs, retain existing pairs and insert missing resolved pairs. Leave unresolved tokens in Markdown; when a Page is created, re-evaluate existing page contents for its slug. Let PostgreSQL perform source CASCADE and target SET NULL effects.
6. Add `GET /api/pages/{pageId}/wikilinks` and `GET /api/pages/{pageId}/backlinks`, returning one summary per related Page. Return empty arrays for an existing page with no results and 404 for a missing requested page.
7. Validate parser/service/MVC logic and the real PostgreSQL mapping, deduplication, concurrency, transaction rollback, broken-link activation and FK delete effects. Use the existing schema; add no DDL or test replacement schema.

## Project Structure

### Documentation (this feature)

specs/007-wikilinks-backlinks/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/wikilinks-backlinks.openapi.json
└── tasks.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

-->

src/main/java/com/wikigerminare/pages/
├── Page.java                         # Existing; reuse
├── PageRepository.java               # Existing; extend with slug/content queries if needed
├── PageService.java                  # Existing; integrate link sync in Page content writes
└── wikilinks/
    ├── PageLink.java                  # Only if actual page_links key supports JPA mapping
    ├── PageLinkRepository.java
    ├── WikiLinkService.java
    ├── WikiLinkParser.java
    ├── WikiLinkController.java
    └── dto/LinkedPageSummary.java

src/test/java/com/wikigerminare/pages/wikilinks/
├── WikiLinkParserTest.java
├── WikiLinkServiceTest.java
├── WikiLinkControllerTest.java
└── PageLinkPostgresIntegrationTest.java
└── [platform-specific structure: feature modules, UI flows, platform tests]
```
**Structure Decision**: Keep the single Maven project and `com.wikigerminare.pages` boundary. Put feature-specific controller/service/repository/parser/DTO under `pages.wikilinks`. Reuse Page and PageRepository. `PageLink` entity is conditional on the real existing table key; do not invent a primary-key field.
directories captured above]

## Risks and Boundaries

- The physical `page_links` key and UNIQUE constraint are absent from repository DDL; mapping must stop if introspection cannot confirm a safe mapping and logical-pair guarantee without schema change.
- Page PATCH/content update is not implemented in current source, although feature 002 specifies it. Synchronization on removal depends on that content-write flow being available.
- Creating a target scans existing Page contents to resolve pending slugs. No scale/latency target was specified; reassess only if the existing corpus makes this approach impractical.
- All writers must follow the transactional sync protocol; external direct writes to Page content or `page_links` can make relations stale.
- PostgreSQL integration tests require the provisioned database and existing schema.

## Complexity Tracking

No Constitution violations require justification. The parser, relation synchronization and two read routes implement the requested behavior using existing Page and PostgreSQL structures.
