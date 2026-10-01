# Quickstart: users-api

## Prerequisites

- JDK 21 and Docker available for the existing Testcontainers PostgreSQL setup.
- Repository dependencies available through the Maven wrapper.
- No production database connection is used by the local test suite.

## Run focused tests

```powershell
.\mvnw.cmd test "-Dtest=UserServiceTest,UserControllerTest"
.\mvnw.cmd test "-Dtest=UserOwnProfilePostgresIntegrationTest,UserProfileUpdatePostgresIntegrationTest,UserPublicProfilePostgresIntegrationTest"
```

The first selector runs unit and MockMvc tests without Docker. The second runs the
PostgreSQL-backed scenarios and requires a working Docker daemon for Testcontainers.
Run the selectors separately so the Docker requirement is explicit.

Run all JVM tests after focused validation:

```powershell
.\mvnw.cmd test
```

## Acceptance checks

1. `GET /api/users/me` with a valid bearer token returns the account bound to the token UUID, including `id`, `name`, `email`, `avatarUrl`, and `bio`.
2. `GET /api/users/me` without a bearer token returns `401`; a token UUID without a matching row returns `404`.
3. `PATCH /api/users/me` changes only the authenticated account's supplied profile fields. Omitted fields remain unchanged; explicit `null` clears `avatarUrl` or `bio`.
4. Empty PATCH bodies, blank/null/overlong names, unknown properties and attempts to send identity, email, role, credentials or metadata return `400` without changing stored values.
5. `GET /api/users/{userId}` with an authorized bearer token returns only `id`, `name`, `avatarUrl`, and `bio`. The response contains neither email, role, password/hash nor timestamps, including for admin accounts.
6. A valid but unknown UUID returns `404`; a malformed UUID returns `400`.
7. PostgreSQL-backed checks use the real V1 schema, confirm profile changes persist, confirm `email`, `password_hash`, and `role` remain unchanged, and verify the V1 `updated_at` trigger remains functional without adding a migration.

Detailed field visibility, null semantics and response schemas are in [data-model.md](data-model.md) and [contracts/users-api.openapi.yaml](contracts/users-api.openapi.yaml).
