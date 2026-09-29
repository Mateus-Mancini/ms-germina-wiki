# Data Model: pages-api

## Page

`Page` maps to the supplied existing PostgreSQL `pages` table. Do not add or alter database objects.

| Logical field | Column | PostgreSQL definition | Mapping / rule |
|---|---|---|---|
| `id` | `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` | UUID identifier; preserve the existing database generation/default strategy. |
| `title` | `title` | `VARCHAR(255) NOT NULL` | Required, non-blank, maximum 255 characters; reject overflow, never truncate. |
| `slug` | `slug` | `VARCHAR(300) NOT NULL UNIQUE`, no default | Required on create and supplied by the client. Accept 1-300 characters; persist and return exactly as received; no generation, title derivation, or normalization. Immutable on PATCH. Duplicate value returns `409 Conflict` using the existing UNIQUE constraint. |
| `content` | `content` | `TEXT NOT NULL DEFAULT ''` | Required, empty string allowed, no schema-defined length limit. Preserve raw Markdown exactly, including line endings and characters. |
| `version` | `version` | `INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)` | Java `Integer` mapped with JPA `@Version`; initial persisted value is 1 and the provider manages increments. Never set/increment manually. |
| `folderId` | `folder_id` | Nullable UUID FK to `folders(id) ON DELETE SET NULL` | Creation requires an existing folder per spec. Immutable on PATCH. If the folder is deleted, PostgreSQL sets this value to null; API response must represent null. |
| `createdBy` | `created_by` | `UUID NOT NULL` FK to `users(id) ON DELETE RESTRICT` | UUID from host-provided `Principal.getName()`; not accepted from the request body. No User entity/API changes. |
| `updatedBy` | `updated_by` | Nullable UUID FK to `users(id) ON DELETE SET NULL` | Expose the stored nullable value in responses. The spec does not define how it is populated; do not invent update semantics. |
| `createdAt` | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | Map to `Instant`; preserve existing database/application assignment behavior. |
| `updatedAt` | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | Map to `Instant`; refresh only for accepted page updates. |

## Relationships

- At creation, a page must reference an existing folder UUID. The existing FK is nullable and `ON DELETE SET NULL`, so a page can have null `folderId` after its folder is deleted.
- `created_by` references an existing user and is obtained from the authenticated principal. User/authentication behavior remains outside this feature.
- `updated_by` is nullable and references an existing user. The contract returns its stored value but does not define when it is changed.
- Inbound references: `page_images.page_id`, `comments.page_id`, `page_tags.page_id`, and `page_links.source_page_id` use `ON DELETE CASCADE`; `page_links.target_page_id` uses `ON DELETE SET NULL`.
- Page deletion must let PostgreSQL apply these actions. Do not implement cascades/reassignment in the API. A `409` is only for an actual FK violation beyond the supplied actions, not the listed related records.

## Invariants and state changes

- `title` is required, non-blank, and at most 255 characters.
- `slug` is required, 1-300 characters and unique. The API takes it from the client only at creation, does not transform it, returns it, and never accepts it on PATCH.
- `content` is required but may be an empty string. It is raw Markdown with no schema-defined maximum; no rendering, sanitizing, conversion, or normalization.
- New pages start at version `1`; accepted updates advance the integer version exactly once via JPA optimistic locking. Updates compare the strong `If-Match` ETag atomically. Stale preconditions do not change title, content, or `updatedAt`.
- `folderId` cannot be changed by PATCH; PostgreSQL may set it to null when the folder row is deleted.
- `createdBy` is set from the principal. No API behavior for writing `updatedBy` is defined.

## API representations

- `CreatePageRequest`: `title`, `slug`, `content`, `folderId`; slug is client-provided; no `createdBy`, `updatedBy`, or version input.
- `UpdatePageRequest`: one or both of `title`, `content`; no `slug`, `folderId`, or version field. Expected version is carried by a single strong `If-Match` header.
- `PageResponse`: `id`, `title`, `slug`, raw `content`, nullable `folderId`, `createdBy`, nullable `updatedBy`, `createdAt`, `updatedAt`, and numeric `version`.
- HTTP ETag: strong tag derived from page UUID and version, emitted on create, individual read and successful update. List items expose numeric version in JSON; collection ETag is not an item's ETag.

