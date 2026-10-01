# Implementation Plan: users-api

**Branch**: `010-users-api` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-users-api/spec.md`

## Summary

Add authenticated self-profile retrieval and partial update, plus authenticated public-profile lookup by UUID. Reuse the existing `com.wikigerminare.users.User` entity and `UserRepository` established by auth-api, use the existing `AuthenticatedUserProvider` to obtain the authenticated UUID, and map the same persisted user through distinct private and public response DTOs. The current V1 `users` table already contains all profile fields; no migration or additional authentication/RBAC abstraction is required.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Bean Validation, existing Spring Security JWT resource-server configuration

**Storage**: Existing PostgreSQL `users` table from V1 migration

**Testing**: Maven; JUnit 5, Mockito, MockMvc, Spring Boot integration tests, Testcontainers PostgreSQL

**Target Platform**: Existing GerminaWiki HTTP API running locally and on AWS Lambda

**Project Type**: Layered REST backend service

**Performance Goals**: At least 95% of valid profile reads and updates complete within 2 seconds under normal use, as specified by SC-006

**Constraints**: Preserve Controller -> Service -> Repository separation; use DTOs for HTTP input/output; do not add a second User entity or authentication abstraction; no new migration when V1 schema suffices; never serialize credentials, role, or internal metadata; the existing security filter chain requires authentication for all routes except its explicit public allowlist

**Scale/Scope**: Three operations over existing user accounts: own-profile GET, own-profile PATCH, and public-profile GET by UUID; editable fields are `name`, `avatarUrl`, and `bio`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / constraint | Status | Evidence |
|---|---|---|
| Specification is the source of truth | PASS | The implementation is constrained to the three operations and field visibility/editability contract in `spec.md`. |
| Layered architecture | PASS | `UserController` handles HTTP, `UserService` owns profile use cases and mapping, and the existing `UserRepository` performs persistence. |
| REST contracts and DTOs | PASS | The three endpoints use dedicated input/output DTOs, validation and specified HTTP outcomes. |
| Automated tests | PASS | Unit, MVC, and PostgreSQL-backed integration coverage are planned for business rules, privacy, identity and persistence. |
| Simplicity and consistency | PASS | Reuse existing user persistence, auth identity contract, Spring dependencies, error conventions and PostgreSQL schema. |
| Java 21 / Spring Boot / PostgreSQL | PASS | Matches `pom.xml`, README and the project Constitution. |
| No unrelated or duplicate security work | PASS | No auth-api, JWT, RBAC, account lifecycle or global security behavior changes are in scope. |

No gate violations identified.

## Project Structure

### Documentation (this feature)

```text
specs/010-users-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── users-api.openapi.yaml
```

### Source Code (repository root)

```text
src/main/java/com/wikigerminare/
└── users/
    ├── User.java                         # Existing entity; add profile-only mutation capability
    ├── UserRepository.java               # Existing repository; reuse findById/save
    ├── UserService.java                  # Self/public lookups, update rules and DTO mapping
    ├── UserController.java               # GET/PATCH /me and GET /{userId}
    ├── UserNotFoundException.java
    ├── UserValidationException.java
    ├── UserExceptionHandler.java
    └── dto/
        ├── OwnUserProfileResponse.java   # id, name, email, avatarUrl, bio
        ├── PublicUserProfileResponse.java # id, name, avatarUrl, bio
        └── UpdateUserProfileRequest.java # name, avatarUrl, bio with presence tracking

src/test/java/com/wikigerminare/
└── users/
    ├── UserServiceTest.java
    ├── UserControllerTest.java
    └── UserApiPostgresIntegrationTest.java
```

**Structure Decision**: Keep feature behavior and DTOs in the existing `com.wikigerminare.users` domain, which already owns the single `User` entity and repository used by auth-api. Do not create a parallel profile entity, user repository, or security package. Use a users-scoped exception handler so profile errors do not alter unrelated API error handling.

## Design Decisions

### Persistence and identity

- Reuse `User` and `UserRepository`; Spring Data's existing `findById(UUID)` and `save(User)` cover all three use cases.
- Add only profile-field mutation capability to the existing entity (`name`, `avatarUrl`, `bio`). Do not change account ID, email, password hash, role, or timestamps through the request DTO.
- `UserService` obtains the acting UUID from the existing `AuthenticatedUserProvider.currentUser().id()`. It never accepts a target user ID for either `/me` operation. Do not introduce a new identity provider or use a client-supplied UUID.
- Keep `/api/users/me` protected by the existing `SecurityConfig` `.anyRequest().authenticated()` rule. Do not modify the security allowlist, JWT, or auth-api.
- `GET /api/users/{userId}` is available to an authenticated/authorized system consumer according to existing security policy; “public profile” means public field visibility and does not make the route anonymous.

### Profile fields and DTO boundaries

| Surface | Fields allowed | Excluded fields |
|---|---|---|
| Own profile response (`GET /me` and successful PATCH response) | `id`, `name`, `email`, `avatarUrl`, `bio` | `passwordHash`, `role`, credentials/authentication data, `createdAt`, `updatedAt`, all other internal metadata |
| Public profile response (`GET /{userId}`) | `id`, `name`, `avatarUrl`, `bio` | `email`, `passwordHash`, `role`, credentials/authentication data, timestamps and all other internal metadata |
| Update request (`PATCH /me`) | `name`, `avatarUrl`, `bio` | `id`, `email`, `passwordHash`, `role`, credential/auth fields, timestamps, and all unknown properties |

Use explicit response DTO mapping; never return/serialize `User` or an auth details object. The successful update returns the own-profile DTO, including the non-editable email as prescribed for the private self view. Email remains immutable through this API; no email-change behavior is introduced.

### PATCH and validation

- Use a mutable request DTO with per-property presence flags, following `UpdateFolderRequest`: Jackson setters record that a key was present even when its JSON value is `null`.
- Omitted fields leave persisted values unchanged. Explicit `null` clears only `avatarUrl` and `bio`; explicit `null` for `name` is invalid.
- Reject empty PATCH bodies, blank names and names longer than 150 characters. Validate request fields before mutating or saving the entity; invalid requests must not partially update any fields.
- `avatarUrl` and `bio` are nullable PostgreSQL `TEXT`; do not invent maximum lengths or URL-format restrictions absent from the specification/schema.
- Reject unknown JSON properties, including attempts to supply account identity, email, credentials, role, or metadata. Because the application's default JSON handling may ignore unknown properties, the update DTO must collect unknown keys with endpoint-local handling (for example, an any-setter) and reject the request before entity mutation. Do not change global Jackson behavior.

### Errors and HTTP behavior

- Missing/invalid bearer identity is rejected by the existing security filter chain with `401`; service-level missing/invalid current-user identity must not fall through to another user.
- Missing account for authenticated UUID or public UUID maps to `404`.
- Empty PATCH, invalid profile values, malformed UUID conversion and forbidden/unknown request properties map to `400`.
- Success responses use `200`; null optional profile values are serialized as JSON `null`.
- Follow the feature-local `{"error":"..."}` response convention seen in folders/pages. The users exception handler handles users exceptions and request validation/conversion failures only; it does not change shared/global handlers.

## Phase 0: Research

See [research.md](research.md). All relevant implementation and schema questions were resolved from repository sources; no open `NEEDS CLARIFICATION` items remain.

## Phase 1: Design & Contracts

- [Data model](data-model.md) describes the existing account fields and the three explicit DTO boundaries.
- [OpenAPI contract](contracts/users-api.openapi.yaml) specifies routes, security, request/response schemas and primary error statuses.
- [Quickstart](quickstart.md) describes local PostgreSQL-backed validation scenarios using repository test commands and existing test infrastructure.

### Post-design Constitution Check

| Principle / constraint | Status | Design evidence |
|---|---|---|
| Specification governs behavior | PASS | Endpoint outcomes, fields and editability match `spec.md`; email is visible only in self responses and is not editable. |
| Layered architecture | PASS | Controller delegates to service; service applies identity, validation, update and response mapping; repository handles persistence. |
| DTO and REST contract | PASS | Separate own/public response DTOs and dedicated PATCH request DTO; OpenAPI contract captures all exposed shapes. |
| Security and privacy | PASS | Existing auth identity is reused; explicit allowlisted response fields prevent role/credential leakage; route remains authenticated. |
| Existing schema and shared User | PASS | Uses V1 `users`; no migration or second entity/repository. |
| Testability | PASS | Unit, MockMvc and PostgreSQL Testcontainers cases cover business, serialization, identity, and persistence behavior. |
| Scope and simplicity | PASS | No authentication, RBAC, account administration, or unrelated changes; no new library or global security/Jackson changes. |

**Gate result**: PASS. Ready for task generation.

## Complexity Tracking

No Constitution violations; no exceptions require justification.
