# REST Contract: User Sign-up

## POST /api/auth/register

Public without Authorization. Content-Type: application/json.

```json
{"name":"Student","email":"student@example.com","password":"correct password"}
```

Exactly name, email and password are supported. Name/email are stripped; email case is preserved. Name is nonblank and <=150 characters, email valid and <=255 characters. Password is nonblank, >=8 characters and <=72 UTF-8 bytes, preserved exactly. Unknown fields (including role/id/passwordHash/avatarUrl) produce 400. Every account is member.

### 201 Created

Location: `/api/users/{id}` (existing public-profile endpoint still requires authentication).

```json
{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Student","email":"student@example.com","avatarUrl":null,"bio":null}
```

No access token or secret fields. Account is committed before success is returned.

### 400 Bad Request

Missing, null, blank, malformed or out-of-bounds fields, unsupported fields or malformed JSON:

```json
{"error":"Invalid request"}
```

### 409 Conflict

Exact email already exists after stripping, including concurrent collisions:

```json
{"error":"Email already registered"}
```

Existing name, password and permissions remain unchanged. Other storage errors are not disguised as conflict or success.

## Subsequent login

`POST /api/auth/login` with the returned email and original password retains its existing 200 token contract. Use the Bearer token for `GET /api/users/me`; existing admin-only operations deny this member (403). Unsupported HTTP methods under register remain protected by the existing filter chain (401 without identity).
