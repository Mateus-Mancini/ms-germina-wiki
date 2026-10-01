# Quickstart & Validation: Auth API

This guide validates feature `009-auth-api`. See
[contracts/auth-api.yaml](contracts/auth-api.yaml) for HTTP shape and
[data-model.md](data-model.md) for user, role and token invariants.

## Prerequisites

- JDK 21 and Docker available.
- The repository Maven Wrapper (`mvnw.cmd`) and the existing Testcontainers setup.
- A local PostgreSQL schema migrated from V1 for manual runtime testing.
- Local runtime variables set outside the repository:
  - `APP_AUTH_JWT_SECRET_BASE64`: base64 encoding of at least 32 random bytes.
  - `APP_AUTH_JWT_TTL_SECONDS`: positive integer no greater than `3600`; use `900`
    (15 minutes) as the intended starting value.
- A pre-provisioned test/local user row with a BCrypt-compatible `password_hash` and
  `role` set to `admin` or `member`. This feature does not create accounts.

Generate a unique local signing key locally (do not commit it or reuse it in
production), then set both variables in the current shell or an ignored local secrets
file. Never print production secrets in CI logs.

## Automated Validation

From the repository root:

```powershell
.\mvnw.cmd test
```

The auth test set should include:

- `AuthServiceTest`: valid BCrypt credentials issue a token; unknown email and wrong
  password yield the same generic failure and no token.
- `AuthControllerTest`: valid response contains only the documented token fields;
  malformed login input returns 400; credential failures return the same 401 body.
- `JwtTokenServiceTest` and `JwtConfigurationTest`: HS256, canonical UUID `sub`,
  persisted role, `iat`, `exp`, stable `iss`, no sensitive claims, and rejection of
  expired, altered, unsupported-algorithm or invalid-claim tokens.
- `JwtRoleAuthenticationConverterTest`: only `admin` maps to `ROLE_ADMIN`; the
  existing `SecurityAuthenticatedUserProvider` receives UUID identity and the
  correct `isAdmin` value for both roles.
- `SecurityConfigTest`: protected routes reject missing, malformed and expired tokens,
  accept valid JWTs, and preserve public health and image-read routes.
- `UserRepositoryPostgresTest`, `AuthIntegrationTest` and
  `AuthSecurityIntegrationTest`: use the existing Flyway V1 schema/Testcontainers to
  check native enum mapping, exact lookup, login, generic failures and UUID identity.
  No users migration is added.
- Lambda adapter test: pass an issued Bearer token through the real HTTP API v2 adapter
  and confirm that token handling does not require a session or produce a 502.

## Manual Login and Protected Request

1. Start the API with the local environment variables and a pre-provisioned account.
2. Send `POST /api/auth/login` with JSON `{"email":"...","password":"..."}`.
3. Confirm `200` and only `accessToken`, `tokenType` and `expiresAt` in the response.
4. Send the token in `Authorization: Bearer <accessToken>` to a protected endpoint.
5. Confirm the API associates the request with the account UUID, not the email.
6. Repeat with a member and admin account and verify only the admin carries
   `ROLE_ADMIN` / `isAdmin=true`.
7. Repeat with a missing token, altered token and expired token; each protected
   request must receive 401. Try both an unknown email and a wrong password; their
   login status and response JSON must match exactly.

## Production Environment Dependency

The production environment infrastructure must inject
`APP_AUTH_JWT_SECRET_BASE64` from the approved secrets manager and
`APP_AUTH_JWT_TTL_SECONDS` (recommended `900`) into every API Lambda runtime. The
signing key must be the same across instances and at least 256 bits before Base64
encoding. Do not change `template.yaml` or release workflows as part of this feature;
production authentication cannot start until the separate environment-infrastructure
work provides these variables.
