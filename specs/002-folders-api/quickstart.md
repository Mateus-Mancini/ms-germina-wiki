# Quickstart: folders-api

This guide validates the planned feature. It assumes implementation is complete; it does not create schema or configure authentication.

## Prerequisites

- JDK 21.
- PostgreSQL reachable by the application, already containing the `users` and `folders` schema described in [data-model.md](data-model.md).
- A test database using the same PostgreSQL FK actions as the target schema.
- `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` set in the current PowerShell session, following `src/main/resources/application.properties`.
- At runtime, the hosting authentication layer must supply an authenticated principal with the user's UUID. This feature does not configure credentials or tokens.

Set database values through the team's local environment/secrets mechanism; do not commit credentials. For a local development database, for example:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/germinawiki'
$env:DATABASE_USERNAME = '<local-user>'
$env:DATABASE_PASSWORD = '<local-password>'
```

Replace placeholders with local development values before running commands.

## Automated validation

From the repository root:

```powershell
.\mvnw.cmd test
```

Expected: Service and MVC tests validate request handling, status codes, DTOs, partial-update semantics, and hierarchy rules; PostgreSQL integration tests validate actual JPA mapping, FK delete behavior, and concurrent reparent protection. The current repository has no schema fixture or test database service, so DB tests require the prerequisite PostgreSQL schema. Do not substitute H2 for PostgreSQL-specific checks.

## End-to-end scenarios

Run the application only after setting the prerequisites:

```powershell
.\mvnw.cmd spring-boot:run
```

Use an API client authenticated through the host application's existing authentication mechanism. The feature does not define an authorization header or token format.

1. `POST /api/folders` with `{"name":"Architecture"}` returns `201 Created`; `createdBy` is derived from the authenticated principal, not from the request body.
2. Create a child with `{"name":"Decisions","parentFolderId":"<root-id>"}`; it returns `201 Created` and refers to the root.
3. `GET /api/folders/<child-id>` returns `200 OK`; `GET /api/folders` returns the collection; `GET /api/folders/tree` returns the root with the child nested under `children`.
4. `PATCH /api/folders/<child-id>` with `{"parentFolderId":null}` returns `200 OK` and makes the child a root. An omitted field remains unchanged.
5. Attempt to set a folder as its own parent or under a descendant; expect `409 Conflict` and verify the persisted tree is unchanged.
6. `DELETE /api/folders/<id>` returns `204 No Content` if the configured FK actions allow deletion. If the database rejects the deletion for dependent rows, expect `409 Conflict` with no partial API-side changes.
7. Submit a blank/whitespace name, a name longer than 150 characters, or an empty PATCH object; expect `400 Bad Request`.

## Result interpretation

- `404 Not Found`: target folder or requested parent UUID does not exist.
- `409 Conflict`: cycle/self-parent attempt or PostgreSQL blocks a delete due to an FK.
- Database acceptance of a delete follows the configured FK action; inspect the database schema when validating dependent records.

