# Phase 0 Research: RBAC Middleware (Admin-Only Guard)

All unknowns from the Technical Context were resolvable from the existing codebase and Spring
Security's documented, stable capabilities — no `NEEDS CLARIFICATION` markers remain.

## 1. Mechanism: declarative method security vs. a custom filter/aspect

**Decision**: Use Spring Security's native, declarative method security
(`@EnableMethodSecurity` + `@PreAuthorize`) as the enforcement mechanism, exposed to callers
through one small composed annotation, `@AdminOnly` (`@PreAuthorize("hasRole('ADMIN')")`). No
custom `Filter`, `HandlerInterceptor`, or hand-written AOP `Aspect` is introduced.

**Rationale**:
- `SecurityConfig` already relies on Spring Security's standard exception translation:
  `AuthenticationException` → `AuthenticationEntryPoint` (already wired to
  `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`, producing 401) and `AccessDeniedException` →
  `AccessDeniedHandler` (Spring Security's default, producing 403 for an authenticated caller).
  Method-security denials throw exactly these two exception types, so the existing filter chain
  already produces the correct 401/403 split with zero new exception-handling code (FR-002,
  FR-003).
- `JwtRoleAuthenticationConverter` already grants the authority `ROLE_ADMIN` to admin users
  (verified in `JwtRoleAuthenticationConverterTest`), so `hasRole('ADMIN')` (which Spring Security
  expands to the authority `ROLE_ADMIN`) requires no new role/claim plumbing (FR-005).
- Method security runs as a pre-invocation check (a proxy wraps the bean and throws before the
  target method executes), so a denied caller's method body never runs, satisfying FR-002/FR-003's
  "operation's own logic must never execute" requirement without extra guard code inside each
  method.
- This is the smallest change: one additive annotation on `SecurityConfig`
  (`@EnableMethodSecurity`) plus one small, dependency-free annotation type. Per Constitution
  Principle V (simplicity), this is preferred over any custom-built equivalent.

**Alternatives considered**:
| Alternative | Why rejected |
|---|---|
| Custom `HandlerInterceptor`/`Filter` reading `AuthenticatedUserProvider`/`Authentication` manually, per protected route | Re-implements access-decision and exception-translation logic Spring Security already provides; risks diverging from `SecurityConfig`'s existing 401 behavior; must be wired per-route, which is exactly the per-feature duplication the spec asks to avoid (FR-006). |
| Hand-written AOP `@Aspect` calling into `AuthenticatedUserProvider.currentUser().isAdmin()` | Reinvents what `@PreAuthorize`/`AccessDecisionManager` already does; introduces a second, parallel "is this caller allowed" code path alongside Spring Security's, increasing the risk of the two disagreeing; more code to maintain for no behavioral gain. |
| `@Secured("ROLE_ADMIN")` instead of `@PreAuthorize` | Older, less expressive annotation (no SpEL, more limited meta-annotation ergonomics in some Spring Security versions); `@PreAuthorize` is the currently recommended, actively developed mechanism and is already demonstrated in this codebase's dependency set (`spring-security-config`). |
| Only `.requestMatchers(...).hasRole("ADMIN")` entries added to `SecurityConfig` per admin-only route | Forces every new admin-only operation project-wide to be registered centrally in `SecurityConfig`, coupling unrelated features' route tables to one shared file and reintroducing the "each feature touches shared config" problem FR-006 explicitly wants avoided. Method security instead lets each operation opt in locally, at its own declaration site, in whichever feature package owns it. |
| `@EnableGlobalMethodSecurity` (legacy annotation) | Deprecated in current Spring Security in favor of `@EnableMethodSecurity`; using it would add avoidable technical debt. |

## 2. Enabling method security

**Decision**: Add `@EnableMethodSecurity` to the existing `SecurityConfig` class (no new
`@Configuration` class needed). `spring-security-config` — which provides this annotation and its
supporting infrastructure — is already transitively available via `spring-boot-starter-security`;
no `pom.xml` change is required.

**Rationale**: Spring Boot does not auto-enable method security; it must be turned on explicitly.
Placing it on `SecurityConfig` keeps all Spring Security wiring for this project in the one place
that already owns the filter chain and the 401 entry point, satisfying the task's explicit ask to
define "integração com `SecurityConfig`".

**Alternatives considered**: A separate `MethodSecurityConfig` class — rejected as an unnecessary
extra file for a single annotation; would only be justified if method-security configuration grew
non-trivial options later (e.g., custom `MethodSecurityExpressionHandler`), which is not needed
now.

## 3. Reusable contract shape: composed annotation vs. repeating `@PreAuthorize` everywhere

**Decision**: Define `com.wikigerminare.security.AdminOnly`, a custom runtime-retained annotation
meta-annotated with `@PreAuthorize("hasRole('ADMIN')")`. Features apply `@AdminOnly` directly to
the controller or service method that represents the protected operation.

**Rationale**: Spring Security resolves security meta-annotations (custom annotations that are
themselves annotated with `@PreAuthorize`/`@PostAuthorize`/etc.) via its standard
`AnnotatedElementUtils`-based annotation merging, a capability that has been documented and stable
since Spring Security 5.6 and carries forward unchanged into the Spring Security release used by
Spring Boot 4.1.1. This gives every feature a single, intention-revealing declaration
(`@AdminOnly`) instead of repeating the literal SpEL expression `"hasRole('ADMIN')"` at every call
site — satisfying FR-001/FR-006/SC-004 ("single, documented, reusable declaration ... zero custom
permission-checking code").

**Alternatives considered**:
| Alternative | Why rejected |
|---|---|
| Require every feature to write `@PreAuthorize("hasRole('ADMIN')")` directly | Works today, but spreads a literal role-string expression across every feature; a future change to the admin expression (e.g., allowing a second admin-equivalent authority) would require a project-wide search-and-replace instead of a one-line edit to `AdminOnly`. |
| A reusable helper method/static utility called manually from each operation | Does not run as a pre-invocation guard by itself — each feature would still need to remember to call it and interpret its result, reintroducing per-feature duplication and the risk of a forgotten check. |

**Placement of `@AdminOnly` within a feature (controller vs. service)**: Left to the adopting
feature. Because Spring's method-security proxy applies to any Spring-managed bean method, the
annotation can be placed on a controller method (coarse, HTTP-route-level) or a service method
(finer-grained, usable even from non-HTTP callers) without this feature mandating one or the
other — this preserves Constitution Principle II (Controller → Service → Repository layering is a
decision each feature already owns) and keeps this feature from reaching into `pages-api`/
`comments-api` to decide that for them.

## 4. 401/403 response body shape

**Decision**: For this feature, 401/403 responses produced by `@AdminOnly` use Spring Security's
existing defaults — the 401 path already configured in `SecurityConfig`
(`HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`, empty body) and Spring Security's default
`AccessDeniedHandler` for 403 (empty body, standard status line). No new JSON error envelope is
introduced by this feature.

**Rationale**: The spec's functional requirements and success criteria only require the correct
HTTP status (401/403/allow) and that the protected operation's logic never runs when denied; they
do not require a specific JSON error schema. Introducing one here would exceed this feature's
scope ("Não modificar regras de negócio específicas de pages, comments ou images") and would risk
conflicting with each feature's own existing error-body conventions (e.g., `comments-api`'s
`ErrorResponse`/`ApiExceptionHandler` already maps its own `CommentException("FORBIDDEN", ...)` to
a JSON body for its *business*-rule 403s, which is untouched by this feature).

**Alternatives considered**: A shared JSON `ErrorResponse` body for every `@AdminOnly` denial,
modeled after `comments-api`'s `ErrorResponse` — deferred as a follow-up note for whoever adopts
`@AdminOnly` project-wide, not built now, since no functional requirement or success criterion
calls for a specific body shape and doing so now would be speculative, unrequested scope.

## 5. Scope boundary: proving the mechanism without touching `pages-api`/`comments-api`

**Decision**: Prove `@AdminOnly`'s 401/403/allow behavior using one or more test-only probe
controllers defined inside the test source tree (`src/test/java/...`), following the existing
pattern in `SecurityConfigTest` (`ProtectedProbeController`). No file under `pages/`, `controller/`
(comments/images), `users/`, `search/`, or `folders/` is created or modified.

**Rationale**: The task explicitly forbids modifying `pages-api`/`comments-api` in this feature.
Spec success criterion SC-005 ("exercised by operations in at least two distinct feature areas")
is satisfied at the mechanism level by proving `@AdminOnly` behaves identically wherever it is
applied (e.g., two independent probe endpoints/methods in the test tree simulating two different
"feature areas"); real adoption inside `pages-api`/`comments-api` is explicitly deferred to future,
separate work, documented for that future implementer in `contracts/admin-only-guard.md` and
`quickstart.md`.
