---
description: "Implementation task list for users-api"
---

# Tasks: users-api

**Input**: Design documents from `/specs/010-users-api/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/users-api.openapi.yaml`, `quickstart.md`

**Tests**: Required by the feature specification and user request. Add the tests before implementation tasks and validate with the existing Maven/JUnit/MockMvc/Testcontainers PostgreSQL stack.

**Organization**: Tasks are grouped by the specification's user stories. The existing auth-api, JWT configuration and security policy are dependencies only and must not be changed.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel with other marked tasks because it edits different files and has no incomplete dependencies.
- **[Story]**: User story from `spec.md` (`US1`, `US2`, `US3`).
- Every task names its exact file path(s).

## Path Conventions

- Java production sources: `src/main/java/com/wikigerminare/users/`
- Java tests: `src/test/java/com/wikigerminare/users/`
- Feature documentation: `specs/010-users-api/`

## Phase 1: Setup

**Purpose**: Confirm existing project infrastructure can support the feature without unrelated setup changes.

- [X] T001 Confirm existing Maven dependencies, shared `User`/`UserRepository`, authenticated-user provider and PostgreSQL Testcontainers are reused; do not add dependencies, a second User model/repository, a migration, authentication or RBAC changes in `pom.xml`, `src/main/java/com/wikigerminare/users/User.java`, `src/main/java/com/wikigerminare/users/UserRepository.java`, `src/main/java/com/wikigerminare/config/SecurityConfig.java`, or `src/main/resources/db/migration/`.

## Phase 2: Foundational

**Purpose**: Establish users-scoped error and validation responses required by all profile operations.

- [X] T002 Implement users-scoped not-found and profile-validation exceptions plus `UserExceptionHandler` in `src/main/java/com/wikigerminare/users/UserNotFoundException.java`, `src/main/java/com/wikigerminare/users/UserValidationException.java`, and `src/main/java/com/wikigerminare/users/UserExceptionHandler.java`; map missing users to 404, invalid PATCH/path input to 400, and preserve the feature-local `{"error":"..."}` shape without changing shared error handling.

**Checkpoint**: Existing persistence, identity and authentication infrastructure remains unchanged; user-story implementation can begin.

## Phase 3: User Story 1 - Consultar o próprio perfil (Priority: P1) 🎯 MVP

**Goal**: Return only the authenticated account's own profile, with the private email included but no role, credentials or internal metadata.

**Independent Test**: Authenticate as a provisioned account, call `GET /api/users/me`, verify the response maps that token's UUID and own-profile allowlist; unauthenticated or unmatched identity requests cannot expose another account.

### Tests for User Story 1

- [X] T003 [P] [US1] Add `UserServiceTest` cases in `src/test/java/com/wikigerminare/users/UserServiceTest.java` for own-profile lookup using `AuthenticatedUserProvider.currentUser().id()`, expected private fields, missing authenticated account, and proof that the repository is queried only with the authenticated UUID.
- [X] T004 [P] [US1] Add MockMvc coverage in `src/test/java/com/wikigerminare/users/UserControllerTest.java` for `GET /api/users/me`, 200 response field allowlist, no password/hash/role/internal metadata, and 404 propagation for a missing account.
- [X] T005 [P] [US1] Add authenticated PostgreSQL-backed scenarios in `src/test/java/com/wikigerminare/users/UserOwnProfilePostgresIntegrationTest.java` using the existing Testcontainers configuration and auth-api-issued bearer token; verify the correct user's `id`, email and profile are returned, `password_hash` is absent from JSON, no token returns 401, and a valid token whose UUID has no row returns 404.

### Implementation for User Story 1

- [X] T006 [P] [US1] Create `OwnUserProfileResponse` in `src/main/java/com/wikigerminare/users/dto/OwnUserProfileResponse.java` with exactly `id`, `name`, `email`, `avatarUrl`, and `bio`; do not include role, credentials, or timestamps.
- [X] T007 [US1] Implement `UserService` own-profile retrieval in `src/main/java/com/wikigerminare/users/UserService.java`; inject the existing `UserRepository` and `AuthenticatedUserProvider`, resolve the target exclusively from the authenticated UUID, return 404 when that account is absent, and explicitly map the allowlisted own-profile response without serializing `User`.
- [X] T008 [US1] Implement `GET /api/users/me` in `src/main/java/com/wikigerminare/users/UserController.java`, delegate to `UserService`, and return 200 with `OwnUserProfileResponse`; do not accept a user UUID from the request.

**Checkpoint**: Own-profile GET is independently functional and protected by the existing authentication filter.

## Phase 4: User Story 2 - Atualizar o próprio perfil (Priority: P1)

**Goal**: Allow authenticated users to partially update only their own profile fields, preserving omission/null semantics and rejecting attempts to alter identity or private account state.

**Independent Test**: Authenticate as one account, PATCH profile fields and verify persistence changes only that account's submitted fields; omitted values stay unchanged, explicit null clears nullable fields, and invalid/disallowed input causes no changes.

### Tests for User Story 2

- [X] T009 [P] [US2] Extend `src/test/java/com/wikigerminare/users/UserServiceTest.java` with PATCH cases for updating the provider's authenticated UUID only, preserving omitted fields, clearing nullable `avatarUrl`/`bio` when explicitly null, rejecting empty updates and null/blank/overlong `name`, and never saving after invalid input.
- [X] T010 [P] [US2] Extend `src/test/java/com/wikigerminare/users/UserControllerTest.java` with `PATCH /api/users/me` cases for successful 200 response, omitted-versus-explicit-null field presence, empty request, invalid name, unknown/prohibited identity/email/role/credential properties returning 400, and no service call for request-validation failures.
- [X] T011 [P] [US2] Add authenticated PostgreSQL persistence scenarios in `src/test/java/com/wikigerminare/users/UserProfileUpdatePostgresIntegrationTest.java` for updating the caller's row, proving a client-supplied other-user UUID cannot redirect the update, omitted fields remain unchanged, explicit null clears optional fields, and `email`, `password_hash`, and `role` remain unchanged while the V1 `updated_at` trigger is respected.

### Implementation for User Story 2

- [X] T012 [P] [US2] Add `UpdateUserProfileRequest` in `src/main/java/com/wikigerminare/users/dto/UpdateUserProfileRequest.java` for only `name`, `avatarUrl`, and `bio`; track presence for every property using Jackson setters as in `src/main/java/com/wikigerminare/folders/dto/UpdateFolderRequest.java`, allow explicit null only for nullable fields, and capture unknown property names locally so they can be rejected without changing global Jackson configuration.
- [X] T013 [US2] Add profile-only mutation methods to the existing entity in `src/main/java/com/wikigerminare/users/User.java`; permit updates only to `name`, `avatarUrl`, and `bio`, and do not add or alter account ID, email, password hash, role, or timestamps through the request.
- [X] T014 [US2] Implement `UserService` profile update in `src/main/java/com/wikigerminare/users/UserService.java`; get the target UUID only from `AuthenticatedUserProvider`, reject unknown request properties and an empty patch, validate `name` as non-null/nonblank and at most 150 characters, preserve omitted attributes, clear `avatarUrl`/`bio` only for explicit null, avoid partial mutation on invalid input, save the existing User, and return the own-profile DTO.
- [X] T015 [US2] Implement `PATCH /api/users/me` in `src/main/java/com/wikigerminare/users/UserController.java` with `@Valid` request binding and a 200 own-profile response; do not add an ID path/body selector, email change, credential change, role change, or authentication behavior.

**Checkpoint**: Own-profile GET and PATCH work independently of public profile lookup; the existing auth-api and shared security policy remain unchanged.

## Phase 5: User Story 3 - Consultar o perfil público de uma pessoa (Priority: P2)

**Goal**: Let an authorized consumer look up a user's limited public profile by UUID without exposing account-private fields.

**Independent Test**: Query an existing and nonexistent UUID under an authorized bearer identity; verify 200/404 outcomes and that the response has exactly the public allowlist for member and admin accounts.

### Tests for User Story 3

- [X] T016 [P] [US3] Add public-profile cases in `src/test/java/com/wikigerminare/users/UserServiceTest.java` for lookup by requested UUID, 404 for absent users, and explicit mapping that excludes email, role, password hash and timestamps.
- [X] T017 [P] [US3] Add MockMvc cases in `src/test/java/com/wikigerminare/users/UserControllerTest.java` for `GET /api/users/{userId}`, exact public JSON fields including null optional values, malformed UUID as 400, and missing user as 404.
- [X] T018 [P] [US3] Add authenticated PostgreSQL-backed cases in `src/test/java/com/wikigerminare/users/UserPublicProfilePostgresIntegrationTest.java` for an authorized member and admin looking up another account, exact public response allowlist, no `password_hash`/email/role exposure, and unknown UUID returning 404.

### Implementation for User Story 3

- [X] T019 [P] [US3] Create `PublicUserProfileResponse` in `src/main/java/com/wikigerminare/users/dto/PublicUserProfileResponse.java` with exactly `id`, `name`, `avatarUrl`, and `bio`; exclude email, role, credentials, and internal metadata.
- [X] T020 [US3] Add public-profile lookup and explicit mapping to `UserService` in `src/main/java/com/wikigerminare/users/UserService.java`; look up only the requested path UUID, return 404 for an absent account, and never return the entity or own-profile DTO.
- [X] T021 [US3] Implement `GET /api/users/{userId}` in `src/main/java/com/wikigerminare/users/UserController.java`, bind UUID path input, delegate to `UserService`, and return 200 with `PublicUserProfileResponse`; do not permit anonymous access or introduce role checks.

**Checkpoint**: All three API operations are available with separate own/public response allowlists and existing authentication enforcement.

## Phase 6: Polish & Cross-Cutting Validation

**Purpose**: Align runnable validation guidance and verify the documented API contract against the complete implementation.

- [X] T022 [P] Update `specs/010-users-api/quickstart.md` with the final test class/method selectors and acceptance commands; cover authenticated `GET /me`, `PATCH /me`, `GET /{userId}`, unauthorized access, correct identity, attempted other-account mutation, public-data privacy, absent-versus-null PATCH, invalid name and missing users.
- [ ] T023 Validate `specs/010-users-api/contracts/users-api.openapi.yaml`, `specs/010-users-api/data-model.md`, `specs/010-users-api/spec.md`, and `specs/010-users-api/quickstart.md` against the implemented route behavior and serialized field allowlists; run the focused users tests and `.\mvnw.cmd test` without changing auth-api, `pom.xml`, `src/main/java/com/wikigerminare/config/SecurityConfig.java`, or database migrations. The full Maven test suite was attempted but PostgreSQL/Testcontainers tests are blocked because this environment has no accessible Docker daemon; rerun when Docker is available.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no implementation or dependency additions; verify existing infrastructure and scope boundaries.
- **Foundational (Phase 2)**: establishes user-scoped not-found/validation outcomes and blocks story work.
- **US1 (Phase 3)**: depends on Phase 2; delivers the MVP own-profile read.
- **US2 (Phase 4)**: depends on US1 because it extends the same `UserService`, `UserController`, and service test files; introduces profile mutation.
- **US3 (Phase 5)**: depends on US2 because it extends the existing users service/controller and test files; adds public profile view.
- **Polish (Phase 6)**: after the three operations are complete.

### User Story Dependencies

- **US1 (P1)**: no dependency on another story; first independently deliverable increment.
- **US2 (P1)**: follows US1 to avoid concurrent edits to shared users service/controller and tests; profile update is independently verifiable once the shared module exists.
- **US3 (P2)**: follows US2 for the same-file integration order; public lookup behavior is independently tested after it is added.

### Within Each User Story

- Story tests are written first, then DTO/entity support, service behavior, and controller route.
- Error mapping and request validation are kept in the users domain and do not alter global handlers.
- PostgreSQL integration tests verify actual V1 persistence, existing auth identity, and the updated-at trigger.
- No story task changes the auth-api, security configuration, shared authentication contracts, or migrations.

### Parallel Opportunities

- US1 test files T003-T005 can be written in parallel; implementation DTO T006 can be developed independently of those tests.
- US2 test files T009-T011 can be written in parallel; DTO T012 can be developed independently of tests.
- US3 test files T016-T018 and response DTO T019 can be developed in parallel.
- Do not parallelize tasks that edit the same `UserService.java`, `UserController.java`, `UserServiceTest.java`, or `UserControllerTest.java`.
- Quickstart T022 can be updated independently after implementation behavior is known; final contract validation T023 depends on all story work.

## Parallel Example: User Story 1

```text
Task: T003 Add own-profile UserService tests in src/test/java/com/wikigerminare/users/UserServiceTest.java
Task: T004 Add GET /me MVC tests in src/test/java/com/wikigerminare/users/UserControllerTest.java
Task: T005 Add authenticated PostgreSQL own-profile tests in src/test/java/com/wikigerminare/users/UserOwnProfilePostgresIntegrationTest.java
Task: T006 Create OwnUserProfileResponse in src/main/java/com/wikigerminare/users/dto/OwnUserProfileResponse.java
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete setup and foundational error mapping.
2. Complete US1 own-profile response DTO, service, controller and tests.
3. Validate `GET /api/users/me` with real bearer authentication and PostgreSQL, including unauthorized and missing-account cases.
4. Stop at the US1 checkpoint for an independently usable profile-read MVP.

### Incremental Delivery

1. Deliver US1 own-profile GET.
2. Add US2 secure own-profile PATCH with explicit omission/null behavior and database persistence checks.
3. Add US3 UUID-based public profile GET with response allowlist privacy tests.
4. Update quickstart and validate all contract artifacts and the full Maven test suite.

## Notes

- No `User` duplicate, migration, dependency, authentication implementation, RBAC, or auth-api change is planned.
- Reuse `AuthenticatedUserProvider.currentUser().id()` for both own-profile operations. The public-profile UUID comes only from the read path.
- Own-profile responses include `email`; public responses do not. Neither response includes `role`, `password_hash`, authentication data, or timestamps.
- Each task uses the required checkbox, sequential task ID, optional parallel/story labels, and exact repository file paths.
