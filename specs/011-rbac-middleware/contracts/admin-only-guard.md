# Contract: `@AdminOnly` Admin-Only Guard

This is not an HTTP/REST contract — this feature adds no new endpoint. It is the **usage
contract** other backend features (notably `pages-api` and `comments-api`, in future work) rely on
to mark one of their own operations as administrator-only, without writing their own permission
check.

## Where it lives

```java
package com.wikigerminare.security;
```

`com.wikigerminare.security.AdminOnly` — a new, standalone package with no dependency on any
feature package (`pages`, `controller`, `users`, `search`, `folders`, ...), so any feature may
depend on it without creating a cycle.

## Declaration (shape, not final source)

```java
package com.wikigerminare.security;

import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {
}
```

## How a feature consumes it

```java
@AdminOnly
@DeleteMapping("/api/pages/{pageId}")
public void delete(@PathVariable UUID pageId) { ... }
```

or, placed on a service method instead of the controller, at the feature's own discretion:

```java
@AdminOnly
public void deleteFolder(UUID folderId) { ... }
```

No other code is required in the consuming feature: no manual `Authentication` lookup, no manual
`isAdmin()` check, no custom exception handling for the 401/403 cases.

## Preconditions for the contract to hold

- The method is invoked through Spring (a managed bean method called via its proxy — e.g., a
  `@RestController`/`@Service` bean method called from outside the class). Calling the annotated
  method directly via `this.method(...)` from inside the same class bypasses the proxy and is a
  known, general Spring AOP limitation — not specific to this feature.
- `SecurityConfig` continues to enable method security (`@EnableMethodSecurity`) and continues to
  authenticate requests via the existing JWT resource-server configuration that grants
  `ROLE_ADMIN` to administrators (`JwtRoleAuthenticationConverter`).

## Guaranteed behavior (the contract)

| Caller | Outcome | HTTP status | Method body runs? |
|---|---|---|---|
| No authenticated identity (anonymous, missing/invalid token) | Denied — not authenticated | 401 | No |
| Authenticated, `ROLE_ADMIN` not present | Denied — insufficient permission | 403 | No |
| Authenticated, `ROLE_ADMIN` present | Allowed | n/a (request proceeds) | Yes, unmodified |

This matrix is identical regardless of which feature package applies `@AdminOnly` (FR-007,
SC-005).

## Explicit non-responsibilities (what this contract does NOT do)

- It does not perform or replace any feature-specific business authorization (e.g., "only the
  comment's author may edit it", ownership checks in `CommentService`, publish-state checks in
  pages). Those rules stay exactly where they are today.
- It does not produce a JSON error body for the 401/403 responses (see `research.md` §4); it only
  guarantees the HTTP status and that the method body does not execute.
- It does not require migrating any existing hand-written admin check (e.g., `ImageController`'s
  `isAdmin(Principal)`, `CommentService.reply`'s `user.isAdmin()` check) — adoption is optional and
  left to each feature's own future work.

## Future adoption note (not implemented by this feature)

A later, separate change may replace an existing feature's hand-written admin check (for example,
`CommentService.reply`'s `if (!user.isAdmin()) throw ...`, or `ImageController.delete`'s
`isAdmin(principal)` parameter) with `@AdminOnly` on the corresponding controller/service method.
That replacement is out of scope here and must be done, and tested, by that future change —
this feature only guarantees the table above holds once `@AdminOnly` is applied.
