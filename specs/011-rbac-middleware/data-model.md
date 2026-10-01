# Phase 1 Data Model: RBAC Middleware (Admin-Only Guard)

This feature introduces no persistent data entity, database table, or migration. The two
conceptual entities named in `spec.md` are realized purely through existing runtime constructs;
they are documented here for traceability back to the spec, not as new storage.

## Protected Operation

| Aspect | Description |
|---|---|
| What it represents | A backend operation (a controller or service method) that has been designated as requiring administrative permission before it runs. |
| Realized as | A Spring-managed bean method annotated with `com.wikigerminare.security.AdminOnly`. |
| Key attribute | Presence/absence of the `@AdminOnly` annotation on the method (or, if ever needed, the class). No other state is tracked. |
| Lifecycle | Static — determined at compile time by whoever writes the annotation; not created, updated, or deleted at runtime. |
| Relationships | None to other entities in this feature. A future feature (e.g., `pages-api`, `comments-api`) owns the actual business entity/operation the annotation is attached to; this feature does not model or depend on that business entity. |

## Access Decision

| Aspect | Description |
|---|---|
| What it represents | The outcome produced when a request reaches a Protected Operation: denied-not-authenticated (401), denied-insufficient-permission (403), or allowed. |
| Realized as | The control-flow outcome of Spring Security's method-security interceptor evaluating `hasRole('ADMIN')` against the current `Authentication` from `SecurityContextHolder`: it either lets the method proceed (allowed), throws `AuthenticationException` (translated to 401 by the existing `AuthenticationEntryPoint`), or throws `AccessDeniedException` (translated to 403 by the default `AccessDeniedHandler`). |
| Key attribute | The resulting HTTP status (401 / 403 / none — i.e., request proceeds). Not persisted; computed per request. |
| Relationships | Derived from the caller's existing `AuthenticatedUser`/`Authentication` (established by the pre-existing authentication feature) and the target method's Protected Operation marking. This feature neither creates nor stores that identity — it only reads the authority already granted to it (`ROLE_ADMIN`). |

## Non-goals (explicitly not modeled here)

- No new `User`, `Role`, or `Permission` entity/table.
- No audit/log entity for denied attempts (not required by any FR/SC in `spec.md`).
- No per-operation configuration entity (e.g., a database-driven allow-list of admin-only routes)
  — the marking is purely declarative, in source code, via the annotation.
