---

description: "Task list for 001-backend-hosting"
---

# Tasks: Backend Hosting

**Input**: Design documents from `/specs/001-backend-hosting/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. Constitution principle IV requires automated tests for every new feature. Tests are written first and must fail before implementation.

**Organization**: Tasks are grouped by user story so each story can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story from spec.md (US1–US4)
- Paths are relative to the repository root (single Maven project, per plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencies and build configuration for running on Lambda and testing without the prod DB

- [X] T001 Add `com.amazonaws.serverless:aws-serverless-java-container-springboot4:3.0.2` and `org.crac:crac` dependencies in `pom.xml` (research R2, R3)
- [X] T002 Add Boot-managed test dependencies `spring-boot-testcontainers` and Testcontainers PostgreSQL + JUnit Jupiter (no explicit versions) in `pom.xml` (research R14)
- [X] T003 Add Maven profile `lambda` in `pom.xml` plus descriptor `src/assembly/lambda.xml`: `maven-assembly-plugin` producing `target/wikigerminare-lambda.zip` (classes at root, dependencies in `lib/`), with embedded Tomcat, Boot Tomcat modules and Lombok excluded (research R2)
- [X] T004 [P] Add a `.gitignore` entry for `.aws-sam/` and `samconfig.local.toml` in `.gitignore`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Test harness, Lambda entry point and base SAM stack that every story builds on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 Create `src/test/java/com/wikigerminare/TestcontainersConfiguration.java` exposing a `@ServiceConnection` `PostgreSQLContainer` bean
- [X] T006 Update `src/test/java/com/wikigerminare/WikigerminareApplicationTests.java` to `@Import(TestcontainersConfiguration.class)` so `contextLoads` never needs the prod DB
- [X] T007 [P] Create `src/test/java/com/wikigerminare/TestWikigerminareApplication.java` so `./mvnw spring-boot:test-run` starts the app with a throwaway Postgres
- [X] T008 Set common, Lambda-safe settings in `src/main/resources/application.properties`:
  - `spring.jpa.open-in-view=false`
  - `spring.jpa.hibernate.ddl-auto=none`
  - explicit `PostgreSQLDialect`
  - `hibernate.boot.allow_jdbc_metadata_access=false`
  - Hikari `maximum-pool-size=2`, `minimum-idle=0`, `connection-timeout=5000`, `allow-pool-suspension=true`
  - `spring.main.banner-mode=off`

  (research R3, R10)
- [X] T009 [P] Create `src/main/resources/application-lambda.properties` (springdoc api-docs and swagger-ui disabled; log levels) and `src/main/resources/application-local.properties`
- [X] T010 Create `src/main/java/com/wikigerminare/lambda/StreamLambdaHandler.java`, a `RequestStreamHandler` with the Spring Boot 4 container handler initialised in a static block (research R2)
- [X] T011 Create base `template.yaml`:
  - parameters `DbUrl`, `DbUsername`, `DbPassword` (NoEcho), `WebAppOrigin`, `AlertEmail`
  - `ApiFunction`: `java21`, `arm64`, 2048 MB, 20 s timeout, handler `StreamLambdaHandler`, `CodeUri: target/wikigerminare-lambda.zip`
  - env vars `SPRING_DATASOURCE_*` and `SPRING_PROFILES_ACTIVE=lambda`
  - `AutoPublishAlias: live`, no VPC
  - `AWS::Logs::LogGroup` with `RetentionInDays: 7` (FR-005, FR-007, FR-008, FR-012)
- [X] T012 [P] Create `samconfig.toml` with non-secret defaults: stack `ms-germina-wiki`, region `sa-east-1`, `capabilities = CAPABILITY_IAM`, `resolve_s3 = true`, `confirm_changeset = true`, and no parameters (CLI overrides replace them; contracts/runtime-config.md)

**Checkpoint**: `./mvnw test` passes on Testcontainers; `./mvnw -Plambda package` builds the Lambda jar; `sam validate --lint` passes

---

## Phase 3: User Story 1 - The web app reaches a live API in production (Priority: P1) 🎯 MVP

**Goal**: The API is reachable over HTTPS at a stable address, and callable by browsers only from allowed origins

**Independent Test**: quickstart §3. Allowed origins get `Access-Control-Allow-Origin`, `https://evil.example` does not, and plain HTTP isn't served

- [ ] T013 [US1] Add `FunctionUrlConfig` to `ApiFunction` in `template.yaml`: `AuthType: NONE`, CORS `AllowOrigins: [!Ref WebAppOrigin, http://localhost:3000]`, methods GET/POST/PUT/PATCH/DELETE, headers `Authorization`, `Content-Type` (FR-001, FR-002, research R4, R5)
- [ ] T014 [US1] Add stack outputs `ApiUrl` (alias `live` Function URL), `ApiFunctionName`, `LiveVersion` in `template.yaml`
- [ ] T015 [P] [US1] Write `src/test/java/com/wikigerminare/config/LocalCorsConfigTest.java`: under profile `local`, a preflight from `http://localhost:3000` is allowed and one from `https://evil.example` is rejected
- [ ] T016 [US1] Create `src/main/java/com/wikigerminare/config/LocalCorsConfig.java`, a `@Profile("local")` `WebMvcConfigurer` reading origins from `app.cors.allowed-origins` in `application-local.properties` (plan Complexity Tracking)
- [ ] T017 [US1] First deploy (owner) per quickstart §2, then apply the 1-day lifecycle rule to the SAM artifacts bucket and validate reachability and CORS per quickstart §3; record results in the PR description

**Checkpoint**: US1 is live and verifiable with curl

---

## Phase 4: User Story 2 - The web app knows when the backend is ready (Priority: P1)

**Goal**: `GET /health` returns `200 {"status":"ready"}` only when the DB answers, and `503 {"status":"not_ready"}` in under 10 s otherwise

**Independent Test**: quickstart §1. With Postgres up the check is `200 ready`; with it stopped, `503 not_ready` in < 10 s; no internal details in either body

### Tests for User Story 2 (write first, must fail)

- [ ] T018 [P] [US2] Write `src/test/java/com/wikigerminare/service/HealthServiceTest.java` (Mockito) covering three cases: the repository succeeds → `ready`; it throws → `not_ready`; it exceeds the timeout → `not_ready`
- [ ] T019 [P] [US2] Write `src/test/java/com/wikigerminare/controller/HealthControllerTest.java` (`@WebMvcTest`). It asserts 200 and 503, a body of exactly `{"status":"ready"}` or `{"status":"not_ready"}` (`status`: "`ready` or `not_ready`. There are no other fields."), `Cache-Control: no-store`, and no auth required
- [ ] T020 [P] [US2] Write `src/test/java/com/wikigerminare/HealthIntegrationTest.java` (`@SpringBootTest` + Testcontainers). It expects 200 `ready` against a live container and 503 `not_ready` in under 10 s after the container is stopped

### Implementation for User Story 2

- [ ] T021 [P] [US2] Create `src/main/java/com/wikigerminare/dto/ReadinessStatus.java`, a record with a single field `status` serialised as `ready` / `not_ready` (data-model.md)
- [ ] T022 [P] [US2] Create `src/main/java/com/wikigerminare/repository/HealthRepository.java`, which runs `SELECT 1` via `JdbcTemplate` with a 3 s query timeout (research R10, R13)
- [ ] T023 [US2] Create `src/main/java/com/wikigerminare/service/HealthService.java`, which maps a repository success to `ready` and any exception or timeout to `not_ready`, logging the cause at WARN without exposing it (FR-003, FR-004)
- [ ] T024 [US2] Create `src/main/java/com/wikigerminare/controller/HealthController.java`: `GET /health` returns 200 or 503 with `Cache-Control: no-store` (contracts/health.openapi.yaml)
- [ ] T025 [US2] Run `./mvnw test`, confirm T018–T020 pass, and redeploy. Validate `/health` in production per quickstart §3 and SC-007

**Checkpoint**: MVP complete. The web app can reach the API (US1) and knows when it's ready (US2)

---

## Phase 5: User Story 3 - The first visit after idle still feels quick (Priority: P2)

**Goal**: < 3 s for the first request after ≥ 30 min idle; < 300 ms when warm

**Independent Test**: quickstart §4, cold and warm `curl -w` timings over 10 cold occasions

- [ ] T026 [US3] Enable `SnapStart: ApplyOn: PublishedVersions` on `ApiFunction` in `template.yaml` (research R3)
- [ ] T027 [P] [US3] Write `src/test/java/com/wikigerminare/lambda/SnapStartPrimingTest.java`. It asserts that priming completes without opening any DB connection (Hikari active + idle connections = 0 after `beforeCheckpoint`)
- [ ] T028 [US3] Create `src/main/java/com/wikigerminare/lambda/SnapStartPriming.java`, an `org.crac.Resource` registered in `StreamLambdaHandler` whose `beforeCheckpoint` sends one synthetic request to an unmapped path (404 error path, never `/health`, which touches the DB) through the container handler, and serialises an in-memory `ReadinessStatus` with Jackson (research R3)
- [ ] T029 [US3] Redeploy and measure per quickstart §4. Record `Restore Duration` and cold/warm timings in `specs/001-backend-hosting/quickstart.md` under a "Measured results" section, and adjust memory in `template.yaml` only if the data justifies it

**Checkpoint**: SC-003 and SC-004 measured and met, or the deviation is documented for escalation (research R3, native image)

---

## Phase 6: User Story 4 - The team can deploy, update and roll back safely at no cost (Priority: P2)

**Goal**: One-command deploy, fast rollback, burst cap, automatic shutoff at 80% of free limits, USD 1 budget alert

**Independent Test**: quickstart §5–§7: rollback, 429 under flood, simulated shutoff and email, budget present

- [ ] T030 [P] [US4] Add an `AWS::SNS::Topic` with an email subscription to `!Ref AlertEmail` in `template.yaml`
- [ ] T031 [P] [US4] Add an `AWS::Budgets::Budget` `germinawiki-monthly` of USD 1 with ACTUAL > 100% and FORECASTED > 100% email notifications in `template.yaml` (FR-010, research R8)
- [ ] T032 [US4] Add `GuardFunction` in `template.yaml`: Python 3.13 inline, 128 MB, 7-day log group, IAM scoped to `cloudwatch:GetMetricStatistics`, `lambda:PutFunctionConcurrency` on `ApiFunction` only and `sns:Publish` on the topic. It computes month-to-date requests and GB-s and stops the API at ≥ 80%. It accepts `thresholdOverride` in the event for testing, and never calls `GetMetricData` (FR-015, research R7)
- [ ] T033 [US4] Add an `AWS::Scheduler::Schedule` `rate(10 minutes)` targeting `GuardFunction`, with its execution role, in `template.yaml` (SC-008)
- [ ] T034 [US4] Validate the burst cap and the shutoff per quickstart §6, including R7-a (reserved concurrency 0 is allowed) and R7-b (a redeploy doesn't clear it). If R7-a fails, implement the `AuthType: AWS_IAM` fallback in `GuardFunction` and update research.md
- [ ] T035 [P] [US4] Write `docs/deployment.md` (FR-013, FR-016): one-time account setup (IAM admin user with MFA, `aws login`, no access keys, no everyday root use, both USD 1 budgets), prerequisites, deploy, rollback, re-enable after shutoff, runtime configuration keys, and the S3 artifacts-bucket 1-day lifecycle command (research R11)
- [ ] T036 [US4] Validate rollback per quickstart §5 (SC-006) and a from-scratch deploy following `docs/deployment.md` only (SC-005)

**Checkpoint**: All four user stories are independently functional in production

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T037 [P] Replace the placeholder `README.md` with a project overview, local run (`./mvnw spring-boot:test-run`), tests, and a link to `docs/deployment.md`
- [ ] T038 Run the full quickstart (§1–§7) end to end and tick the results in the PR description
- [ ] T039 Review cost after deploy: Billing shows USD 0.00, the budget exists, log retention is 7 days, and the only EventBridge schedule targets the guard function, never the API (FR-011, SC-002, quickstart §7)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → user stories → **Polish (7)**
- **US1 (3)** and **US2 (4)** both depend only on Foundational. The code for US2 can be built before US1, but its production validation (T025) needs the US1 deploy (T017)
- **US3 (5)** depends on US2, because priming exercises the real request path and cold timings are measured on `/health`
- **US4 (6)** depends on US1 (a deployed stack). T030/T031/T035 can start as soon as Foundational is done

### Within Each Story

- Tests are written first and must fail. Then DTO/repository → service → controller → deploy/validate
- Each task, or tightly related group, is one Conventional Commit referencing its task ID(s)

### Parallel Opportunities

- Setup: T004 alongside T001–T003
- Foundational: T007, T009 and T012 in parallel after T005/T008 start
- US2: T018, T019 and T020 (tests) in parallel; T021 and T022 in parallel
- US4: T030, T031 and T035 in parallel

### Parallel Example: User Story 2

```text
Task: "T018 HealthServiceTest in src/test/java/com/wikigerminare/service/HealthServiceTest.java"
Task: "T019 HealthControllerTest in src/test/java/com/wikigerminare/controller/HealthControllerTest.java"
Task: "T020 HealthIntegrationTest in src/test/java/com/wikigerminare/HealthIntegrationTest.java"
```

---

## Implementation Strategy

### MVP First (US1 + US2, both P1)

1. Phase 1 → Phase 2 (all local, no AWS account needed)
2. Phase 4 (US2) code and tests locally, since this also needs no AWS account
3. Phase 3 (US1) once the AWS account exists → first deploy
4. Validate US2 in production (T025) → **MVP**: the web app can reach the API and detect readiness

### Incremental Delivery

- US3 → measured cold starts
- US4 → cost guards and rollback proven
- Polish → README and full quickstart run

### Notes

- Tasks that need the AWS account: T017, T025 (prod part), T029, T034, T036, T038, T039. Everything else can be done now.
- There is only production. Never run tests against Neon, and deploy only from reviewed commits.
