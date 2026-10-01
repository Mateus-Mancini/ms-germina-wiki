# Implementation Plan: pages-api

**Branch**: `004-pages-api` (current feature branch) | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification in `specs/004-pages-api/spec.md`

## Summary

Add REST CRUD for Wiki pages, storing Markdown unchanged and referencing the existing folders table by UUID. Accept a client-provided unique slug unchanged at creation and return it in page responses. Map `version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)` with JPA `@Version Integer`, without manual increments or schema changes. Expose strong per-page ETags and require `If-Match` on PATCH; translate stale updates to `412` with the current version after rollback.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Existing Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Springdoc OpenAPI and Bean Validation. No new production dependency is planned.

**Storage**: Existing PostgreSQL `pages`, `folders`, and `users` tables as specified by the user. `spring.jpa.hibernate.ddl-auto=validate`; no DDL, migrations or generated schema. Verify the deployed schema matches the supplied definition before implementation.

**Testing**: Existing JUnit and Spring Boot MVC/JPA test modules; unit/MVC tests plus PostgreSQL integration against a provisioned existing schema.

**Target Platform**: JVM 21 server

**Project Type**: Single Spring Boot REST backend

**Performance Goals**: No numeric SLA or volume target is specified. List returns all pages as required.

**Constraints**: Raw Markdown; title max 255; content is TEXT with no artificial maximum and may be empty; client supplies `slug` (1-300 chars), unchanged and unique; immutable `folderId` after creation; exactly one strong page-specific `If-Match` on PATCH. Missing precondition returns `428`; stale version returns `412`. Principal UUID comes from the host. No auth or out-of-scope API changes.

**Scale/Scope**: `pages-api` CRUD: create, get, list, update title/content, delete.

## Constitution Check

*Initial gate before research: PASS. Post-design gate recheck: PASS, subject to the schema and branch gates below.*

- **Java and platform**: PASS. Java 21, Spring Boot, PostgreSQL, REST and DTOs follow the current Constitution.

- **Layering**: PASS. Controller handles HTTP/ETag/principal; Service owns business rules and optimistic preconditions; Repository owns persistence.

- **Contracts and validation**: PASS. OpenAPI defines DTOs, REST statuses, `If-Match`, `412` and `428`; existing Bean Validation is available.

- **Automated tests**: PASS at design level. Unit, MVC and PostgreSQL integration coverage is planned for success, validation, Markdown, folder references and stale writes.

- **Simplicity and scope**: PASS. Existing dependencies, `FolderRepository` and principal convention are reused. No schema changes or out-of-scope API work.

- **Schema gate**: The supplied schema confirms `version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)`. Map it as `Integer @Version`, let JPA manage updates, and verify the provider inserts initial version `1` without manual increments. Verify the deployed schema and FK actions match the supplied definition; no DDL is authorized.

- **Slug contract**: `slug VARCHAR(300) NOT NULL UNIQUE` has no default. The updated `spec.md` requires the client to send it on create, preserves it exactly, returns it, and forbids PATCH changes. Duplicate values return `409`; no slug generation or schema change is planned.

- **Folder deletion effect**: `folder_id` is nullable and `ON DELETE SET NULL`. Creation still requires an existing folder and PATCH cannot change it; the spec now allows response `folderId` to be null after database-driven folder deletion. Do not block folder deletion or add application cascades.

- **Database test gate**: PostgreSQL test/development environment with existing `pages`, `folders`, and `users` schema must be provisioned. No `DATABASE_URL` is available to this process and no `psql` client is installed; `.env` was not read.

- **Branch gate**: Current branch is `004-pages-api`, satisfying the Constitution's feature-branch requirement for subsequent source changes.

- **Constitution recordkeeping**: Normative Java 21 requirement matches the feature. Sync-impact/version/date metadata still describes the 2026-09-25 amendment; reconcile separately before integration. This plan does not edit the Constitution.

## Design and Implementation Sequence

1. Verify the deployed schema matches the supplied definition and check insert/default behavior read-only. Record discrepancies before mapping; no DDL.

2. Add `Page` and `PageRepository` under `com.wikigerminare.pages`, mapping exactly `id`, `title`, `slug`, `content`, `version`, `folder_id`, `created_by`, `updated_by`, `created_at`, and `updated_at`. Use `Integer @Version`; JPA alone advances the version, the insert starts at `1`, and the positive check remains satisfied. Keep user identifiers as UUID scalars; do not add a User entity.

3. Implement DTOs and transactional Service operations for create, get, list, update and delete. `CreatePageRequest` receives slug from the client; `PageResponse` exposes slug and nullable `updatedBy`; `UpdatePageRequest` excludes slug and folderId. Reuse `FolderRepository` for create-time folder existence, preserve Markdown, and obtain `created_by` from the existing principal at the controller boundary. Do not invent update semantics for `updated_by`.

4. Add the page REST controller and feature-local exception handling. Derive strong ETags from page UUID/version; enforce a single strong `If-Match` on PATCH and map missing, invalid and stale preconditions to `428`, `400` and `412` respectively.

5. Flush update work before returning its incremented version. For a provider-detected optimistic-lock race, allow rollback, then read the latest version in a new transaction and return `412` with its ETag/version.

6. Add Service/MVC tests and PostgreSQL integration tests for the supplied schema, version seed `1`/JPA increment, slug uniqueness, FK behavior, Markdown round-trip and two concurrent updates using the same ETag. Verify page delete applies database CASCADE/SET NULL actions without API-side cascading; a `409` is only for an actual FK violation, not the listed dependent rows. Run against the existing-schema test database; add no DDL or test replacement schema.

## Supplied Page Schema Mapping

| Column | Schema fact | Plan consequence |
|---|---|---|
| `id` | UUID PK, `DEFAULT gen_random_uuid()` | Map UUID and preserve database default unless current ID generation convention requires an explicitly verified equivalent. |
| `title` | `VARCHAR(255) NOT NULL` | Required, non-blank, maximum 255; do not use folders' 150-character limit. |
| `slug` | `VARCHAR(300) NOT NULL UNIQUE`, no default | Required; supplied by the client on create, returned unchanged, immutable on PATCH; duplicate returns `409`. |
| `content` | `TEXT NOT NULL DEFAULT ''` | Required, empty string allowed, no schema-defined maximum; preserve raw Markdown exactly. |
| `version` | `INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)` | Map `Integer @Version`; test insert starts at 1 and JPA increments updates. Never increment manually. |
| `folder_id` | Nullable UUID FK to `folders(id) ON DELETE SET NULL` | Validate an existing folder on create; PATCH cannot change it; represent database-set null if the folder is later deleted. |
| `created_by` | UUID NOT NULL FK to `users(id) ON DELETE RESTRICT` | UUID from authenticated principal, never freely supplied by request. |
| `updated_by` | Nullable UUID FK to `users(id) ON DELETE SET NULL` | Map nullable UUID scalar and expose its stored nullable value; the spec does not define who populates it, so do not invent write behavior. |
| `created_at`, `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | Map to `Instant`; preserve schema/default behavior and update timestamp only on accepted changes. |

**Inbound page references**: `page_images.page_id`, `comments.page_id`, `page_tags.page_id`, and `page_links.source_page_id` use `ON DELETE CASCADE`; `page_links.target_page_id` uses `ON DELETE SET NULL`. Do not reproduce these actions in application code. None of these supplied relationships is an expected page-delete `23503` scenario.

## Project Structure

### Documentation (this feature)
