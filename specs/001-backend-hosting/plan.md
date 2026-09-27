# Implementation Plan: Backend Hosting

**Branch**: `001-backend-hosting` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-backend-hosting/spec.md`

## Summary

Host the Spring Boot API on **AWS Lambda in `sa-east-1`**, next to the Neon database, behind a **Lambda Function URL** on a published alias `live`:
- The whole stack is defined in an **AWS SAM** template and deployed with one command, with rollback by moving the alias.
- **Cold starts**: SnapStart plus CRaC priming, with no DB connection kept in the snapshot and a lean Spring startup.
- **Readiness**: an unauthenticated `GET /health` (Controller → Service → Repository) wakes Neon with `SELECT 1`.
- **Cost guards**:
  - The account's concurrency quota of 10 caps bursts.
  - A scheduled guard function stops the API at 80% of the free limits.
  - A USD 1 budget alerts on any spend.
  - Every component is in Always Free or permanent free limits.

## Technical Context

**Language/Version**: Java 21. The guard function uses Python 3.13 inline in the SAM template (see Complexity Tracking).

**Primary Dependencies**:
- Spring Boot 4.1 (webmvc, data-jpa)
- `aws-serverless-java-container-springboot4` 3.0.2
- `org.crac:crac`
- AWS SAM CLI
- Existing: springdoc-openapi, PostgreSQL driver, Lombok

**Storage**: Neon serverless PostgreSQL, `sa-east-1`, pooled endpoint. No schema changes in this feature.

**Testing**: JUnit 5, Mockito, `@WebMvcTest`, `@SpringBootTest` + Testcontainers PostgreSQL (`@ServiceConnection`). The quickstart covers the cloud-level checks.

**Target Platform**: AWS Lambda, `java21` runtime on `arm64`, Function URL, region `sa-east-1`.

**Project Type**: Web service (REST API, backend repo only).

**Performance Goals**:
- `/health` answers in < 3 s after ≥ 30 min idle (SC-003).
- It answers in < 300 ms when warm (SC-004).

**Constraints**:
- USD 0.00/month (SC-002). No per-hour resources, no VPC/NAT, no API Gateway, no Secrets Manager, no `GetMetricData`.
- 6 MB request limit.
- Not-ready answer in < 10 s (SC-007).
- Logs kept 7 days.

**Scale/Scope**: ≤ 15 users, < 10k requests/month, one function plus one guard function.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against constitution v1.0.0 on `main`, plus v1.0.1 (PR #3, pending review), which only corrects the stack to Java 21.

| Principle | Status | Evidence |
|---|---|---|
| I. Spec as source of truth | ✅ | Spec plus 4 recorded clarifications. Nothing here goes beyond FR-001..FR-016. |
| II. Layered architecture | ✅ | `HealthController` (HTTP only) → `HealthService` (readiness rule, timeout handling) → `HealthRepository` (`SELECT 1`). The Lambda handler and config classes are infrastructure adapters, not business logic. |
| III. REST contracts and DTOs | ✅ | `GET /health` returns the `ReadinessStatus` DTO with 200/503. Contract in `contracts/health.openapi.yaml`. |
| IV. Automated tests | ✅ | Unit (service success/failure/timeout), `@WebMvcTest` (status codes, body, `no-store`), Testcontainers integration (real Postgres up/down). Cloud behaviour is covered by quickstart scenarios. |
| V. Simplicity | ✅ with justification | New dependencies are each required by a requirement: the Lambda adapter (FR-001), `org.crac` (SC-003), Testcontainers (never testing against prod). A plain controller instead of Actuator. Python guard justified below. |
| Technical constraints (Java 21, Spring Boot, PostgreSQL, REST, DTOs) | ✅ | API code is Java 21/Spring Boot. |
| Workflow (feature branch, clear commits, PR + review) | ✅ | Branch `001-backend-hosting`, one commit per SpecKit phase and per task group, PR reviewed by a teammate. |

**Post-design re-check (after Phase 1)**: ✅ No new violations. The contracts, data model and quickstart introduce no business logic outside services and no endpoint without a DTO.

## Project Structure

### Documentation (this feature)

```text
specs/001-backend-hosting/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1: validation/run guide
├── contracts/
│   ├── health.openapi.yaml
│   └── runtime-config.md
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
template.yaml                         # SAM: API function + alias/URL, guard, scheduler, SNS, budget, log groups
samconfig.toml                        # non-secret deploy defaults (stack, region, capabilities)
docs/deployment.md                    # FR-013: prerequisites, deploy, rollback, re-enable, config keys
pom.xml                               # + adapter, org.crac, Testcontainers; `lambda` profile (shade, no Tomcat)

src/main/java/com/wikigerminare/
├── WikigerminareApplication.java
├── controller/HealthController.java
├── service/HealthService.java
├── repository/HealthRepository.java
├── dto/ReadinessStatus.java
├── config/LocalCorsConfig.java       # @Profile("local") only; prod CORS is on the Function URL
└── lambda/
    ├── StreamLambdaHandler.java      # adapter entry point (static init)
    └── SnapStartPriming.java         # org.crac beforeCheckpoint priming

src/main/resources/
├── application.properties            # common, lean startup, Hikari/JPA settings for Lambda
├── application-lambda.properties     # springdoc off, logging
└── application-local.properties      # local CORS origins

src/test/java/com/wikigerminare/
├── TestcontainersConfiguration.java  # @ServiceConnection PostgreSQL
├── TestWikigerminareApplication.java # spring-boot:test-run entry
├── WikigerminareApplicationTests.java
├── controller/HealthControllerTest.java
├── service/HealthServiceTest.java
└── HealthIntegrationTest.java
```

**Structure Decision**: a single Maven project with packages by layer (`controller`, `service`, `repository`, `dto`, `config`), matching the constitution's Controller → Service → Repository flow. Lambda-specific adapters are isolated in `lambda/`, so the rest of the app stays a plain Spring MVC app that teammates can run locally without AWS. Teammates' unpushed features may add packages; `pom.xml` and `application.properties` changes are kept in small, separate commits to ease merges.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Guard function written in Python (inline in `template.yaml`), not Java | FR-015/SC-008 need a scheduled ~40-line ops script that reads metrics and sets concurrency. Inline code keeps it in the one version-controlled template, with no build step and a ~100 ms cold start at 128 MB. | A Java/Spring guard needs a second Maven module or artifact, a heavier cold start, and more GB-s per run. AWS Budgets actions lag 8–24 h and can't change Lambda concurrency. It isn't part of the API, so the Java/Spring constraint (which covers the backend API) isn't weakened. |
| Two CORS mechanisms (Function URL in prod, Spring `@Profile("local")`) | Function URL CORS answers preflights without invoking Lambda (free, no cold start). Local runs have no Function URL. | Spring-only CORS makes every preflight a billed invocation that can trigger a cold start. Function-URL-only CORS breaks local frontend development. |
