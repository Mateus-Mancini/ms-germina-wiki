# Research: Backend Hosting

**Feature**: `001-backend-hosting` | **Date**: 2026-09-27

Each entry records a decision, why it was made, and what else was considered. All prices and limits were checked in September 2026 for an AWS account created **after 2025-07-15**. Such accounts have the credit-based Free Plan and the permanent "Always Free" limits, but none of the legacy 12-month free tier.

## R1. Compute platform

- **Decision**: AWS Lambda, `java21` managed runtime, `arm64`, region `sa-east-1` (São Paulo).
- **Rationale**: Lambda is Always Free (1M requests + 400,000 GB-s per month), and the allowance applies account-wide across regions. `sa-east-1` is where the Neon database lives (FR-008), which keeps DB round-trips short. `arm64` supports SnapStart and costs less if the free tier is ever exceeded.
- **Alternatives considered**:
  - EC2 or Lightsail: billed per hour whether idle or not (violates FR-009).
  - App Runner or Fargate: not free.
  - Running in `us-east-1` next to other services: adds ~120 ms per DB round-trip to São Paulo.

## R2. Running Spring Boot 4 inside Lambda

- **Decision**: `com.amazonaws.serverless:aws-serverless-java-container-springboot4:3.0.2` (latest patch of the 3.0 line), with a `RequestStreamHandler` whose container handler is initialised in a static block.
- **Rationale**: It's the official AWS adapter, tested against Spring Boot 4 / Spring Framework 7. The app stays a normal Spring MVC app: controllers are unchanged and it still runs locally with embedded Tomcat.
- **Packaging**: a Maven profile `lambda` builds `target/wikigerminare-lambda.zip` with `maven-assembly-plugin`: compiled classes at the root and dependency jars in `lib/`. Embedded Tomcat and Boot's Tomcat modules are **excluded**, because the adapter replaces them. The default build keeps Tomcat, so `./mvnw spring-boot:run` keeps working. A shaded uber-jar was rejected: Boot 4 splits auto-configuration across many modules whose `META-INF/spring/*.imports` and `spring.factories` files would have to be merged exactly, or configuration is silently lost. AWS also recommends the `lib/` zip layout for Java functions.
- **Alternatives considered**:
  - AWS Lambda Web Adapter (runs Tomcat inside Lambda): simpler, but cold starts are slower and it has known SnapStart hook issues.
  - Spring Cloud Function: requires rewriting endpoints as functions, which conflicts with the REST + Controller layering in the constitution.

## R3. Cold-start strategy

- **Decision**, in priority order:
  1. **SnapStart** (`ApplyOn: PublishedVersions`). It is free for Java. Traffic only ever hits a published alias (`live`).
  2. **Priming** in an `org.crac` `beforeCheckpoint` hook. It pushes one synthetic request that doesn't touch the DB through the adapter, which exercises the DispatcherServlet, the Jackson serializers and the error path. Class loading and JIT then happen at publish time instead of on the user's request.
  3. **No DB connection in the snapshot**:
     - `spring.datasource.hikari.allow-pool-suspension=true`, with `org.crac:crac` on the classpath. Spring Boot then auto-configures `HikariCheckpointRestoreLifecycle`, which suspends the pool and evicts its connections on checkpoint and resumes it on restore.
     - Hibernate is configured not to touch the DB at boot: `hibernate.boot.allow_jdbc_metadata_access=false`, an explicit `PostgreSQLDialect`, and `ddl-auto=none`. Publishing a version then doesn't need the DB, and no connection exists when the snapshot is taken.
  4. **Memory 2048 MB**. Lambda allocates CPU in proportion to memory, which speeds up the first post-restore invocations. Expected load (< 10k requests/month × ~0.3 s × 2 GB ≈ 6,000 GB-s) is 1.5% of the free allowance.
  5. **Lean startup**: `spring.jpa.open-in-view=false`, `spring.main.banner-mode=off`, springdoc/Swagger UI disabled in the `lambda` profile, and Tomcat excluded from the Lambda jar.
- **Measurement**: `Restore Duration` and `Duration` from the CloudWatch `REPORT` log lines, plus end-to-end timing with `curl -w` (see quickstart). SC-003 budgets < 3 s after ≥ 30 min idle. That covers Neon's wake-up (~0.5–1 s in-region), SnapStart restore (~0.2–0.6 s) and the first request.
- **Escalation, deferred**: a GraalVM native image (Spring AOT) on `provided.al2023`. It would only be considered if SC-003 fails after measurement, because it adds a heavy build and CI burden.
- **Rejected**:
  - Scheduled keep-warm pings: they would keep Neon awake and burn its 100 CU-hours/month (violates FR-011).
  - Provisioned concurrency: billed hourly (violates FR-009).

## R4. Public HTTPS entry point

- **Decision**: a **Lambda Function URL** bound to the `live` alias, `AuthType: NONE`.
- **Rationale**: Function URLs cost nothing beyond the Lambda invocation. Their address is stable across deploys (FR-001), because it's tied to the alias and not the version. They are HTTPS-only (plain HTTP isn't served, satisfying US1-AS3). The payload limit is 6 MB, which covers every JSON endpoint, since images go directly to storage.
- **Alternatives considered**: API Gateway HTTP API. It isn't in Always Free, so on this account it would consume credits and then bill.

## R5. CORS (FR-002)

- **Decision**: in production, CORS is handled by the **Function URL's CORS config**: `AllowOrigins` = [web app origin, `http://localhost:3000`]. In the `local` Spring profile only, the same origins are configured in Spring MVC for local runs. The two mechanisms never run together, to avoid duplicate headers.
- **Rationale**: Lambda answers Function URL preflight (`OPTIONS`) requests without invoking the function. That's faster, free, and doesn't count toward concurrency.
- **Open detail**: whether throttled (429) responses carry CORS headers. If they don't, the browser surfaces a network error. The web app must treat network errors and 429 alike as "retry" (noted in the contract).

## R6. Concurrency cap (FR-014)

- **Finding**: new accounts start with an **account-wide concurrency quota of 10**. Lambda also requires ≥ 100 unreserved concurrency before any reserved concurrency can be set, so per-function reserved concurrency **can't be configured** on this account yet.
- **Decision**: the account quota of 10 **is** the cap. Requests beyond 10 concurrent are throttled, and the Function URL returns `429` without invoking or billing. The quickstart verifies the quota. If AWS raises it later, add `ReservedConcurrentExecutions: 5` to the function in the template.
- **Alternative considered**: requesting a quota increase. Rejected, since it would remove the cap we want.

## R7. Automatic shutoff (FR-015, SC-008)

- **Decision**: a small **guard function**, Python 3.13 in `infra/guard/guard.py` (packaged by SAM from that folder, with unit tests alongside), 128 MB, triggered **every 10 minutes by EventBridge Scheduler**. On each run it:
  1. reads month-to-date `AWS/Lambda` `Invocations` (account aggregate) and `Duration` per function via **`GetMetricStatistics`** (daily periods from the 1st of the month)
  2. computes requests and GB-s against the 1M / 400,000 limits
  3. at ≥ 80% of either limit, calls `PutFunctionConcurrency(ReservedConcurrentExecutions=0)` on the API function and publishes to an **SNS topic** with an email subscription.
  Re-enabling is a documented `aws lambda delete-function-concurrency` run by the account owner.
- **Rationale**:
  - EventBridge Scheduler: 14M invocations/month free (we use ~4,400).
  - SNS: 1,000 emails free.
  - `GetMetricStatistics` is covered by CloudWatch's 1M free API requests. **`GetMetricData` is not**: it's billed per metric requested, so it's deliberately avoided.
  - A 10-minute schedule meets the 15-minute bound in SC-008.
  - A concurrency of 0 makes the Function URL return 429 without invoking anything.
  - Python avoids a second Maven module and a Java cold start for a ~60-line ops script (see Complexity Tracking in `plan.md`). It lives in its own file rather than inline in the template so it can be unit-tested (constitution IV).
- **To verify during implementation**:
  - (a) Setting reserved concurrency to **0** is allowed under the 10-quota rule. It leaves unreserved concurrency at 10, the account minimum. **Fallback**: switch the alias Function URL to `AuthType: AWS_IAM`, which makes public calls get 403 without invoking.
  - (b) A later `sam deploy` doesn't silently clear the shutoff, since `ReservedConcurrentExecutions` isn't declared in the template. The quickstart has a check for this.
- **Alternative considered**: AWS Budgets actions. Rejected because billing data lags 8–24 h, which is too slow for SC-008, and the actions can't change Lambda concurrency directly.

## R8. Spending alert (FR-010)

- **Decision**: an `AWS::Budgets::Budget` of USD 1/month in the SAM template, with two email notifications: ACTUAL > 100% and FORECASTED > 100%. The account owner also keeps AWS's built-in Free Tier usage alerts enabled (Billing preferences).
- **Rationale**: Budgets without actions are free, and it's version-controlled (FR-006).

## R9. Secrets and runtime configuration (FR-005)

- **Decision**: DB credentials are passed as **`NoEcho` SAM parameters** at deploy time and land in Lambda environment variables, encrypted at rest with the AWS-managed key (free). Spring reads them via relaxed binding (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Locally, values come from the developer's shell or a git-ignored `samconfig` override. In CI (feature 002) they come from GitHub encrypted secrets.
- **Alternatives considered**:
  - Secrets Manager: USD 0.40/secret/month (violates FR-009).
  - SSM Parameter Store SecureString is free, but CloudFormation can't resolve `ssm-secure` into Lambda env vars, and fetching at runtime would add latency or bake the secret into the snapshot anyway.

## R10. Database connection from Lambda

- **Decision**: Neon **pooled** endpoint (`-pooler` host), `sslmode=require`, Hikari `maximum-pool-size=2`, `minimum-idle=0`, `connection-timeout=5000` ms, `max-lifetime` below Neon's idle cutoff. The readiness query uses a 3 s statement timeout. The function is **not** attached to a VPC.
- **Rationale**:
  - Each Lambda instance serves one request at a time, so 2 connections is enough, and 10 instances × 2 = 20 stays far below the pooler's limits.
  - The timeouts keep "not ready" answers under 10 s (SC-007) while leaving room for Neon's wake-up.
  - A VPC would need a NAT Gateway (~USD 32/month) to reach Neon.

## R11. Deployment tooling, versions and rollback (FR-006, FR-007, SC-005, SC-006)

- **Decision**: **AWS SAM**, with `template.yaml` and `samconfig.toml` at the repo root, and `AutoPublishAlias: live` so every deploy publishes a new immutable version (FR-007). To roll back, the owner runs `aws lambda update-alias --name live --function-version <previous>`. That takes seconds (SC-006), and the Function URL follows the alias.
- **Cost note**: CloudFormation needs code in S3. The SAM-managed artifacts bucket gets a **1-day expiration lifecycle rule**. A ~40 MB jar kept for a day costs < USD 0.0001 per deploy, which rounds to USD 0.00 on the bill. There is no Lambda code path that avoids S3 while staying in CloudFormation.
- **Alternatives considered**:
  - Terraform: needs state storage (S3/Terraform Cloud) and is less native for Lambda + SnapStart.
  - CDK: needs a Node toolchain and a bootstrap stack. SAM is simpler for a single function.

## R12. Logging (FR-012)

- **Decision**: an explicit `AWS::Logs::LogGroup` per function with `RetentionInDays: 7`, and Lambda's **Text** log format. The JSON format was rejected: its system-level filter at `WARN` would drop the `REPORT` lines that carry `Restore Duration` and `Duration`, which we need to measure SC-003 and SC-004. Spring's own log levels control application verbosity (`lambda` profile).
- **Rationale**: 7 days per the clarification. The 5 GB/month ingestion is free, and there's a large margin at this traffic.

## R13. Readiness check design (FR-003, FR-004)

- **Decision**: `GET /health`, unauthenticated, runs `SELECT 1` through a repository.
  - Success: `200 {"status":"ready"}`.
  - Failure or timeout: `503 {"status":"not_ready"}`.
  - Both carry `Cache-Control: no-store`. No versions, hostnames or exception text in the body; errors are only logged.
- **Rationale**: it's a plain controller, per the constitution's minimal-dependency rule. Actuator would add a dependency and extra startup work, and it exposes more surface than needed.

## R14. Testing without touching production

- **Decision**:
  - JUnit 5 + Mockito unit tests for the service.
  - `@WebMvcTest` for the controller contract.
  - A `@SpringBootTest` integration test against **Testcontainers PostgreSQL** via `@ServiceConnection`. The existing `contextLoads` test also switches to Testcontainers, so no test ever needs the Neon prod DB.
  - A `TestWikigerminareApplication` enables `./mvnw spring-boot:test-run`, which starts a local app with a throwaway Postgres.
- **Note**: Spring Boot 4 manages Testcontainers 2.x, whose artifacts were renamed (e.g. `testcontainers-postgresql`). Use the Boot-managed coordinates without versions.
