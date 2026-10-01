# Research: users-api

**Feature**: `010-users-api` | **Date**: 2026-10-01

## R1. Reuse the auth-api user persistence

- **Decision**: Reuse the existing `com.wikigerminare.users.User` entity and `UserRepository`; use `findById(UUID)` and `save(User)` for profile operations. Add only profile mutation behavior to the existing entity as needed.
- **Rationale**: Auth-api already maps the V1 `users` table in this package. V1 includes all fields required for profile behavior, and a second entity/repository would create duplicate mappings of the same account.
- **Alternatives considered**:
  - Add a separate profile entity or table: rejected because schema already contains `name`, `avatar_url`, and `bio`, and there is one account identity.
  - Add a second user repository: rejected because `UserRepository` already provides the required persistence operations.

## R2. No database migration

- **Decision**: Do not add a migration.
- **Rationale**: V1 defines `id UUID`, required `name VARCHAR(150)`, `email VARCHAR(255)`, internal `password_hash TEXT`, nullable `avatar_url TEXT` and `bio TEXT`, role, and timestamps. These columns already support profile reads and updates.
- **Alternatives considered**:
  - Add profile-specific columns or a profile table: rejected because no specified profile field is absent from V1.
  - Alter V1: rejected because it is an applied initial migration and not necessary for the contract.

## R3. Reuse the authenticated-user integration contract

- **Decision**: `UserService` uses the existing `AuthenticatedUserProvider.currentUser()` and reads only `AuthenticatedUser.id()` for `/me`.
- **Rationale**: This is the shared auth identity port already used by comments-api; `SecurityAuthenticatedUserProvider` obtains the UUID from the authenticated Spring Security name and rejects missing/malformed identity. It avoids trusting a request UUID and avoids adding another authentication abstraction.
- **Alternatives considered**:
  - Accept user UUID as a path or body value for self operations: rejected because it allows the client to select the mutated account.
  - Add a new users-specific principal/provider abstraction: rejected as duplicate identity infrastructure.
  - Inject `Principal` into the new controller: although other controllers read UUID from `Principal`, the existing shared provider supplies the typed UUID and is the established integration port for service-level identity-aware business rules; using it centralizes self-target selection without adding a new abstraction.

## R4. Separate response DTOs as privacy allowlists

- **Decision**: Define a private own-profile response (`id`, `name`, `email`, `avatarUrl`, `bio`) and a public response (`id`, `name`, `avatarUrl`, `bio`). Map manually from `User`; never serialize the entity.
- **Rationale**: The spec explicitly permits email only for the user's own profile and forbids public role/credentials/internal metadata. Separate DTO types make this data boundary explicit and reduce accidental disclosure.
- **Alternatives considered**:
  - One broad user DTO with conditional field suppression: rejected because it makes privacy depend on runtime serialization behavior and risks exposing sensitive fields.
  - Return `User` with ignored properties: rejected because the entity includes credential and role getters and is persistence/authentication state, not an API contract.

## R5. Preserve PATCH omission versus explicit null

- **Decision**: Use presence flags recorded by Jackson setters, consistent with `folders.dto.UpdateFolderRequest`. Omitted values are untouched; explicit null clears nullable `avatarUrl` and `bio`; null `name` is invalid.
- **Rationale**: Ordinary nullable fields cannot distinguish omitted values from explicit JSON null, but the v1 contract requires both preserve and clear semantics.
- **Alternatives considered**:
  - Treat null and omission identically: rejected because clients could not clear optional profile fields.
  - Replace the request model with a JSON merge-patch library: rejected as unnecessary dependency/abstraction given existing repository precedent.

## R6. Reject prohibited/unknown PATCH fields locally

- **Decision**: The PATCH DTO contract allows only `name`, `avatarUrl`, and `bio`. It records unknown property names with DTO-local handling (such as a Jackson any-setter), and the service rejects the request before changing the entity. Do not change global JSON handling.
- **Rationale**: The application's default unknown-property behavior may ignore unrecognized JSON keys. Local capture and rejection enforces the specification's rejection of ID, email, role, credential and metadata changes while preventing accidental acceptance of future or misspelled keys.
- **Alternatives considered**:
  - Silently ignore unknown properties: rejected because a client-supplied `id`/`role` would appear accepted despite having no legitimate update effect, and specification requires rejection.
  - Change application-wide Jackson configuration: rejected because that would alter unrelated endpoints.

## R7. Keep route authorization in existing auth middleware

- **Decision**: Add the users endpoints without changing `SecurityConfig`; its existing `.anyRequest().authenticated()` rule protects them. Keep public-profile reads authenticated.
- **Rationale**: The spec says “public” describes the profile's visible fields for authorized consumers and does not authorize anonymous access. The existing auth-api already enforces authentication for these routes.
- **Alternatives considered**:
  - Permit anonymous access to `/api/users/{userId}`: rejected because it changes the application's access policy and is not required by the specification.
  - Add users-specific roles or RBAC: rejected as out of scope.

## R8. Error handling and tests follow local conventions

- **Decision**: Add users-scoped not-found/validation errors and a users-controller-scoped exception handler using the existing `{"error": ...}` shape. Test service rules with JUnit/Mockito, request/response behavior with MockMvc, and persisted PostgreSQL behavior through existing Testcontainers setup.
- **Rationale**: Folders and pages already use feature-scoped services, exception classes, DTOs and `{"error":...}` handlers. The repository has Spring MVC test support and Testcontainers PostgreSQL with the real V1 migration.
- **Alternatives considered**:
  - Change global exception handling for all endpoints: rejected to avoid unrelated behavior changes.
  - Use only H2 for persistence verification: rejected for database-facing mapping because the project guidance prefers real PostgreSQL when Testcontainers is configured.

## Repository findings

- `auth-api` is implemented. `User` and `UserRepository` live in `com.wikigerminare.users`; the repository inherits `findById` and `save`, and auth uses `findByEmail`.
- `AuthenticatedUserProvider` returns `AuthenticatedUser(UUID id, boolean isAdmin)`. No new auth contract is needed; the users feature uses only `id`.
- `SecurityConfig` requires authentication for all routes other than explicit health/docs/login/image exceptions. `/api/users/**` is protected already.
- `UpdateFolderRequest` uses `@JsonSetter`, `Nulls.SET`, and explicit `provided` flags to distinguish omission from JSON null.
- Existing folder/page services implement domain validation and DTO conversion; controllers delegate and use feature-specific HTTP error handlers.
- Existing integration tests use `@SpringBootTest`, MockMvc, `JdbcTemplate`, Testcontainers PostgreSQL, and JWT issuance for real authenticated requests.
- `pom.xml` already contains Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security, Testcontainers PostgreSQL and the Spring test starters. No dependency addition is indicated.
