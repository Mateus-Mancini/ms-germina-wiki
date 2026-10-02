# Tasks: User Sign-up

**Input**: Design documents from `specs/012-user-signup/`
**Prerequisites**: spec.md, plan.md, research.md, data-model.md, contracts/registration.md
**Tests**: Required by FR-011 and constitution IV. Write tests before implementation.
**Organization**: Tasks grouped by user story; existing single-module paths apply.

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Review constitution 1.0.2, auth/users implementation, schema and ignore patterns in .specify/memory/constitution.md, src/main/resources/db/migration/V1__initial_schema.sql and .gitignore; preserve unrelated working changes.
- [X] T002 Create feature branch feat/user-signup and resolve active feature in .specify/feature.json; generate and review artifacts in specs/012-user-signup/.

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T003 Add failing service and real-security MVC test scaffolding in src/test/java/com/wikigerminare/auth/RegistrationServiceTest.java and RegistrationControllerTest.java.
- [X] T004 Add safe User.registerMember factory in src/main/java/com/wikigerminare/users/User.java with UUID, timestamps, encoded password, fixed member role and null avatar/bio; retain existing mappings.

## Phase 3: User Story 1 - Criar conta e entrar (Priority: P1) MVP

**Goal**: Anonymous member creation and existing login/profile use.
**Independent Test**: Valid signup -> login -> own profile; member denied by administrator guard.

- [X] T005 [P] [US1] Add successful register/login/profile and member authorization scenarios in src/test/java/com/wikigerminare/auth/RegistrationPostgresIntegrationTest.java (FR-001,004,007,008,010,011; SC-001,004).
- [X] T006 [US1] Add RegisterRequest in src/main/java/com/wikigerminare/auth/dto/RegisterRequest.java: name "mandatory, stripped, nonblank, at most 150 characters"; email "mandatory, stripped, valid email, at most 255 characters; case-sensitive"; password "mandatory, nonblank, at least 8 characters and at most 72 bytes in UTF-8; no stripping or truncation; write-only"; mask toString (FR-002,003,005,007).
- [X] T007 [US1] Add transactional RegistrationService in src/main/java/com/wikigerminare/auth/RegistrationService.java using Validator, existing PasswordEncoder, UserRepository and OwnUserProfileResponse; generate UUID/time and persist with saveAndFlush (FR-001..005,007,008).
- [X] T008 [US1] Add 201 and Location controller in src/main/java/com/wikigerminare/auth/RegistrationController.java; expose only POST /api/auth/register anonymously in src/main/java/com/wikigerminare/config/SecurityConfig.java (FR-001,008,010).

## Phase 4: User Story 2 - Corrigir dados inválidos/duplicados (Priority: P2)

**Goal**: Clear errors without creating or changing an account.
**Independent Test**: Invalid/unknown input yields 400; repeated/concurrent email yields 409; original account unchanged.

- [X] T009 [US2] Extend unit/MVC cases for malformed/null/missing/oversize data, unknown fields, whitespace, Unicode boundaries, secrets and different integrity failures in src/test/java/com/wikigerminare/auth/RegistrationServiceTest.java and RegistrationControllerTest.java (FR-002..007,009,011; SC-002,004).
- [X] T010 [P] [US2] Add PostgreSQL duplicate, case-sensitive identity, Unicode limit and simultaneous signup scenarios in src/test/java/com/wikigerminare/auth/RegistrationPostgresIntegrationTest.java (FR-003,005,006,009,011; SC-002,003).
- [X] T011 [US2] Reject unknown fields in src/main/java/com/wikigerminare/auth/dto/RegisterRequest.java; finish Service validation and selectively map users_email_key/23505 collisions in src/main/java/com/wikigerminare/auth/RegistrationService.java (FR-004,006,009).
- [X] T012 [US2] Add domain exceptions in src/main/java/com/wikigerminare/auth/RegistrationValidationException.java and EmailAlreadyRegisteredException.java; map to generic 400 and 409 in src/main/java/com/wikigerminare/auth/AuthExceptionHandler.java (FR-006,007,009).

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T013 Document registration and subsequent login in README.md and verify specs/012-user-signup/quickstart.md.
- [X] T014 Execute unit/MVC and existing auth/users/RBAC regression tests; record outcomes in specs/012-user-signup/tasks.md (FR-010,011; SC-004).
- [X] T015 Execute PostgreSQL registration/concurrency and full Maven suite per specs/012-user-signup/quickstart.md; record any environment limitation honestly in specs/012-user-signup/tasks.md (FR-011; SC-001..004).
- [X] T016 Review final diff and requirement coverage against specs/012-user-signup/spec.md, plan.md and tasks.md; verify .specify/feature.json points to this feature and constitution remains unchanged.

## Dependencies & Execution Order

T001 -> T002 -> T003 -> T004 -> US1 (T005..T008) -> US2 (T009..T012) -> polish (T013..T016). Within each story tests precede corresponding production code. US2 extends US1 while its error outcomes are independently testable. Complete verification before marking verification tasks done.

## Parallel Opportunities

US1: PostgreSQL success tests T005 and DTO work T006 affect different files after scaffolding. US2: T009 unit/MVC tests and T010 PostgreSQL tests can be authored independently. Shared Service/Controller files must be edited sequentially; no additional agent delegation is required for implementation.

## Implementation Strategy

Deliver anonymous member creation first; validate the existing login/profile path. Then complete invalid input, duplicate race and privacy behavior before considering the feature ready. No deployment or integration to main in this task.

## Requirement Coverage

| Requirements/outcomes | Tasks |
|---|---|
| FR-001, FR-008 | T005, T007, T008 |
| FR-002, FR-003, FR-005 | T006, T007, T009, T010 |
| FR-004, FR-007 | T004, T005, T006, T009, T011, T012 |
| FR-006, FR-009 | T009, T010, T011, T012 |
| FR-010 | T005, T008, T014 |
| FR-011, SC-001..004 | T003, T005, T009, T010, T014, T015 |

## Validation Results

Completed on 2026-10-01 on branch feat/user-signup. All 16 tasks complete.

- Pre-implementation test run failed compilation because the new registration types did not yet exist (expected red stage).
- Registration tests: 22 passed (11 service, 5 real-security MVC, 6 PostgreSQL integration), including concurrent one-201/one-409 creation, stored BCrypt, member-only permissions and subsequent login/profile.
- Full Maven suite: 286 tests, 0 failures, 0 errors, 3 configured skips; BUILD SUCCESS. PostgreSQL 18 and MinIO ran via Docker; Flyway applied V1/V2 to disposable databases.
- Runtime used installed Java 22 with Maven release 21. Environment failures in existing health/storage tests were independently reproduced with a standalone Selector/HttpClient probe and resolved by directing Unix-domain sockets to the writable target directory. The final test command used the existing local Maven cache and -DargLine=-Djdk.net.unixdomain.tmpdir=C:/germinawiki/ms-germina-wiki/target. No production setting, dependency or test assertion was changed for this workaround.
- Final review: 11/11 functional requirements and 4/4 success criteria covered; checklist 16/16 passing; no constitution violations, unresolved clarification or new migration; diff whitespace checks passed. No Spec Kit extension hooks are registered.
