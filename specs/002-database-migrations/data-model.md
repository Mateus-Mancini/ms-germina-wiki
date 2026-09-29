# Data Model: Database Migrations

**Feature**: `002-database-migrations` | **Date**: 2026-09-27

## Application schema (V1, from the team's `db-script`)

This feature introduces no new domain model; V1 reproduces the approved schema verbatim. The objects verified by `SchemaMigrationTest` (SC-001):

| Kind | Objects |
|---|---|
| Extension | `pgcrypto` |
| Enum types | `user_role` (`admin`, `member`), `comment_status` (`OPEN`, `RESOLVED`) |
| Tables | `users`, `folders`, `pages`, `page_images`, `comments`, `tags`, `page_tags`, `page_links` |
| Key rules | UUID PKs via `gen_random_uuid()`; `pages.version` > 0 (optimistic locking); unique `pages.slug`, `users.email`, `tags.name`, `(parent_folder_id, name)`, `(source_page_id, target_page_title)` |
| Full-text | GIN index `idx_pages_full_text_search` on `to_tsvector('english', title ‖ ' ' ‖ content)` |
| Function / triggers | `update_updated_at()`; triggers `users_updated_at`, `folders_updated_at`, `pages_updated_at`, `comments_updated_at` |

## Schema history (Flyway-managed)

`flyway_schema_history`, created by Flyway on first migrate:

| Field | Meaning |
|---|---|
| `installed_rank` | Order in which migrations were applied |
| `version`, `description` | From the file name `V<version>__<description>.sql` |
| `checksum` | CRC32 of the file; a mismatch on validate rejects the run (FR-005) |
| `installed_on`, `execution_time`, `success` | Audit and failure visibility |

## Migration lifecycle

```
new file on a branch ──tests (Testcontainers)──> rehearsed on a Neon branch ──PR approved + merged──> applied to production from main
                                                                                                   (never edited afterwards; fix forward with V<n+1>)
```

## Disposable database copy (Neon branch)

| Attribute | Value |
|---|---|
| name | `rehearse-<git-branch>-<timestamp>` |
| parent | `main` (production) |
| expiry | now + 1 h (safety net; the script deletes it right after use) |
| cost | free (the plan includes 10 branches) |
