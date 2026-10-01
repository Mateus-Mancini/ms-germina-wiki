# Research: Auth API

**Feature**: `009-auth-api` | **Date**: 2026-10-01

## R1. JWT issuance and validation

- **Decision**: Use Spring Security's OAuth2 Resource Server and JOSE modules,
  managed by the Spring Boot 4.1.1 dependency set. Use the Resource Server JWT bearer
  filter for request authentication and Spring Security's `NimbusJwtEncoder` /
  `NimbusJwtDecoder` for signing and validation. Select HMAC SHA-256 (HS256) with one
  application signing key shared across instances. Add the Spring Boot
  `spring-boot-starter-security-oauth2-resource-server` production dependency; its
  managed Spring Security Resource Server/JOSE modules provide the encoder/decoder
  APIs and Nimbus implementation. Spring Boot 4.1.1 marks the older
  `spring-boot-starter-oauth2-resource-server` artifact deprecated in favor of this
  renamed starter.
- **Rationale**: Spring Security has native Resource Server JWT authentication and
  JOSE support, including JWT decoding and Nimbus-backed encoding. A single backend
  both issues and validates tokens, so a local symmetric key avoids a remote identity
  provider, JWK endpoint, key-pair lifecycle and extra runtime services. Spring Boot
  manages the supported dependency versions. The custom work is limited to application
  claims, key configuration and role conversion.
- **Alternatives considered**:
  - **JJWT or Auth0 java-jwt**: rejected because they add a second JWT stack where the
    Spring Security ecosystem already supplies the required encoder/decoder and
    Bearer authentication.
  - **Opaque random tokens with server-side lookup**: rejected because the feature
    explicitly requires JWT and this would introduce token persistence/state.
  - **RSA/EC signing with a JWK endpoint or external issuer**: not selected because
    this application currently issues and validates tokens in one backend and has no
    external consumers or key-distribution service. Revisit if independent services
    need public-key verification.
- **Validation rules**: Pin accepted signature algorithm to HS256; require signed
  tokens with `sub` as a canonical UUID, recognized `role`, `iat` and future `exp`;
  reject malformed or unknown identity/role claims. Use Spring Security's standard
  expiry validation. No user-controlled algorithm selection, `none` algorithm,
  refresh token or revocation store.
- **References**:
  - [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
  - [Spring Boot: Spring Security configuration](https://docs.spring.io/spring-boot/4.1/reference/web/spring-security.html)
  - [Spring Boot: dependency starters](https://docs.spring.io/spring-boot/4.1/reference/using/build-systems.html)

## R2. User persistence and PostgreSQL enum

- **Decision**: Add one shared `users.User` JPA entity, `UserRole` enum and
  `UserRepository` that read the existing `users` table from V1. The repository needs
  only lookup by email for login. Map `id`, `name`, `email`, `password_hash`, `role`,
  `created_at` and `updated_at` to the existing columns; keep profile fields out of
  auth response DTOs and keep the entity internal to persistence. Map the read-only
  PostgreSQL `user_role` column as its stored lowercase string and convert it through
  `UserRole.fromDatabaseValue` before authentication/authority creation.
- **Rationale**: No user entity/repository exists in `src/main/java`; V1 is authoritative
  and already supplies UUID, unique email, password hash and `user_role` labels
  `admin`/`member`. A shared user mapping satisfies auth without creating a second user
  model or a migration. PostgreSQL is the target database and H2 alone is insufficient
  to validate its native enum behavior.
- **Alternatives considered**:
  - **Create a users migration or alter V1**: rejected because the schema already
    includes all required identity/authentication fields and V1 must remain immutable.
  - **Duplicate a reduced user table/model within `auth`**: rejected because it creates
    a second source of truth and conflicts with the schema.
  - **Use only H2 repository tests**: rejected for role mapping because H2 does not
    reproduce PostgreSQL enum binding semantics.
  - **Bind the native enum directly as an ORM enum for this read-only repository**:
    rejected because V1's lowercase PostgreSQL labels differ from conventional
    uppercase Java enum constants and the feature never writes or queries by role.
    Explicit string-to-enum conversion avoids a schema change and ORM-specific
    parameter binding while a PostgreSQL integration test verifies the actual read.

## R3. Password verification

- **Decision**: Use Spring Security `DaoAuthenticationProvider` /
  `AuthenticationManager`, a repository-backed `UserDetailsService`, and
  `BCryptPasswordEncoder` for verifying `password_hash`. Existing/provisioned password
  hashes must be BCrypt-compatible; provisioning them is a prerequisite owned outside
  this feature.
- **Rationale**: These are the standard Spring Security components for username and
  password authentication. `DaoAuthenticationProvider` provides credential checks and
  hides user-not-found as bad credentials by default; the public controller still maps
  all credential failures to one generic response. BCrypt is available in the current
  Spring Security ecosystem without a separate password hashing dependency.
- **Alternatives considered**:
  - **Manual repository lookup and `PasswordEncoder.matches` in controller/service**:
    rejected because it bypasses Spring Security's established authentication
    provider and needlessly duplicates credential-failure handling.
  - **Plaintext, reversible encryption, or a custom hash**: rejected as inappropriate
    for stored passwords and unnecessary for the requested login feature.
- **Compatibility requirement**: This repository has no existing user model, hash
  implementation or account-provisioning feature; current fixture hash `x` is only an
  image-test placeholder, not a supported login credential. Confirm the actual
  provisioning source supplies BCrypt hashes before production auth is enabled.

## R4. Claim-to-principal and authority contract

- **Decision**: Set standard JWT `sub` to the persisted UUID's canonical string.
  Include a signed `role` claim derived from the database enum. Configure
  `JwtAuthenticationConverter` to preserve `sub` as the authentication name and map
  `admin` to `ROLE_ADMIN`; map `member` to a non-admin authority such as `ROLE_MEMBER`.
  Reject any other role. `SecurityAuthenticatedUserProvider` remains unchanged.
- **Rationale**: Spring Security's JWT authentication uses `sub` as its principal name
  by default, matching `SecurityAuthenticatedUserProvider`, pages-api, folders-api and
  image-storage. Explicit role conversion is needed because Resource Server's default
  authority extraction is designed for OAuth scopes and would not inherently map the
  project's PostgreSQL enum to `ROLE_ADMIN`.
- **Alternatives considered**:
  - **Put an email in `sub` and UUID in a custom claim**: rejected because current API
    consumers parse `Authentication.getName()` as UUID.
  - **Use a generic authority prefix on the lowercase database enum**: rejected
    because it would produce `ROLE_admin`, not the existing exact `ROLE_ADMIN`
    contract.
  - **Create authorization rules in auth-api**: rejected; each resource API retains
    its own authorization logic.
- **Token semantics**: Identity and role are snapshots at login. A role change affects
  newly issued tokens; already-issued tokens remain valid until expiration. This is
  bounded by the short TTL and avoids per-request database reads or a revocation
  system, neither of which is in scope.

## R5. Configuration and deployment boundary

- **Decision**: Bind `APP_AUTH_JWT_SECRET_BASE64` and
  `APP_AUTH_JWT_TTL_SECONDS` to validated `app.auth.jwt` settings. Require a
  base64-decoded key of at least 32 cryptographically random bytes for HS256. Require
  a positive configured lifetime no greater than one hour; document 900 seconds
  (15 minutes) as the intended local/production starting value. Do not supply a
  committed or hard-coded fallback, and fail startup for missing/invalid values.
- **Rationale**: A Base64 environment value is safe for deployment systems and key
  length is validated in decoded form. A short, bounded lifetime limits exposure if a
  bearer token is leaked while allowing the client to know `expiresAt`. Requiring the
  key in every runtime prevents accidental use of a known development signing key.
- **Local environment**: Developers generate a unique local key and set both variables
  in their shell or an ignored local secrets file. Automated tests override properties
  with a test-only key that is never used in deployment.
- **Production dependency**: The environment-infrastructure work must provide the two
  values to the Lambda runtime and store the key in the approved secrets manager.
  `template.yaml`, release workflows and infrastructure resources are explicitly
  untouched by this feature; production activation is blocked until that external work
  supplies the variables consistently to every running instance.
- **Alternatives considered**:
  - **Embed a development/production default in application properties or SAM**:
    rejected because a known or committed secret compromises all issued tokens and
    production secret injection belongs to the separate infrastructure feature.
  - **Secret manager integration in the auth feature**: rejected because it would
    alter deployment infrastructure and cross the user-defined feature boundary.

## R6. Login contract and error behavior

- **Decision**: Use `POST /api/auth/login` with a DTO containing email and password.
  Return a DTO containing only `accessToken`, `tokenType: "Bearer"` and ISO-8601
  `expiresAt`. Invalid/missing request shape returns 400; unknown email and incorrect
  password both return 401 with exactly `{"error":"Invalid email or password"}`.
- **Rationale**: This is consistent with an access-token login flow and the project's
  scoped error-body patterns. A dedicated response DTO prevents accidental
  serialization of `User`, `password_hash` or unrelated profile data.
- **Alternatives considered**:
  - **Different error responses for missing email and wrong password**: rejected
    because that leaks account existence.
  - **Return user/profile fields with the token**: rejected because profile is
    explicitly out of scope and token authentication needs only the UUID/role claims.

## R7. Test strategy

- **Decision**: Cover service success/failure and role claim creation with unit tests;
  cover request validation and exact response status/body with MockMvc; verify User
  mapping and authentication end-to-end on the existing PostgreSQL Testcontainers
  schema; run a signed token through the real Lambda adapter security test.
- **Rationale**: Each layer tests its own contract and the project already uses
  Testcontainers for PostgreSQL and a dedicated Lambda adapter test because
  MockMvc cannot detect adapter-specific session behavior.
- **Alternatives considered**: Trusting mocked Spring Security in all tests is rejected
  because it would not prove that the issued signature, expiration, subject and claims
  are accepted by the actual filter chain or Lambda adapter.
