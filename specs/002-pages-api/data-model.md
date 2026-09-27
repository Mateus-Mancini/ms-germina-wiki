# Data Model: pages-api

## Page

A Wiki page maps to the existing PostgreSQL `pages` table. The repository contains no DDL or page entity, so all physical column names, exact SQL types, nullability, defaults, and foreign-key actions must be verified against PostgreSQL before entity mapping. Do not add or alter schema objects.

| Logical field | Physical column | Mapping / rule |
|---|---|---|
| `id` | Verify in schema | UUID identifier, required, primary key. Reuse the existing generation/default strategy. |
| `title` | Verify in schema | Required non-blank text. Respect the existing maximum length; reject overflow rather than truncate. |
| `content` | Verify in schema | Required text containing raw Markdown; empty string is allowed. Preserve bytes/characters and line endings through API persistence/response without rendering or normalization. |
| `folder` | Verify in schema | Required UUID FK to the existing `folders` row. The page's folder is immutable after creation. |
| `createdBy` | Verify in schema | Required UUID from the host-provided `Principal.getName()`; do not accept from the request body or create/change user/auth entities. |
| `createdAt` | Verify in schema | Required creation timestamp; use the existing database/application assignment convention. |
| `updatedAt` | Verify in schema | Required update timestamp; change only on successful updates. |
| `version` | Existing numeric column; inspect exact name/type | Required numeric optimistic-lock value mapped using `@Version` to the exact existing column. No DTO may set it and application code must not manually increment it. Confirm current rows and insert default before mapping. |

## Relationships

- Each page references exactly one existing folder by UUID. Validate folder existence before insert; let the existing FK remain the final integrity boundary.
- Page creator is the UUID supplied by the existing authenticated principal. Authentication and user resolution remain outside this feature.
- Folder assignment is immutable after create; updates change only title and/or content.
- Other inbound references and their configured `ON DELETE` actions are unknown locally. Page deletion must follow the real FK actions; no application cascade, DDL or reassignment is introduced.

## Invariants and state changes

- Title is required and non-blank. Exact maximum length follows the existing `pages` schema.
- Content is required but may be an empty string; content is stored as raw Markdown and not rendered, sanitized, converted, or reformatted by the API.
- Create assigns the initial numeric version according to the mapped provider/schema convention and returns it; confirm actual insert behavior in PostgreSQL.
- Every accepted update compares and advances version atomically. The response returns the newly persisted numeric version.
- Stale preconditions do not change title, content, or `updatedAt`. For an optimistic-lock race, transaction rollback completes before reading and returning the current version.
- `folderId` is immutable once the page has been created.

## API representations

- `CreatePageRequest`: `title`, `content`, `folderId`; no `createdBy` or version input.
- `UpdatePageRequest`: one or both of `title`, `content`; no `folderId` or version field. Expected version is carried by a single strong `If-Match` header.
- `PageResponse`: `id`, `title`, raw `content`, `folderId`, `createdBy`, `createdAt`, `updatedAt`, and numeric `version`.
- HTTP ETag: strong tag derived from page UUID and version, emitted on create, individual read and successful update. List items expose numeric version in their JSON; collection ETag is not an item's ETag.
