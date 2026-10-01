# Data Model: search-api

**Feature**: [spec.md](spec.md)

## Existing entities used

This feature adds no persisted entity, table, column, relationship, or migration. The checked-in PostgreSQL V1 schema is the source of truth.

### Page (`pages`)

| Field | Database column | Existing constraint / relationship | Search use |
|------|-----------------|------------------------------------|-------------|
| `id` | `id` | UUID primary key | Stable secondary ordering for equal relevance |
| `title` | `title` | `VARCHAR(255) NOT NULL` | Included in full-text vector |
| `content` | `content` | `TEXT NOT NULL DEFAULT ''` | Included in full-text vector |
| `folderId` | `folder_id` | Nullable UUID FK to `folders(id) ON DELETE SET NULL` | Exact folder scope or recursive folder membership |
| Other response fields | `slug`, `version`, `created_by`, `updated_by`, `created_at`, `updated_at` | Existing Page fields/constraints | Returned through the existing PageResponse shape; not search predicates |

**Matching expression already indexed**:

```sql
to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(content, ''))
```

Index: `idx_pages_full_text_search` (GIN). The query's vector expression and configuration must remain compatible with this index. Search score is calculated from that vector and the parsed `q` query. Results order by score descending, then UUID ascending.

### Folder (`folders`)

| Field | Database column | Existing constraint / relationship | Search use |
|------|-----------------|------------------------------------|-------------|
| `id` | `id` | UUID primary key | `folderId` parameter and recursive traversal node |
| `parentFolder` | `parent_folder_id` | Nullable self-FK to `folders(id) ON DELETE CASCADE` | Defines descendants |

`idx_folders_parent` indexes `parent_folder_id`. The folder selected by `folderId` is part of its own descendant scope; each child is reached by matching its `parent_folder_id` to the current folder ID. `folders-api` prevents cycles on service-mediated reparenting; the recursive query should deduplicate IDs to bound traversal if unexpected cycles exist.

## Search result

Search results are read-only Page records represented using the existing `PageResponse` fields:

`id`, `title`, `slug`, `content`, `version`, `folderId`, `createdBy`, `updatedBy`, `createdAt`, `updatedAt`.

The score is used for ordering and is not added to the response because the approved specification requests the existing page response shape. No search data is persisted.

## Scope behavior

| Inputs | Eligible pages |
|--------|----------------|
| `folderId` absent; either `includeSubfolders` value | All matching pages, including `folder_id IS NULL` |
| `folderId` provided, `includeSubfolders=false` or omitted | Matching pages whose `folder_id` equals the selected UUID |
| `folderId` provided, `includeSubfolders=true` | Matching pages whose `folder_id` is the selected UUID or any descendant UUID |
| `folderId` provided but not present in `folders` | No result query; service returns the established folder-not-found error (`404`) |

All scope rules compose with the same full-text predicate and result ordering. No scope includes pages without a folder.
