# Quickstart: search-api

This guide validates the search feature after implementation. It creates no schema and does not change the PostgreSQL text-search configuration.

## Prerequisites

- JDK 21 and the existing Spring Boot application.
- PostgreSQL with the existing V1 `users`, `folders`, and `pages` schema and indexes, including `idx_pages_full_text_search`.
- Docker available for the existing Testcontainers PostgreSQL tests, or an equivalent project-supported PostgreSQL test environment.
- The existing database connection configuration. Do not include credentials in test output or commit them.
- Search must be exposed as `GET /api/search` with the response and parameter contract in [search-api.openapi.json](contracts/search-api.openapi.json).

## Automated validation

From the repository root, run the feature tests after implementation:

```powershell
.\mvnw.cmd "-Dtest=SearchControllerTest,SearchServiceTest,SearchPostgresIntegrationTest" test
```

Then run the complete regression suite:

```powershell
.\mvnw.cmd test
```

Expected coverage:

- Controller/API: successful list response, absent `q`, blank `q`, malformed `folderId`, invalid `includeSubfolders`, default false, and folder-not-found status/body.
- Service: blank-query rejection, validation that a provided folder exists, ignoring descendant expansion when no folder is provided, and mapping results to the existing PageResponse fields.
- PostgreSQL: matching text in title, matching text in content, more relevant result first, UUID ascending tie order, direct folder only, selected folder plus nested descendants, exclusion of unrelated folders, global inclusion of unfiled pages, empty results, and both `includeSubfolders=false` and `true`.

PostgreSQL full-text and recursive folder scenarios must run against PostgreSQL; H2 does not validate compatibility with the existing GIN expression/configuration or PostgreSQL recursive CTE behavior. Do not edit V1 or apply DDL for this feature.

## Request examples

Global search, including pages with no folder:

```http
GET /api/search?q=student%20handbook
```

Direct children of one folder only (`includeSubfolders` defaults to false):

```http
GET /api/search?q=student&folderId=550e8400-e29b-41d4-a716-446655440000
```

Include the selected folder and every descendant:

```http
GET /api/search?q=student&folderId=550e8400-e29b-41d4-a716-446655440000&includeSubfolders=true
```

Expected successful responses are `200 OK` with an array using the existing PageResponse fields. The array is ordered by relevance descending, then page UUID ascending; no matches produce `[]`. A missing or blank `q`, malformed folder UUID, or invalid boolean produces `400 Bad Request`. A well-formed UUID for a nonexistent folder produces `404 Not Found` using the current `{ "error": "..." }` error body convention.

## Interpretation note

The existing index and search use the PostgreSQL `english` text-search configuration. English stemming/stop-word behavior may be a poor fit for Portuguese morphology. This feature documents that limitation but does not change the configuration or index.
