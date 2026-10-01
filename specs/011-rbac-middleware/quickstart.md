# Quickstart: Validating the Admin-Only Guard

This guide validates the `@AdminOnly` mechanism end-to-end once implemented, using the project's
existing local test tooling. It does not touch `pages-api`/`comments-api`; it validates the guard
itself via the test-only probe described in `plan.md`/`research.md` §5.

## Prerequisites

- JDK 21 and Docker available locally (same prerequisites as the rest of the project — see
  `README.md`).
- No environment variables needed beyond what the existing test suite already configures (the
  `local`/test JWT signing setup already used by `SecurityConfigTest`/`AuthSecurityIntegrationTest`).

## 1. Run the guard's dedicated test classes

```bash
./mvnw test -Dtest=AdminOnlyAnonymousAccessTest,AdminOnlyMemberAccessTest,AdminOnlyAdminAccessTest,AdminOnlyOtherFeatureProbeTest
```

**Expected outcome**: all 11 tests pass — `AdminOnlyAnonymousAccessTest` (US1, 401), `AdminOnlyMemberAccessTest`
(US2, 403 — including the no-granted-authorities case), `AdminOnlyAdminAccessTest` (US3, allow), and
`AdminOnlyOtherFeatureProbeTest` (US4, same matrix reproduced in a second feature area), covering the
scenarios below plus the "never executes" assertion via each probe's call counter.

## 2. Scenarios this proves

| # | Scenario | How it's exercised | Expected result |
|---|---|---|---|
| 1 | Anonymous caller | `MockMvc` request to a probe endpoint annotated with `@AdminOnly`, no `Authorization` header | HTTP 401; probe method body not invoked (verified via a mock/spy or a static counter) |
| 2 | Authenticated, non-admin caller | Same request, with a valid signed JWT whose `role` claim is `member` | HTTP 403; probe method body not invoked |
| 3 | Authenticated administrator | Same request, with a valid signed JWT whose `role` claim is `admin` | HTTP 200 (or the probe's declared success status); probe method body invoked exactly once |
| 4 | Unaffected, unmarked operation | A second probe endpoint with no `@AdminOnly` | Reachable by the same non-admin caller from scenario 2 (HTTP 200), proving the guard only affects marked operations (FR-008) |
| 5 | Consistency across "feature areas" | Two independent probe endpoints/methods in different packages, both `@AdminOnly` | Both produce the identical 401/403/allow matrix for the same three caller types (SC-005 at the mechanism level) |

## 3. Run the full existing security regression suite

```bash
./mvnw test -Dtest=SecurityConfigTest,AuthSecurityIntegrationTest,AdminOnlyAnonymousAccessTest,AdminOnlyMemberAccessTest,AdminOnlyAdminAccessTest,AdminOnlyOtherFeatureProbeTest
```

**Expected outcome**: all pass — confirms the new `@EnableMethodSecurity` addition to
`SecurityConfig` does not change the existing filter-chain behavior (public routes, 401 entry
point, JWT validation) documented by `SecurityConfigTest` and `AuthSecurityIntegrationTest`.
`AuthSecurityIntegrationTest` requires a local Docker environment (Testcontainers PostgreSQL);
`SecurityConfigTest` and all `AdminOnly*Test` classes are pure `@WebMvcTest` slices and do not.

## 4. Run the full test suite (final check before merge)

```bash
./mvnw test
```

**Expected outcome**: all existing tests across the project still pass — in particular,
`ImageControllerTest`/`AdminCommentControllerTest`/`CommentServiceTest`, which exercise today's
hand-written admin checks, must be unaffected since this feature does not modify
`pages-api`/`comments-api`/`images` (FR-009).

## 5. Manual sanity check (optional)

```bash
./mvnw spring-boot:test-run
```

Then, with a locally issued admin JWT and a locally issued member JWT (see `README.md`'s
"Authentication" section for how local tokens are produced for this project), call the test probe
route (if temporarily exposed) or rely on the automated tests above — the automated tests are the
primary acceptance check for this feature; manual calls are optional confirmation only.
