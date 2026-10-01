# Tasks: Auth API

**Input**: Design documents from `/specs/009-auth-api/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/auth-api.yaml`, `quickstart.md`

**Organization**: Tasks follow the requested implementation stages. Story labels map
to `spec.md`: US1 login, US2 authenticated identity, US3 admin/member role.

## Phase 1: Setup e dependências

**Purpose**: Add Spring Security's managed JWT runtime support without unrelated build
or infrastructure changes.

- [ ] T001 Add `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` to `pom.xml`; use Spring Boot 4.1.1 dependency management and do not add a separate JWT library or explicit version.

## Phase 2: Fundação de usuário/autenticação

**Purpose**: Map and read existing V1 user data, with no new table or migration.

- [ ] T002 [P] Create `UserRole` in `src/main/java/com/wikigerminare/users/UserRole.java` with database labels exactly `admin` and `member`, and `fromDatabaseValue(String)` that rejects unknown labels.
- [ ] T003 Create the shared `User` persistence mapping in `src/main/java/com/wikigerminare/users/User.java` for existing V1 table `users`; map `id UUID`, `name VARCHAR(150) NOT NULL`, `email VARCHAR(255) NOT NULL UNIQUE`, `password_hash TEXT NOT NULL`, `role user_role NOT NULL DEFAULT 'member'`, nullable `avatar_url` and `bio`, and required `created_at` / `updated_at`; keep it internal and do not change migrations.
- [ ] T004 Create read-only `UserRepository` in `src/main/java/com/wikigerminare/users/UserRepository.java` with exact email lookup only; do not add user CRUD or another User model.
- [ ] T005 [P] Add PostgreSQL mapping tests in `src/test/java/com/wikigerminare/users/UserRepositoryPostgresTest.java` verifying exact email lookup and conversion of V1 `admin` / `member` enum labels using the existing PostgreSQL Testcontainers setup; do not add a schema migration.
- [ ] T006 Add `UserDetailsService` adapter in `src/main/java/com/wikigerminare/auth/security/DatabaseUserDetailsService.java` that loads the shared user via `UserRepository`, treats an unknown role as invalid, and never logs or exposes `password_hash`.
- [ ] T007 Add the Spring Security `UserDetails` representation in `src/main/java/com/wikigerminare/auth/security/AuthenticatedUserDetails.java`; retain the encoded password only for credential verification and do not make this an HTTP response type.
- [ ] T008 [P] Add the password encoder and DAO authentication provider configuration in `src/main/java/com/wikigerminare/auth/security/AuthenticationConfiguration.java`; configure BCrypt verification and the repository-backed `UserDetailsService`, without adding registration or password-management behavior.

## Phase 3: Login

**Purpose**: Define the login request/response contract and test invalid input and
credential failure behavior.

**User story**: US1 — Entrar com email e senha (P1).

**Independent Test**: The login contract accepts valid email/password input, rejects
missing or malformed input with 400, and never exposes credentials.

- [ ] T009 [P] [US1] Add MVC contract tests in `src/test/java/com/wikigerminare/auth/AuthControllerTest.java` for required email/password, valid email, maximum email length 255, malformed input returning 400, exact success DTO fields and no echoed credentials.
- [ ] T010 [P] [US1] Add `LoginRequest` in `src/main/java/com/wikigerminare/auth/dto/LoginRequest.java` with required valid email (maximum 255 characters) and non-empty password validation; do not include password in `toString` or responses.
- [ ] T011 [P] [US1] Add `LoginResponse` in `src/main/java/com/wikigerminare/auth/dto/LoginResponse.java` containing only `accessToken`, `tokenType` (`Bearer`) and `expiresAt`.

## Phase 4: Emissão e validação de JWT

**Purpose**: Implement signed token claims and authentication orchestration using
Spring Security's managed JOSE support.

**User stories**: US1 token issuance; US2 token validation/identity; US3 role claim.

- [ ] T012 [P] [US1] Add service tests in `src/test/java/com/wikigerminare/auth/AuthServiceTest.java` for valid credentials, unknown email and wrong password; assert unknown email and incorrect password yield identical generic failure and no token.
- [ ] T013 [P] [US2] Add `JwtTokenServiceTest` in `src/test/java/com/wikigerminare/auth/JwtTokenServiceTest.java` asserting HS256 signature, canonical UUID `sub`, exact persisted role, `iat`, `exp`, stable `iss`, and no email/password/hash/profile claims.
- [ ] T014 [P] [US2] Add JWT validation tests in `src/test/java/com/wikigerminare/auth/JwtConfigurationTest.java` for valid, expired, tampered-signature, unsupported-algorithm, malformed UUID subject, unknown role, and invalid/missing expiry tokens.
- [ ] T015 Add validated JWT properties in `src/main/java/com/wikigerminare/auth/security/AuthJwtProperties.java` and bind them in `src/main/resources/application.properties`; require `APP_AUTH_JWT_SECRET_BASE64` decoding to at least 32 random bytes and `APP_AUTH_JWT_TTL_SECONDS` from 1 through 3600, with no hard-coded or committed default.
- [ ] T016 [US2] Implement `JwtTokenService` in `src/main/java/com/wikigerminare/auth/security/JwtTokenService.java` using Spring Security `JwtEncoder` / `NimbusJwtEncoder` and HS256; issue only `sub` (canonical UUID), `iss`, `iat`, `exp` and exact lowercase `role`, with 900 seconds as the recommended TTL.
- [ ] T017 [US2] Configure a pinned-HS256 `JwtDecoder` and timestamp/issuer validation in `src/main/java/com/wikigerminare/auth/security/JwtConfiguration.java`; reject invalid signatures, expired tokens, malformed subjects and unknown roles without trusting a token-selected algorithm.
- [ ] T018 [US1] Implement `AuthService` in `src/main/java/com/wikigerminare/auth/AuthService.java` to authenticate through Spring Security's `AuthenticationManager`, issue a JWT only after successful credential verification, and return the `LoginResponse` DTO.
- [ ] T019 [US1] Implement scoped error handling in `src/main/java/com/wikigerminare/auth/AuthExceptionHandler.java`: malformed/invalid requests return 400; unknown account and wrong password return the exact same 401 body `{"error":"Invalid email or password"}` without account, password or hash details.

## Phase 5: Integração com Spring Security

**Purpose**: Add the login route and JWT bearer authentication to the existing
stateless filter chain while preserving current route behavior and identity adapters.

**User stories**: US2 authenticated UUID; US3 admin/member distinction.

- [ ] T020 [P] [US3] Add `JwtRoleAuthenticationConverterTest` in `src/test/java/com/wikigerminare/auth/security/JwtRoleAuthenticationConverterTest.java` proving `admin` maps to `ROLE_ADMIN`, `member` does not receive it, and missing/unknown role claims fail closed.
- [ ] T021 [US3] Implement `JwtRoleAuthenticationConverter` in `src/main/java/com/wikigerminare/auth/security/JwtRoleAuthenticationConverter.java`; map `admin` to `ROLE_ADMIN`, `member` to `ROLE_MEMBER`, preserve JWT `sub` as principal name, and reject all other roles.
- [ ] T022 [P] [US2] Add security-chain tests in `src/test/java/com/wikigerminare/config/SecurityConfigTest.java` verifying no token, malformed token and expired token yield 401 on a protected route, a valid token authenticates, and `/health` plus `GET /api/images/{id}` remain public.
- [ ] T023 [US1] Implement `AuthController` in `src/main/java/com/wikigerminare/auth/AuthController.java` with `POST /api/auth/login`, `LoginRequest` Bean Validation and `200 OK`; serialize only the `LoginResponse` DTO.
- [ ] T024 [US2] Extend the existing filter chain in `src/main/java/com/wikigerminare/config/SecurityConfig.java` to permit `POST /api/auth/login`, configure `oauth2ResourceServer().jwt()` with the validated decoder and role converter, and preserve all other public matchers, stateless sessions, disabled request cache and the 401 entry point.
- [ ] T025 [US2] Verify `src/main/java/com/wikigerminare/config/SecurityAuthenticatedUserProvider.java` receives canonical UUID from `Authentication.getName()` and `ROLE_ADMIN` from JWT authorities; make only a documented compatibility adjustment if necessary, and leave `src/main/java/com/wikigerminare/integration/AuthenticatedUser.java` and `src/main/java/com/wikigerminare/integration/AuthenticatedUserProvider.java` unchanged.

## Phase 6: Testes de integração e regressão

**Purpose**: Prove login-to-request behavior against PostgreSQL, Spring Security and
the actual Lambda adapter.

- [ ] T026 [US1] Add PostgreSQL login integration tests in `src/test/java/com/wikigerminare/auth/AuthIntegrationTest.java` using the existing Flyway V1 schema and Testcontainers; seed a BCrypt-hashed account and test valid credentials, unknown user and incorrect password with identical generic 401 responses for both failure cases.
- [ ] T027 [US2] Add protected-route integration tests in `src/test/java/com/wikigerminare/auth/AuthSecurityIntegrationTest.java` using the real filter chain; assert valid JWT principal name is the canonical UUID, absent/expired/altered tokens receive 401, and `/health` plus `GET /api/images/{id}` remain public.
- [ ] T028 [US2] Extend `src/test/java/com/wikigerminare/lambda/LambdaSecurityTest.java` to pass a signed Bearer token through the real HTTP API v2 adapter and verify protected requests do not require a session or return 502; retain anonymous and public-route assertions.
- [ ] T029 Run auth-focused tests and the complete suite using `.\mvnw.cmd test`; fix auth-caused regressions without modifying unrelated APIs or weakening existing assertions.

## Phase 7: Documentação e validação

**Purpose**: Document environment dependencies and verify that the final change set
does not cross the feature boundary.

- [ ] T030 [P] Update `README.md` with local JWT variables, safe local signing-key generation guidance and the prerequisite for pre-provisioned BCrypt user hashes; do not include secret values.
- [ ] T031 Update `specs/009-auth-api/quickstart.md` with final runnable test names and acceptance checks for valid/invalid credentials, unknown user, wrong password, valid/expired/tampered JWT, UUID identity, admin/member roles, protected 401 and preserved public endpoints.
- [ ] T032 Validate `specs/009-auth-api/contracts/auth-api.yaml`, `specs/009-auth-api/data-model.md`, `specs/009-auth-api/research.md` and `specs/009-auth-api/plan.md` against implementation; retain documentation of `APP_AUTH_JWT_SECRET_BASE64` and `APP_AUTH_JWT_TTL_SECONDS`, and do not edit `template.yaml` or deployment workflows.
- [ ] T033 Confirm no migration was added for `users` and run `git diff --check`; verify source/test modifications stay within auth, shared users/security integration and tests, with no unrelated changes to comments-api, pages-api, folders-api, images-api or search-api.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 adds the managed JWT dependency.
- **Foundation (Phase 2)**: follows T001; T003 depends on T002, and T004/T005 depend
  on the shared user mapping.
- **Login contract (Phase 3)**: follows the shared auth foundation; request/response
  DTO tests and types can be prepared independently.
- **JWT and authentication (Phase 4)**: follows user/auth foundation; T018 depends on
  `AuthenticationManager` (T008) and token issuance (T016).
- **Spring Security integration (Phase 5)**: follows JWT decoder/converter (T017/T021)
  and controller/service (T018/T023); preserve SecurityConfig's established matchers.
- **Integration tests (Phase 6)**: follow the complete login and filter-chain flow.
- **Documentation (Phase 7)**: follows the tests and final implementation behavior.

### User Story Dependencies

- **US1 (P1)**: user repository, password authentication, login DTOs/controller, token
  issuance. Independent check: valid BCrypt credentials return the login DTO; unknown
  account and wrong password have identical generic 401 response.
- **US2 (P1)**: depends on issued-token decoder and SecurityConfig integration.
  Independent check: valid JWT authenticates a protected route with canonical UUID
  principal name; missing, expired and altered tokens receive 401.
- **US3 (P2)**: depends on shared role mapping and JWT converter. Independent check:
  only persisted `admin` maps to `ROLE_ADMIN` / `isAdmin=true`; `member` does not.

### Parallel Opportunities

- T002 and T005 work on distinct files and may proceed independently after setup.
- T009–T011 can proceed in parallel across test and DTO files.
- T012–T014 can proceed in parallel across independent test classes.
- T020 and T022 can proceed in parallel across independent test files.
- T030 may proceed in parallel with the final quickstart update once configuration
  names are fixed.
- Do not parallelize edits to `SecurityConfig.java`, `AuthJwtProperties.java`,
  `JwtConfiguration.java` or `LambdaSecurityTest.java`.

## Parallel Example

```text
Task: T012 Add AuthServiceTest.java
Task: T013 Add JwtTokenServiceTest.java
Task: T014 Add JwtConfigurationTest.java
```

## Implementation Strategy

1. Complete setup and the shared user/authentication foundation (Phases 1–2).
2. Define and test the login DTOs (Phase 3).
3. Implement signing, credential authentication and JWT validation (Phase 4).
4. Wire the login controller and Resource Server into the existing filter chain
   (Phase 5).
5. Validate US1 login, then US2 identity/security, then US3 roles using the real
   PostgreSQL schema and Lambda adapter (Phase 6).
6. Update documentation and check the scoped diff (Phase 7).

## Notes

- Every task uses the required checkbox, sequential Task ID, optional `[P]`, story
  marker for story-specific work, and exact repository-relative paths.
- No task creates another User entity for this table, a `users` migration, a second
  `AuthenticatedUserProvider`, user CRUD, profile, general RBAC or token renewal.
- Production secret injection remains owned by environment infrastructure; this plan
  does not edit `template.yaml` or release workflows.
