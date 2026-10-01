# RBAC Middleware: the `@AdminOnly` Admin-Only Guard

Spec: [specs/011-rbac-middleware](../specs/011-rbac-middleware/) · Usage contract:
[specs/011-rbac-middleware/contracts/admin-only-guard.md](../specs/011-rbac-middleware/contracts/admin-only-guard.md)

## What it is

`com.wikigerminare.security.AdminOnly` is a small, reusable annotation that marks a Spring-managed
controller or service method as **administrator only**. It is meta-annotated with
`@PreAuthorize("hasRole('ADMIN')")` and is enforced by Spring Security's declarative method
security (`@EnableMethodSecurity`, enabled on `SecurityConfig`). It reuses the project's existing
authentication and its canonical administrator authority, `ROLE_ADMIN` (already granted by
`JwtRoleAuthenticationConverter`) — it does not introduce a new authentication mechanism, role
model, or user model.

## The guarantee

| Caller | HTTP status | Does the method body run? |
|---|---|---|
| No authenticated identity (anonymous, missing/invalid/expired token) | 401 | No |
| Authenticated, but without `ROLE_ADMIN` (including a caller with no granted authorities at all) | 403 | No |
| Authenticated, with `ROLE_ADMIN` | n/a — request proceeds | Yes, unmodified |

This matrix holds identically no matter which feature package applies the annotation.

## How to use it in your feature

Add the annotation to the controller or service method that represents the operation you want to
restrict to administrators — no other code is required:

```java
import com.wikigerminare.security.AdminOnly;

@AdminOnly
@DeleteMapping("/api/pages/{pageId}")
public void delete(@PathVariable UUID pageId) {
    pageService.delete(pageId);
}
```

or, at the service layer instead, at your feature's own discretion:

```java
@AdminOnly
public void deleteFolder(UUID folderId) {
    ...
}
```

No manual `Authentication`/`Principal` lookup, no manual `isAdmin()` check, and no custom
401/403 exception handling are needed — Spring Security intercepts the call before the method body
executes.

### Preconditions

- The method must be called through its Spring proxy (e.g., invoked from outside the class, or via
  an HTTP route) — calling it directly via `this.method(...)` from inside the same class bypasses
  the proxy, a general Spring AOP limitation unrelated to this guard.
- The request must already go through the project's existing JWT-based authentication
  (`SecurityConfig` + `JwtConfiguration`), which is already the case for every non-public route.

## Intended adopters

`pages-api` and `comments-api` are the primary intended consumers of `@AdminOnly` going forward —
for example, replacing hand-written checks such as `ImageController.isAdmin(Principal)` or
`CommentService.reply`'s `if (!user.isAdmin()) throw ...`. **This feature does not perform that
replacement.** Adopting `@AdminOnly` in an existing operation is optional and left to that
feature's own future work; existing hand-written admin checks continue to work unchanged until a
feature chooses to migrate.

## What it does NOT do

- It does not perform or replace feature-specific business authorization (e.g., comment ownership
  checks, page publish-state rules). Those rules remain exactly where they are today.
- It does not produce a JSON error body for 401/403 denials — it only guarantees the HTTP status
  and that the method body does not execute when denied. Existing per-feature error bodies
  (e.g., `comments-api`'s `ErrorResponse`) are untouched for business-rule failures.
- It does not add, remove, or manage roles — `ROLE_ADMIN` is the sole canonical administrator
  authority, sourced entirely from the existing authentication mechanism.

## Verifying the guard

The guard's own test suite exercises the full 401/403/allow matrix against two independent,
test-only probe operations (`com.wikigerminare.security.AdminOnlyProbeController` and
`com.wikigerminare.security.otherfeature.AdminOnlyOtherFeatureProbeController`), proving the
mechanism is reusable across feature areas without touching `pages-api`/`comments-api`. See
[specs/011-rbac-middleware/quickstart.md](../specs/011-rbac-middleware/quickstart.md) for how to
run these tests.
