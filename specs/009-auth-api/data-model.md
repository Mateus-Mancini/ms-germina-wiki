# Data Model: Auth API

**Feature**: `009-auth-api` | **Date**: 2026-10-01

## Existing User (shared mapping of V1 `users`)

No `User` entity or user repository currently exists in the source tree. This feature
adds one shared mapping for the existing table; it does not create a second user store.
The mapping must match `src/main/resources/db/migration/V1__initial_schema.sql`.

| Field / column | Type | Required | Use and rules |
|---|---|---:|---|
| `id` | UUID | yes | Primary identity; emitted as canonical JWT `sub`. |
| `name` | VARCHAR(150) | yes | Existing column; not returned by login. |
| `email` | VARCHAR(255) | yes | Unique login identifier; looked up as stored, exact value. |
| `password_hash` | TEXT | yes | Internal only; BCrypt-compatible encoded password; never included in an HTTP response or logs. |
| `role` | PostgreSQL `user_role` enum (read as stored string) | yes | Exact labels `admin` or `member`; validated and converted through the shared `UserRole` enum before issuing authorities. |
| `avatar_url` | TEXT | no | Existing profile data; not used by authentication or returned. |
| `bio` | TEXT | no | Existing profile data; not used by authentication or returned. |
| `created_at` | TIMESTAMPTZ | yes | Existing persistence metadata; not returned. |
| `updated_at` | TIMESTAMPTZ | yes | Existing persistence metadata; not returned. |

**Persistence constraints**:

- `users.id`, email uniqueness, defaults, foreign keys and PostgreSQL `user_role` remain
  defined by V1; no migration is needed.
- The auth repository is read-only for this feature and needs only exact email lookup.
- Use the project's shared `users` domain package for the entity, enum and repository.
- Treat this mapping as read-only for auth. Read the lowercase PostgreSQL enum label as
  a string, then convert it explicitly to `UserRole`; unknown labels must fail closed.
  Verify the mapping against real PostgreSQL.
- No entity, entity projection or `UserDetails` object is serialized directly as an HTTP
  response.
- Existing fixture password value `x` is not a valid account credential; auth
  integration fixtures must insert a real test BCrypt hash.

## Login DTOs

### LoginRequest

| Field | Type | Required | Rules |
|---|---|---:|---|
| `email` | string | yes | Valid email syntax; maximum 255 characters; used as the account lookup key. |
| `password` | string | yes | Non-empty; accepted only for credential verification; never echoed. |

Malformed JSON or absent/invalid required fields return 400 without checking or
revealing account existence.

### LoginResponse

| Field | Type | Required | Rules |
|---|---|---:|---|
| `accessToken` | string | yes | Signed JWT; contains no password or password hash. |
| `tokenType` | string | yes | Constant `Bearer`. |
| `expiresAt` | date-time | yes | Absolute UTC expiration corresponding to the signed `exp` claim. |

## Signed JWT Claims

| Claim | Type | Required | Meaning / invariant |
|---|---|---:|---|
| `sub` | string | yes | Canonical string representation of `users.id`; parsed as UUID by API consumers. |
| `iat` | NumericDate | yes | Token issue time. |
| `exp` | NumericDate | yes | Issue time plus configured positive access-token TTL; must be in the future at validation. |
| `iss` | string | yes | Stable GerminaWiki API issuer identifier. |
| `role` | string | yes | Exact persisted role label, `admin` or `member`; signature protects its integrity. |

No email, profile fields or credential data are needed in the token. The token must be
signed with HS256 and validated only with that configured algorithm and key. Missing,
malformed or unknown subject/role claims do not form an authenticated identity.

## Authentication Principal and Role Mapping

| Persisted role | Spring authority | `Authentication.getName()` | `AuthenticatedUser.isAdmin` |
|---|---|---|---:|
| `admin` | `ROLE_ADMIN` | UUID from `sub` | true |
| `member` | `ROLE_MEMBER` (or equivalent non-admin authority) | UUID from `sub` | false |

`SecurityAuthenticatedUserProvider.currentUser()` remains the shared integration
contract and returns `AuthenticatedUser(id, isAdmin)`. Principal name is not the
email. Each API continues to enforce its own resource-specific authorization.

## Credential and Token State

```text
existing user + matching BCrypt password
    -> issue signed access token (sub UUID, persisted role, iat, exp)
    -> Bearer token presented to protected route
    -> signature/algorithm/expiry/identity/role validated
    -> authenticated principal + mapped authorities

unknown email OR wrong password -> same generic 401; no token issued
missing/malformed request       -> 400; no credential lookup
invalid/expired token           -> 401; no authenticated principal
```

Issued tokens are stateless and cannot be individually revoked in this feature. Role
changes take effect for tokens issued after the change; already-issued tokens retain
their signed role until they expire.
