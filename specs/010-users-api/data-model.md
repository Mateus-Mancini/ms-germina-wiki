# Data Model: users-api

**Feature**: `010-users-api` | **Date**: 2026-10-01

## Existing User account

The existing `com.wikigerminare.users.User` entity maps the authoritative V1 `users` table and is shared with auth-api. The feature reuses it; it does not add a second account/profile entity or alter the table.

| Field / column | V1 type | Nullability | Own response | Public response | Update request |
|---|---|---:|---:|---:|---:|
| `id` | UUID | required | Yes | Yes | No |
| `name` | VARCHAR(150) | required | Yes | Yes | Yes, not null/blank, max 150 characters |
| `email` | VARCHAR(255) | required, unique | Yes, own account only | No | No |
| `password_hash` | TEXT | required | No | No | No |
| `avatar_url` | TEXT | nullable | Yes | Yes | Yes; explicit null clears it |
| `bio` | TEXT | nullable | Yes | Yes | Yes; explicit null clears it |
| `role` | `user_role` enum | required | No | No | No |
| `created_at` | TIMESTAMPTZ | required | No | No | No |
| `updated_at` | TIMESTAMPTZ | required | No | No | No |

The existing V1 trigger maintains `updated_at` on row updates. The field remains internal and is not returned. The schema is sufficient; no migration is required.

## DTOs

### OwnUserProfileResponse

| JSON field | Type | Required | Notes |
|---|---|---:|---|
| `id` | UUID string | yes | Authenticated account identifier |
| `name` | string | yes | Profile name |
| `email` | string | yes | Visible only to the authenticated account itself; immutable in this API |
| `avatarUrl` | string or null | yes | Maps `avatar_url` |
| `bio` | string or null | yes | Nullable profile biography |

Used for `GET /api/users/me` and the successful `PATCH /api/users/me` response. It must not contain role, password/hash, authentication data, or timestamps.

### PublicUserProfileResponse

| JSON field | Type | Required | Notes |
|---|---|---:|---|
| `id` | UUID string | yes | Public profile identifier |
| `name` | string | yes | Public profile name |
| `avatarUrl` | string or null | yes | Maps `avatar_url` |
| `bio` | string or null | yes | Maps `bio` |

Used only for `GET /api/users/{userId}`. Its allowlist excludes email, role, credentials, authentication state and metadata.

### UpdateUserProfileRequest

| JSON field | Type | Required for PATCH | Validation / semantics |
|---|---|---:|---|
| `name` | string | optional | If present, must be non-null, nonblank and at most 150 characters |
| `avatarUrl` | string or null | optional | Omitted preserves current value; explicit null clears value; no schema-defined length or URL validation |
| `bio` | string or null | optional | Omitted preserves current value; explicit null clears value; no schema-defined maximum |

At least one editable field must be present. Presence flags distinguish omitted properties from explicit nulls, following `UpdateFolderRequest`. JSON properties other than these three are rejected. In particular, `id`, `email`, `passwordHash`, `password_hash`, `role`, credential fields and metadata are not accepted.

## Identity and update flow

```text
authenticated request
    -> existing AuthenticatedUserProvider.currentUser().id()
    -> UserRepository.findById(authenticated UUID)
    -> own response DTO or profile-only entity update

authorized consumer + path UUID
    -> UserRepository.findById(path UUID)
    -> public response DTO allowlist
```

No request field can replace the authenticated UUID or change account identity/credentials/role. Missing accounts produce not-found behavior; no fallback account is selected.
