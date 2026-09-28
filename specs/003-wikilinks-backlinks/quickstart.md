# Quickstart: wikilinks-backlinks

This guide validates the feature after implementation. It assumes the existing Page API and PostgreSQL schema are available; it creates no schema.

## Prerequisites

- JDK 21 and the existing Spring Boot application.
- `pages-api` create and content-update flows available so link synchronization can run in the page-write transaction. The current checked-in `PageService` has create but no update method/controller route; confirm the 002 implementation is complete before exercising content edits.
- PostgreSQL with existing `pages`, `folders`, `users`, and `page_links` tables.
- Before mapping/testing, verify the exact `page_links` columns, primary key, pair uniqueness/indexes, and FK actions using read-only schema inspection. Do not run DDL/migrations.
- Database credentials and connection variables configured through the team's environment/secrets mechanism. Do not commit or print credentials.
- Existing host authentication provides the principal needed by page creation; this feature adds no auth mechanism.

## Automated validation

From the repository root:

```powershell
.\mvnw.cmd test
```

Expected tests include WikiLink parsing outside inline/fenced code, exact slug lookup, raw content preservation, unresolved-link behavior and later target creation, pair de-duplication, removal after the final occurrence disappears, outgoing/backlink endpoint statuses, missing pages, same-page links, transaction rollback under stale page version, and actual PostgreSQL CASCADE/SET NULL behavior.

PostgreSQL integration tests must use the existing schema. Do not create a test replacement table or apply DDL. If the local database is unavailable, run the unit and MVC test slices and report the database integration prerequisite as unverified.

## End-to-end scenarios

Start the app after configuring the existing database and host authentication:

```powershell
.\mvnw.cmd spring-boot:run
```

Use a client authenticated through the host infrastructure. The `Authorization` mechanism is not defined by this feature.

1. Create a target page with the existing Page API and a client-provided unique slug, for example `target-page`, using a body that includes `title`, `slug`, `content` and `folderId`. A duplicate slug is rejected by the existing Page API with `409 Conflict`.
2. Create or update a source page through the existing Page API with a unique client-provided slug and content containing `[[target-page]]` outside code. Preserve and read back the exact Markdown.
3. `GET /api/pages/<source-id>/wikilinks` returns one summary for the target (`id`, `title`, `slug`).
4. `GET /api/pages/<target-id>/backlinks` returns one summary for the source.
5. Put the same token twice in source content; outgoing links still contain one target. Remove one occurrence and it remains; remove the last occurrence and the relation disappears.
6. Put `[[target-page]]` in inline code and fenced code; those tokens do not add links.
7. Save `[[future-page]]` before that slug exists; content is accepted and retained, but no relation is returned. Create a page with slug `future-page`; existing content is re-evaluated and the outgoing/backlink relationship appears.
8. A page may contain `[[its-own-slug]]`; the same page appears once in both its outgoing list and backlinks.
9. Request either link route with a valid but nonexistent page UUID and expect `404 Not Found`; with a page that has no results expect `200 OK` and `[]`.
10. Delete a source page and verify outgoing rows are removed by PostgreSQL CASCADE. Delete a target page and verify `target_page_id` becomes null; no application cascade is performed.

## Result interpretation

- `400 Bad Request`: malformed page UUID.
- `404 Not Found`: requested source/target page does not exist.
- `200 OK` with `[]`: requested page exists, but it has no resolved outgoing/incoming relation.
- Backlink/outgoing lists show one row per logical source-target pair, not one per textual occurrence.
