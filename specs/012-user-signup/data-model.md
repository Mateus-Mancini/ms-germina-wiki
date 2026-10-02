# Data Model: User Sign-up

## Existing User (users)

| Field | Persisted type | Registration rule |
|---|---|---|
| id | UUID primary key | Service-generated UUID; client cannot provide |
| name | VARCHAR(150) NOT NULL | Mandatory, stripped, nonblank, at most 150 characters |
| email | VARCHAR(255) NOT NULL UNIQUE | Mandatory, stripped, valid email, at most 255 characters; case-sensitive |
| password_hash | TEXT NOT NULL | BCrypt encoding of unchanged password; never returned |
| role | user_role NOT NULL | Fixed member, mapped with existing enum cast |
| avatar_url | TEXT nullable | null at creation |
| bio | TEXT nullable | null at creation |
| created_at | TIMESTAMPTZ NOT NULL | Service-generated Instant |
| updated_at | TIMESTAMPTZ NOT NULL | Same Instant as created_at; existing update trigger applies later |

Source: `src/main/resources/db/migration/V1__initial_schema.sql` and `users/User.java`. No schema change or new relationship. Existing authored wiki content continues to reference the same users table.

## RegisterRequest

- name: mandatory, stripped, nonblank, at most 150 characters.
- email: mandatory, stripped, valid email, at most 255 characters; case-sensitive.
- password: mandatory, nonblank, at least 8 characters and at most 72 bytes in UTF-8; no stripping or truncation; write-only.
- Any property other than name/email/password makes the request invalid.

## Response

Reuse OwnUserProfileResponse: id, name, email, avatarUrl (null), bio (null). No password, hash, role or access token. The login response remains unchanged.

## State transitions

Unregistered -> validated -> one persisted member account -> optional existing login. Invalid data stops before lookup/hash/persistence. Duplicate lookup or unique-constraint collision produces conflict and leaves existing state unchanged. Failed flush rolls back; no further query in the aborted transaction.
