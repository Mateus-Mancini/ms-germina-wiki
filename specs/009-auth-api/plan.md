# Implementation Plan: Auth API

**Branch**: `009-auth-api` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-auth-api/spec.md`

## Summary

Implement email/password login and short-lived JWT access tokens, then validate those
tokens in the existing stateless Spring Security filter chain. Reuse the existing
`users` table as the only user source and add its currently missing shared persistence
model and repository; do not add a migration or a second identity provider. Spring
Security's OAuth2 Resource Server and JOSE modules provide JWT decoding, encoding and
Bearer authentication. The JWT subject is the canonical user UUID, and the persisted
role is converted to the existing `ROLE_ADMIN` authority when appropriate.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data
JPA, `spring-boot-starter-security-oauth2-resource-server` (managed Spring Security
Resource Server/JOSE), Jakarta Bean Validation, Maven

**Storage**: Existing PostgreSQL `users` table from V1; no schema change planned

**Testing**: Maven Wrapper, JUnit 5, Mockito, MockMvc, PostgreSQL Testcontainers,
existing Lambda adapter security test

**Target Platform**: Existing Spring Boot service on AWS Lambda and local JVM

**Project Type**: REST backend, single Spring Boot application

**Performance Goals**: Complete login within the specification's 30-second user
outcome; issue access tokens with a recommended 15-minute lifetime

**Constraints**: Keep `SecurityConfig` stateless and preserve existing public routes;
never serialize `password_hash`; use the existing UUID identity and `ROLE_ADMIN`
contracts; require a strong environment-provided signing key; do not edit
`template.yaml` or implement users, profile, token renewal, revocation or general RBAC

**Scale/Scope**: One login endpoint, shared user persistence model/repository,
authentication service and Spring Security JWT integration; no user CRUD or schema
migration

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / constraint | Status | Evidence |
|---|---|---|
| I. Specification as source of truth | PASS | Design covers only login, token validation, UUID identity and the admin/member distinction defined in `spec.md`. |
| II. Layered architecture | PASS | `AuthController -> AuthService -> UserRepository`; security adapters/configuration remain in the existing `config` boundary. |
| III. REST contracts and DTOs | PASS | Login uses dedicated request/response DTOs documented in `contracts/auth-api.yaml`; error responses are explicit. |
| IV. Automated tests | PASS | Unit, MVC, real PostgreSQL persistence and Lambda adapter coverage are specified in `quickstart.md`. |
| V. Simplicity and maintainability | PASS | Use Spring Security's supported JWT stack and existing project dependencies/patterns; no custom token parser or second authorization system. |
| Java 21 / Spring Boot / PostgreSQL / Maven | PASS | Matches the repository's actual `pom.xml` and requested technical baseline. |
| Existing schema is authoritative | PASS | V1 already defines `users`, `password_hash`, UUID and PostgreSQL `user_role`; no migration is justified. |
| Preserve non-feature behavior | PASS | Keep existing public route matchers and security entry point; add only login as a public route and configure JWT decoding. |
| Production secret infrastructure | PASS WITH BOUNDARY | Document required deployment variables, but leave `template.yaml` and deployment workflows for the environment-infrastructure feature. |

**Gate result**: PASS. No unjustified constitution violations.

## Project Structure

### Documentation (this feature)

```text
specs/009-auth-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/auth-api.yaml
└── tasks.md                         # generated later by /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/com/wikigerminare/
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── AuthExceptionHandler.java
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   └── LoginResponse.java
│   └── security/
│       ├── UserDetailsService adapter
│       ├── UserDetails representation for authentication
│       ├── JWT claim-to-authority converter
│       └── validated JWT configuration/properties
├── users/
│   ├── User.java                    # shared mapping of V1 users table
│   ├── UserRole.java                # ADMIN, MEMBER mapped to user_role
│   └── UserRepository.java          # lookup by exact email
└── config/
    └── SecurityConfig.java          # existing filter chain, extended for JWT

src/main/resources/
├── application.properties           # auth JWT property bindings
├── application-local.properties      # local profile; no committed secret
└── application-lambda.properties     # production profile; values supplied externally

src/test/java/com/wikigerminare/
├── auth/
│   ├── AuthServiceTest.java
│   ├── AuthControllerTest.java
│   ├── JwtTokenServiceTest.java
│   └── AuthIntegrationTest.java      # PostgreSQL + full security chain
└── lambda/
    └── LambdaSecurityTest.java       # extend for Bearer token and route regressions
```

**Structure Decision**: Keep the existing single Spring Boot project and its domain
packages. `users` owns the one shared persistence model because authentication reads
that domain record and other features already reference its UUID; `auth` owns the login
use case, DTOs and JWT-facing adapters. The existing
`integration.AuthenticatedUserProvider` and `AuthenticatedUser` remain unchanged and
are not reimplemented.

## Phase 0: Research Summary

See [research.md](research.md). Decisions:

1. Use the Spring Security OAuth2 Resource Server and JOSE support managed by Spring
   Boot, with `NimbusJwtEncoder` and `NimbusJwtDecoder` for an internally issued,
   symmetric HS256 JWT.
2. Use the existing V1 `users` schema. Add a shared JPA entity and Spring Data
   repository only; do not create a users migration or user CRUD.
3. Authenticate through Spring Security's `AuthenticationManager` and
   `DaoAuthenticationProvider`/`UserDetailsService`, with BCrypt password verification.
4. Put the UUID in JWT `sub`; translate the persisted `admin` role to `ROLE_ADMIN`,
   and map `member` without administrative authority.
5. Require a base64-encoded signing key and token TTL from environment variables in
   local and production runtime; document infrastructure requirements without editing
   SAM templates or release workflows in this feature.

## Phase 1: Design Summary

- [data-model.md](data-model.md) maps the existing `users` table, authentication
  principal, role, token claims and validation invariants.
- [contracts/auth-api.yaml](contracts/auth-api.yaml) defines the login request and
  response, failure behavior and Bearer security scheme.
- [quickstart.md](quickstart.md) specifies local configuration, test commands and
  acceptance checks for login, authenticated identity, roles and preserved public
  endpoints.

### Implementation Sequencing Constraints

1. Map and test the existing `users` schema on PostgreSQL before wiring login; no DDL
   should be introduced unless this verification finds a concrete V1 mismatch.
2. Add validated JWT configuration, encoder/decoder, and password verification before
   exposing the login controller.
3. Add the explicit subject/role conversion and integrate Resource Server JWT
   authentication into the current `SecurityConfig` without changing unrelated route
   matchers.
4. Verify the resulting `Authentication.getName()` and authorities through
   `SecurityAuthenticatedUserProvider` and existing API consumers.
5. Keep local and production environment provisioning as an external dependency. The
   application must fail startup for missing or invalid signing configuration rather
   than use a development or hard-coded fallback.

### Existing API Impact

- `SecurityAuthenticatedUserProvider` remains the sole implementation of the shared
  identity port. It receives the JWT subject as `Authentication.getName()` and sees
  `ROLE_ADMIN` in authorities, so its current UUID parsing and `isAdmin` mapping
  continue to work.
- pages-api and folders-api continue to obtain the UUID through `Principal.getName()`;
  no request DTO may supply or override the creator identity.
- comments-api continues to use `AuthenticatedUserProvider.currentUser()` for UUID
  and `isAdmin`; its service-level authorization is unchanged.
- image-storage continues to use principal UUID and `ROLE_ADMIN`; `GET
  /api/images/{id}` remains public for embedded image loading.
- Health, API docs matchers, stateless sessions, disabled request cache and 401
  authentication entry point remain unchanged. Only `POST /api/auth/login` is added
  to the public matcher list.
- The user table is not changed. User/account provisioning must provide BCrypt hashes
  understood by this feature; creating and maintaining accounts is out of scope.

## Post-Design Constitution Check

- The final design still follows Controller -> Service -> Repository and keeps business
  authentication orchestration in `AuthService`.
- HTTP input/output use DTOs, and `User.passwordHash` is excluded from all response
  types and serialization paths.
- The single existing shared identity provider is reused; JWT-to-authority conversion
  only carries the role and does not implement resource authorization.
- JWT configuration is externally supplied and validated at startup. Production
  infrastructure delivery is explicitly deferred, without edits to `template.yaml`.
- PostgreSQL integration tests validate the existing enum and columns before a schema
  migration can be considered.

**Post-design gate**: PASS.

## Complexity Tracking

No constitution violations or additional architecture modules are introduced.
