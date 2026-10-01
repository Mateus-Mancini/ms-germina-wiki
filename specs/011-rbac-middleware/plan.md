# Implementation Plan: RBAC Middleware (Admin-Only Guard)

**Branch**: `011-rbac-middleware` | **Date**: 2026-10-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/011-rbac-middleware/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Provide a single, reusable "administrator only" guard for backend operations by turning on
Spring Security's existing method-security authorization (`@EnableMethodSecurity` +
`@PreAuthorize`) and exposing it to the rest of the codebase as one small composed annotation,
`@AdminOnly`. The annotation is meta-annotated with `@PreAuthorize("hasRole('ADMIN')")`, reusing
the `ROLE_ADMIN` authority that `JwtRoleAuthenticationConverter` already grants today. No new
filter, interceptor, aspect, authentication mechanism, or role model is introduced. `SecurityConfig`
keeps producing 401 for missing/invalid authentication (already configured via
`HttpStatusEntryPoint(UNAUTHORIZED)`); Spring Security's default `AccessDeniedHandler` produces 403
when an authenticated, non-admin caller is denied by `@AdminOnly` — both paths run before the
protected method body executes. This feature only builds and proves the mechanism (via test-only
probe endpoints); it does not modify `pages-api` or `comments-api`, which may adopt `@AdminOnly` in
a later, separate change.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (`spring-boot-starter-security`,
`spring-boot-starter-security-oauth2-resource-server`, both already present); Spring Security's
method-security module (`spring-security-config`, transitively included — no new Maven dependency
required)

**Storage**: N/A — no new persistent entity; the guard reads only the in-request `Authentication`
already populated by the existing JWT resource-server filter chain

**Testing**: JUnit 5, Spring Boot Test (`@WebMvcTest`, `MockMvc`), `spring-security-test`
(already a test dependency) — same patterns as `SecurityConfigTest`

**Target Platform**: Existing backend deployment target (AWS Lambda / Function URL via
`aws-serverless-java-container`, `sa-east-1`) — unaffected by this feature beyond normal request
handling

**Project Type**: Single Spring Boot backend project (existing layout; no new module)

**Performance Goals**: No measurable added latency beyond Spring Security's existing
pre-invocation authorization check (in-memory authority comparison); no new I/O, no new network
calls

**Constraints**: Must reuse the `SecurityConfig` filter chain's existing 401 behavior unchanged;
must not introduce a second authentication or role mechanism; must not require
`pages-api`/`comments-api` changes as part of this feature

**Scale/Scope**: Project-wide reusable mechanism; zero real consumers wired in this feature
(adoption by `pages-api`/`comments-api` is explicitly out of scope here and left for later tasks)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Especificação como fonte de verdade | Plan implements exactly FR-001..FR-010 from `spec.md`; no behavior added beyond what the spec describes (e.g., no new error-body format is invented beyond what SC-001..003 require). | PASS |
| II. Arquitetura em camadas | `@AdminOnly` is cross-cutting infrastructure, not a layer. It is applied via Spring's existing proxy-based AOP at whichever layer a future feature chooses (controller or service), and runs *before* that method's body — it does not bypass or replace Controller → Service → Repository responsibilities. | PASS |
| III. Contratos REST e DTOs | This feature adds no new public endpoint or DTO; it only governs the 401/403/allow decision ahead of a protected operation. When later adopted by `pages-api`/`comments-api`, those features keep their own existing REST contracts untouched. | PASS |
| IV. Testes automatizados | Plan requires `@WebMvcTest`/`MockMvc` security tests proving 401 (anonymous), 403 (authenticated non-admin), and allow (admin) outcomes, plus a test proving the protected method body never executes when denied. | PASS |
| V. Simplicidade, consistência e manutenibilidade | Chooses Spring Security's built-in method security over a custom filter/interceptor/aspect (see `research.md`); adds one small annotation type and one config toggle, consistent with existing `config`/`security` conventions already in the codebase (`auth/security/...`). | PASS |

No violations — Complexity Tracking table is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/011-rbac-middleware/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── admin-only-guard.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Single Spring Boot backend project (existing layout — Option 1, no new module). The guard is
added as a small, new `security` package consumable by any existing feature package
(`pages`, `controller` (comments/images), `users`, `search`, `folders`, ...) without those
packages being modified by this feature:

```text
src/main/java/com/wikigerminare/
├── security/                          # NEW — reusable admin-only guard, consumable project-wide
│   └── AdminOnly.java                 # Composed annotation: meta-annotated with
│                                       # @PreAuthorize("hasRole('ADMIN')")
├── config/
│   └── SecurityConfig.java            # MODIFIED — add @EnableMethodSecurity to activate
│                                       # @PreAuthorize-based method security; filter chain,
│                                       # 401 entry point, and public routes stay unchanged
├── controller/  pages/  users/  search/  folders/  ...   # UNCHANGED by this feature;
│                                                          # future features import
│                                                          # com.wikigerminare.security.AdminOnly

src/test/java/com/wikigerminare/
├── security/
│   └── AdminOnlyGuardTest.java        # NEW — @WebMvcTest probe controller annotated with
│                                       # @AdminOnly; asserts 401 (no/invalid token),
│                                       # 403 (authenticated, non-admin), 200 (admin), and that
│                                       # the probe's own method body never runs when denied
└── config/
    └── SecurityConfigTest.java        # UNCHANGED — existing filter-chain/401 coverage stays
                                        # valid as a regression guard
```

**Structure Decision**: Single-project layout (matches the existing Maven/Spring Boot module —
no frontend/backend split, no new module). The guard lives in a new, small, dependency-free
`com.wikigerminare.security` package so that any existing or future feature package can depend
on it (`import com.wikigerminare.security.AdminOnly;`) without creating a reverse dependency on
`config` or on any single feature. `SecurityConfig` gets one additive annotation
(`@EnableMethodSecurity`) to turn on the mechanism; its filter chain, public routes, and 401
entry point are otherwise untouched (FR-002, FR-008). No files under `pages/`, `controller/`
(comments/images), `users/`, `search/`, or `folders/` are created or modified by this feature
(explicit out-of-scope instruction); `quickstart.md` and `contracts/admin-only-guard.md` instead
document, for whoever implements that future adoption, exactly how to add `@AdminOnly` to an
operation in those packages without writing custom permission-checking code (FR-006, SC-004).

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
