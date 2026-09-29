# Quickstart: pages-api

This guide validates the planned feature after implementation. It does not create or initialize database schema.

## Prerequisites

- JDK 21.
- PostgreSQL test/development database provisioned with the existing `pages`, `folders` and `users` schema and its actual FK actions.
- Confirm the exact numeric version column name/type, nullability, existing values and insert default before JPA mapping. Do not run migrations or DDL for this feature.
- `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` set by the team's local environment/secrets mechanism, matching `src/main/resources/application.properties`.
- Hosting authentication configured outside this feature, with the authenticated UUID available as `Principal.getName()`.

Do not commit local credentials. This feature defines no token, login endpoint or auth header format.

## Automated validation

From repository root:

```powershell
.\mvnw.cmd test
```

Expected: page service and MVC tests cover CRUD, missing folder/page, title/content validation, raw Markdown round-trip, `ETag`, `If-Match`, `400`, `412`, `428`, and current-version response. PostgreSQL integration tests run only against the provisioned existing-schema database, verify `@Version` mapping/FK behavior, and use independent transactions for the stale-write race. The repository currently has no local PostgreSQL test service or page schema fixture; database integration scenarios require that environment.

## End-to-end scenarios

Run only after the DB and authentication prerequisites are configured:

```powershell
.\mvnw.cmd spring-boot:run
```

Use an API client authenticated by the host infrastructure. Example requests below use placeholders, not a prescribed authentication header.

1. `POST /api/pages` with `{"title":"Getting started","content":"# Welcome\n\nRaw **Markdown**.","folderId":"<existing-folder-uuid>"}` returns `201 Created`, a page response with numeric `version`, and a strong `ETag` header. `createdBy` is derived from the principal.
2. `GET /api/pages/<page-uuid>` returns `200 OK`, the same raw Markdown and current `ETag`.
3. `GET /api/pages` returns `200 OK`; each item exposes its current numeric version. An empty database returns `[]`.
4. `PATCH /api/pages/<page-uuid>` with `If-Match: "<page-uuid>-v<version>"` and `{"content":"Updated **Markdown**."}` returns `200 OK`, an incremented version and new ETag.
5. Repeat PATCH with the old ETag. Expect `412 Precondition Failed`, current ETag/version in the response, and unchanged latest title/content.
6. PATCH without `If-Match` returns `428 Precondition Required`; a malformed/unsupported tag returns `400 Bad Request`.
7. Create with an unknown `folderId`, or request an unknown page UUID. Expect `404 Not Found` and no partial insert/update.
8. `DELETE /api/pages/<page-uuid>` returns `204 No Content` when existing FKs allow deletion; if PostgreSQL blocks it, expect `409 Conflict` with no API-side cascade.

## Result interpretation

- `400 Bad Request`: invalid title/content, UUID, or unsupported/malformed `If-Match` value.
- `404 Not Found`: page or referenced folder UUID does not exist.
- `412 Precondition Failed`: a valid strong ETag is stale; response reports the latest version after rollback.
- `428 Precondition Required`: PATCH omitted `If-Match`.
- `409 Conflict`: PostgreSQL's existing FK rules prevent deletion.

