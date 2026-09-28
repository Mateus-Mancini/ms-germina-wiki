# Data Model: wikilinks-backlinks

## Existing Page

Reuse the existing `com.wikigerminare.pages.Page` entity and `pages` table. This feature does not add page fields or change the page CRUD contract.

| Field | Existing source | Use in this feature |
|---|---|---|
| `id` | `pages.id UUID` | Source/target identifier in the directed link relation and requested page path. |
| `slug` | `pages.slug VARCHAR(300) NOT NULL UNIQUE` | Exact WikiLink target lookup for `[[slug]]`; no normalization. |
| `content` | `pages.content TEXT NOT NULL` | Source text parsed for WikiLinks; never rewritten by the parser. |
| `title` | `pages.title VARCHAR(255) NOT NULL` | Display field in linked-page summaries. |
| `version` | `pages.version INTEGER` with JPA `@Version` | Existing optimistic lock remains authoritative for content writes; link synchronization commits or rolls back with that write. |

## Existing PageLink relation

`page_links` is documented by `specs/002-pages-api/data-model.md` as an existing relation with:

| Logical field | Existing column | Existing FK action | Use |
|---|---|---|---|
| `sourcePageId` | `source_page_id` | `ON DELETE CASCADE` to `pages.id` | Page whose Markdown contains the WikiLink. |
| `targetPageId` | `target_page_id` | `ON DELETE SET NULL` to `pages.id` | Resolved page targeted by the WikiLink. Null target rows are unresolved/orphaned database relations and are not returned as current outgoing/backlink results. |

The repository has no DDL/entity/migration for `page_links`. Its primary key, additional columns, exact FK constraint names, indexes, and unique constraint on the pair are not confirmed. Inspect PostgreSQL metadata read-only before choosing JPA identity/repository mapping. Do not invent columns or schema constraints. If no usable key or safe logical-pair enforcement exists, stop and report rather than adding DDL.

## Logical WikiLink

A logical WikiLink is a directed pair `(source_page_id, target_page_id)` resolved from one or more exact `[[slug]]` tokens in source Markdown.

- A source-target pair appears once in API results even if its token occurs several times.
- A page may link to itself; source and target UUID may be equal.
- Tokens inside inline code and fenced code do not form relations.
- A token with no existing matching slug remains in raw content but has no active relation.
- When a target page with that slug is created, existing page contents are re-evaluated and matching logical pairs are created.
- Removing the final matching token from a source content write removes the pair; retaining at least one occurrence retains one pair.
- On source deletion, PostgreSQL cascades link rows. On target deletion, PostgreSQL sets target ID to null. API code does not emulate either action.

## API DTOs

### `LinkedPageSummary`

- `id`: existing Page UUID.
- `title`: existing Page title.
- `slug`: existing unique Page slug.

### Endpoints

- `GET /api/pages/{pageId}/wikilinks`: one summary per resolved outgoing target.
- `GET /api/pages/{pageId}/backlinks`: one summary per source page with a resolved edge to the requested page.

Both endpoints return `[]` for an existing page with no results. They do not return raw link occurrences or unresolved slugs.
