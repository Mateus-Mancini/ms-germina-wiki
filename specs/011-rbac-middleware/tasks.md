---

description: "Task list template for feature implementation"
---

# Tasks: RBAC Middleware (Admin-Only Guard)

**Input**: Design documents from `specs/011-rbac-middleware/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/admin-only-guard.md, quickstart.md

**Tests**: Tests are explicitly REQUIRED by the feature instructions (admin → allowed, member → 403, anonymous → 401, non-admin roles → denied, absence of admin authority must never be interpreted as permission).

**Organization**: Tasks are grouped by user story (from `spec.md`) to enable independent implementation and testing of each story. Out of scope for all phases: `pages-api`, `comments-api`, any new JWT/login/`UserService`/user model (per feature instructions).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Single Spring Boot project (existing layout, per `plan.md`):
- Main sources: `src/main/java/com/wikigerminare/`
- Test sources: `src/test/java/com/wikigerminare/`
- Docs: `docs/`, `README.md`

---

## Phase 1: Setup

**Purpose**: Establish the new shared package the guard will live in.

- [ ] T001 Create the new `com.wikigerminare.security` main package with a `package-info.java` in `src/main/java/com/wikigerminare/security/package-info.java`, documenting that this package holds the project-wide, reusable admin-only authorization guard (consumed by any feature package; no dependency on `pages`, `controller`, `users`, `search`, or `folders`)

**Checkpoint**: Package exists; no Maven dependency changes are needed (`spring-security-config`, providing `@EnableMethodSecurity`/`@PreAuthorize`, is already transitively available via `spring-boot-starter-security`, per `research.md` §2).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The reusable mechanism and its activation. All user stories (which only *validate* the mechanism) depend on this phase.

**⚠️ CRITICAL**: No user story test can be written or pass until this phase is complete.

- [ ] T002 [P] Create the reusable `com.wikigerminare.security.AdminOnly` composed annotation in `src/main/java/com/wikigerminare/security/AdminOnly.java`: `@Target({ElementType.METHOD, ElementType.TYPE})`, `@Retention(RetentionPolicy.RUNTIME)`, meta-annotated with `@PreAuthorize("hasRole('ADMIN')")` (Spring Security expands `hasRole('ADMIN')` to the authority `ROLE_ADMIN`, the project's canonical admin authority granted by `JwtRoleAuthenticationConverter`); add Javadoc describing the 401/403/allow contract from `specs/011-rbac-middleware/contracts/admin-only-guard.md`
- [ ] T003 [P] Enable declarative method-security authorization by adding `@EnableMethodSecurity` to `src/main/java/com/wikigerminare/config/SecurityConfig.java`, without changing its existing filter chain, public route matchers, or the 401 `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` authentication entry point (per `research.md` §1-2)
- [ ] T004 Create a shared, test-only probe controller in `src/test/java/com/wikigerminare/security/AdminOnlyProbeController.java` exposing: (a) `GET /security-test/admin-only`, annotated with `@AdminOnly`, that increments a thread-safe call counter and returns 200 on execution, and (b) `GET /security-test/unprotected`, with no annotation, that also increments its own counter and returns 200 — both counters reset per test (depends on T002 for `@AdminOnly` to exist)
- [ ] T005 [P] Create a shared test-support helper in `src/test/java/com/wikigerminare/security/AdminOnlyGuardTestSupport.java` that mints signed test JWTs for three caller types — admin (`role=admin` claim → `ROLE_ADMIN`), member (`role=member` claim → `ROLE_MEMBER`), and no-authority (a valid signed token whose role claim grants no recognized authority) — following the same `JwtEncoder`/`JwtClaimsSet` pattern already used in `src/test/java/com/wikigerminare/config/SecurityConfigTest.java`

**Checkpoint**: Foundation ready — `@AdminOnly` exists and is enforced, the probe controller is protectable, and test helpers can mint every caller type needed by the user story phases below.

---

## Phase 3: User Story 1 - Anonymous user is blocked from an administrative operation (Priority: P1) 🎯 MVP

**Goal**: A request with no valid authenticated identity against an `@AdminOnly` operation is rejected with HTTP 401, and the operation's own logic never runs.

**Independent Test**: Call the `@AdminOnly` probe (`GET /security-test/admin-only`) with no `Authorization` header and with an invalid/expired bearer token; confirm both return 401 and the probe's call counter never increments.

- [ ] T006 [US1] Write test in `src/test/java/com/wikigerminare/security/AdminOnlyAnonymousAccessTest.java` asserting a request to `/security-test/admin-only` with no `Authorization` header returns HTTP 401 and the probe's call counter remains at 0 after the call (uses `AdminOnlyProbeController` from T004)
- [ ] T007 [US1] In the same file (`src/test/java/com/wikigerminare/security/AdminOnlyAnonymousAccessTest.java`), add a test asserting a request with an invalid/malformed/expired bearer token to `/security-test/admin-only` is treated the same as anonymous — HTTP 401, call counter stays at 0 — confirming invalid identities never fall through to a permission check (spec Edge Cases, FR-002)

**Checkpoint**: User Story 1 is independently verifiable — anonymous and invalid-credential callers are provably denied before the protected code runs.

---

## Phase 4: User Story 2 - Authenticated non-administrator is denied (Priority: P1)

**Goal**: An authenticated caller without `ROLE_ADMIN` is rejected with HTTP 403 against an `@AdminOnly` operation, and the operation's own logic never runs. Any non-admin caller is denied — never treated as implicitly permitted.

**Independent Test**: Call the `@AdminOnly` probe as a signed-in `member`, as a signed-in caller with an unrecognized/no admin authority, and confirm both return 403 with the call counter never incrementing; also confirm the same `member` caller can reach the unprotected probe normally.

- [ ] T008 [US2] Write test in `src/test/java/com/wikigerminare/security/AdminOnlyMemberAccessTest.java` asserting a request to `/security-test/admin-only` using a valid signed token for an authenticated `member` (role=member, authority `ROLE_MEMBER`, from `AdminOnlyGuardTestSupport`) returns HTTP 403 and the probe's call counter remains at 0
- [ ] T009 [US2] In the same file (`src/test/java/com/wikigerminare/security/AdminOnlyMemberAccessTest.java`), add a test asserting a different non-admin authenticated role (e.g., a caller whose only authority is `ROLE_MEMBER` but obtained via a distinct token/claim shape, or any authority string other than `ROLE_ADMIN`) is likewise denied with HTTP 403 — proving denial generalizes to "any role other than admin" (FR-003), not just the literal `member` case
- [ ] T010 [US2] In the same file (`src/test/java/com/wikigerminare/security/AdminOnlyMemberAccessTest.java`), add a test asserting an authenticated caller that holds **zero** granted authorities (valid identity, no `ROLE_ADMIN` and no other role at all) is still denied with HTTP 403 — explicitly proving "ausência de autoridade administrativa não pode ser interpretada como permissão" (absence of admin authority must never be interpreted as permission)
- [ ] T011 [US2] In the same file (`src/test/java/com/wikigerminare/security/AdminOnlyMemberAccessTest.java`), add a test asserting the same `member` caller from T008 CAN successfully call `/security-test/unprotected` (HTTP 200, its own counter increments) — confirming the guard only affects operations explicitly marked `@AdminOnly` (FR-008)

**Checkpoint**: User Story 2 is independently verifiable — every flavor of "authenticated but not admin" is provably denied, and unprotected operations remain unaffected.

---

## Phase 5: User Story 3 - Administrator is granted access (Priority: P1)

**Goal**: An authenticated caller holding `ROLE_ADMIN` is allowed through an `@AdminOnly` operation exactly as if the guard were not present.

**Independent Test**: Call the `@AdminOnly` probe as a signed-in admin and confirm HTTP 200 with the probe's call counter incrementing exactly once per call.

- [ ] T012 [US3] Write test in `src/test/java/com/wikigerminare/security/AdminOnlyAdminAccessTest.java` asserting a request to `/security-test/admin-only` using a valid signed token for an authenticated admin (role=admin, authority `ROLE_ADMIN`, from `AdminOnlyGuardTestSupport`) returns HTTP 200 and the probe's call counter increments by exactly 1
- [ ] T013 [US3] In the same file (`src/test/java/com/wikigerminare/security/AdminOnlyAdminAccessTest.java`), add a test asserting two consecutive admin requests increment the counter to exactly 2 (no accidental double-invocation or caching of the decision), and that the same admin caller can also reach `/security-test/unprotected` normally

**Checkpoint**: User Story 3 is independently verifiable — legitimate administrators are never falsely denied.

---

## Phase 6: User Story 4 - Other APIs reuse the guard without reimplementing checks (Priority: P2)

**Goal**: Prove `@AdminOnly` produces identical 401/403/allow behavior when applied by a different, independent "feature area" with zero custom permission-checking code, and document how real features (`pages-api`, `comments-api`) should adopt it later — without modifying those features now.

**Independent Test**: Apply `@AdminOnly` to a second, independent probe simulating a different feature area; confirm it reproduces the same 401/403/allow matrix as Phases 3-5, using only the annotation (no custom code); confirm adoption documentation exists and matches the real package/import path.

- [ ] T014 [P] [US4] Create a second, independent test-only probe controller simulating a different feature area in `src/test/java/com/wikigerminare/security/otherfeature/AdminOnlyOtherFeatureProbeController.java`, exposing `GET /security-test/other-feature/admin-only` annotated with `@AdminOnly` (imported from `com.wikigerminare.security.AdminOnly`) and incrementing its own call counter — with zero custom permission-checking code beyond the annotation (depends on T002)
- [ ] T015 [US4] Write test in `src/test/java/com/wikigerminare/security/otherfeature/AdminOnlyOtherFeatureProbeTest.java` proving `/security-test/other-feature/admin-only` reproduces the identical matrix already proven in Phases 3-5 — anonymous → 401, member → 403, admin → 200 with counter incrementing — using the same `AdminOnlyGuardTestSupport` helpers (depends on T014, T005)
- [ ] T016 [P] [US4] Write project documentation in `docs/rbac-middleware.md` explaining: what `@AdminOnly` is, the 401/403/allow contract it guarantees, exactly how another backend feature (explicitly naming `pages-api` and `comments-api` as intended future adopters) imports and applies it (`import com.wikigerminare.security.AdminOnly;` + `@AdminOnly` on a controller or service method), what it does NOT do (no change to feature-specific business authorization, no new error-body format), and that adoption is optional and left to each feature's own future work — cross-reference `specs/011-rbac-middleware/contracts/admin-only-guard.md` for the full usage contract
- [ ] T017 [P] [US4] Add a row for this feature to the feature table in `README.md` (`| [011-rbac-middleware](specs/011-rbac-middleware/) | ... |`), following the existing table's format and style, and link to `docs/rbac-middleware.md`

**Checkpoint**: All four user stories are independently functional and verifiable; the guard is proven reusable across more than one feature area without being wired into `pages-api`/`comments-api`.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Regression safety and final validation across all stories.

- [ ] T018 Run the full new/changed security test set: `./mvnw test -Dtest=AdminOnlyAnonymousAccessTest,AdminOnlyMemberAccessTest,AdminOnlyAdminAccessTest,AdminOnlyOtherFeatureProbeTest` and confirm all pass
- [ ] T019 Run the existing security regression suite to confirm `@EnableMethodSecurity` did not change prior behavior: `./mvnw test -Dtest=SecurityConfigTest,AuthSecurityIntegrationTest`
- [ ] T020 Run the full project test suite (`./mvnw test`) and confirm existing admin-related tests are unaffected and still pass unmodified: `ImageControllerTest`, `AdminCommentControllerTest`, `CommentServiceTest`, `AdminCommentServiceTest`, `JwtRoleAuthenticationConverterTest` (FR-009 — no existing feature is rewritten to adopt the guard)
- [ ] T021 [P] Walk through `specs/011-rbac-middleware/quickstart.md` end-to-end and correct any command, path, or expected-result drift found against the tasks actually implemented above

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup (T001) — BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational (Phase 2) completion
  - US1 (Phase 3), US2 (Phase 4), US3 (Phase 5) are all P1 and mutually independent — can proceed in parallel (different test files) once Phase 2 is done
  - US4 (Phase 6) only needs T002 (the annotation) and T005 (test helpers) from Phase 2; it does not depend on the probe controller from T004, so it may start as soon as Phase 2 finishes, in parallel with US1-US3
- **Polish (Phase 7)**: Depends on all of Phases 3-6 being complete

### User Story Dependencies

- **User Story 1 (P1)**: Needs T002, T004, T005 — no dependency on US2/US3/US4
- **User Story 2 (P1)**: Needs T002, T004, T005 — no dependency on US1/US3/US4
- **User Story 3 (P1)**: Needs T002, T004, T005 — no dependency on US1/US2/US4
- **User Story 4 (P2)**: Needs T002, T005 (and, internally, T014 before T015) — no dependency on US1/US2/US3's test files

### Within Each User Story

- T006 → T007 (same file, sequential)
- T008 → T009 → T010 → T011 (same file, sequential)
- T012 → T013 (same file, sequential)
- T014 → T015 (T015 needs T014's controller to exist); T016 and T017 have no code dependency and may run anytime after Phase 2

### Parallel Opportunities

- T002 and T003 (Phase 2) touch different files — run in parallel
- T005 and T004 touch different files — may run in parallel, though T004 itself depends on T002 (needs `@AdminOnly` to annotate the protected probe endpoint)
- Once Phase 2 completes: Phase 3 (US1), Phase 4 (US2), Phase 5 (US3), and Phase 6 (US4) each touch entirely different files and can be worked on in parallel
- T014, T016, T017 (Phase 6) touch different files — run in parallel
- T021 (quickstart validation) can run in parallel with T018-T020 once implementation is complete

---

## Parallel Example: Foundational Phase

```bash
# After T001 completes, launch the two independent foundational tasks together:
Task: "Create AdminOnly annotation in src/main/java/com/wikigerminare/security/AdminOnly.java"
Task: "Enable @EnableMethodSecurity in src/main/java/com/wikigerminare/config/SecurityConfig.java"
```

## Parallel Example: User Stories 1-4 (after Foundational phase)

```bash
# Four different developers/agents, four different test files, zero shared state:
Task: "Anonymous-access tests in src/test/java/com/wikigerminare/security/AdminOnlyAnonymousAccessTest.java"       # US1
Task: "Member-access tests in src/test/java/com/wikigerminare/security/AdminOnlyMemberAccessTest.java"            # US2
Task: "Admin-access tests in src/test/java/com/wikigerminare/security/AdminOnlyAdminAccessTest.java"              # US3
Task: "Second-feature-area probe + docs under src/test/java/com/wikigerminare/security/otherfeature/ and docs/"   # US4
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002-T005) — CRITICAL, blocks all stories
3. Complete Phase 3: User Story 1 (T006-T007)
4. **STOP and VALIDATE**: `./mvnw test -Dtest=AdminOnlyAnonymousAccessTest` passes; anonymous callers are provably denied with 401
5. This alone does not yet prove admin access or member denial — continue to US2/US3 before considering the guard complete, since all three are P1

### Incremental Delivery

1. Setup + Foundational → guard mechanism exists and is activated
2. Add US1 (401) → Add US2 (403) → Add US3 (allow) → the three P1 stories together fully prove the required 401/403/allow matrix
3. Add US4 (P2) → proves reusability across feature areas and documents adoption for `pages-api`/`comments-api`
4. Polish → regression-proves nothing else broke, validates quickstart

### Parallel Team Strategy

With multiple developers/agents:

1. One developer completes Setup + Foundational (T001-T005) — this is the narrow blocking path
2. Once Foundational is done, up to four developers work Phases 3-6 in parallel (different files each)
3. One developer runs Phase 7 polish once all four story phases report their checkpoints complete

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability (US1-US4 from `spec.md`)
- No task in this plan touches `pages-api`, `comments-api`, creates a JWT/login mechanism, creates a `UserService`, or creates a new user/role model — all explicitly out of scope per feature instructions
- `ROLE_ADMIN` (via `hasRole('ADMIN')` in `@AdminOnly`) is the sole canonical admin authority used; no parallel role system is introduced
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
