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

- `AuthServiceTest`: valid login issues a signed token with correct UUID and role;
  unknown account and wrong password produce the same generic authentication error;
  no token is issued on failure.
- JWT tests: HS256 signature, canonical UUID `sub`, role claim, issue/expiry times,
  invalid signature, expired token and malformed/unknown claims.
- MockMvc tests: login success DTO shape; 400 for missing/malformed request; identical
  401 status/body for unknown email and incorrect password; no password/hash in output.
- PostgreSQL Testcontainers test against the Flyway V1 schema: exact user lookup,
  native `user_role` mapping and login using a BCrypt hash; no migration is added.
- Security-chain tests: a real login token authenticates a protected route; the
  authentication name is the user UUID; only admin has `ROLE_ADMIN`; missing,
  malformed and expired tokens receive 401.
- Regression tests: `/health` and `GET /api/images/{id}` remain public, while other
  protected requests remain denied without a valid token.
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
