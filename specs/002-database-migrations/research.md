# Research: Database Migrations

**Feature**: `002-database-migrations` | **Date**: 2026-09-27

## R1. Current database state (verified)

- Production (Neon project in `aws-sa-east-1`, database `neondb`, default branch) runs **PostgreSQL 18.6** and is **empty**: no tables, enums, triggers or migration-history table. The only extension is `plpgsql`.
- The team's `db-script` (Camilla) creates 8 tables, 2 enums, the `pgcrypto` extension, 31 indexes (including PKs, uniques and the GIN full-text index), 1 function and 4 triggers. It was verified to apply cleanly on `postgres:18-alpine`, and is **not re-runnable** (a second run fails with `type "user_role" already exists`).
- **Consequence**: no baseline is needed. V1 is applied from scratch.

## R2. Migration tool

- **Decision**: **Flyway** Community 12.4.0, managed by the Spring Boot 4.1 BOM, with `flyway-database-postgresql`.
- **Rationale**:
  - Plain versioned SQL files (`V1__…sql`). The team's script becomes V1 unchanged (FR-002).
  - Checksums plus `validateOnMigrate` reject edited or duplicate migrations (FR-005).
  - Each migration runs in a transaction on PostgreSQL, where DDL is transactional (FR-006).
  - A `flyway_schema_history` table records applied migrations (FR-003).
  - Flyway supports PostgreSQL up to 18.
- **Alternatives considered**:
  - Liquibase: XML/YAML changelogs add a layer over SQL the team already writes.
  - Hibernate `ddl-auto`: no history, and not safe for production.
  - Hand-run scripts (status quo): not repeatable and no record.

## R3. Where migrations run

- **Decision**:
  - **Tests and local runs**: Spring Boot's Flyway auto-configuration migrates every Testcontainers database at context start. The Flyway starter is added in **`test` scope only**.
  - **Production**: the **`flyway-maven-plugin`** (Boot-managed 12.4.0) is run explicitly by the owner through `scripts/db-migrate.sh`, and later by CI (feature 003).
  - **API (Lambda)**: Flyway isn't on the runtime classpath at all, so the API can't migrate at startup (FR-008), and neither the Lambda zip nor SnapStart init is affected (SC-007).
- **Alternatives considered**:
  - Migrate on Lambda init: it would run at SnapStart publish and on every non-snapshot cold start, couple deploys to schema changes, and open DB connections before the snapshot.
  - A separate "migrator" Lambda: more infrastructure for no gain until CI exists.

## R4. Connection used for migrations

- **Decision**: migrations use Neon's **direct** endpoint (the pooled host with `-pooler` removed). The API keeps the pooled endpoint.
- **Rationale**: the pooler is PgBouncer in transaction mode. Flyway takes a session-level advisory lock to serialise concurrent runs, and session state doesn't survive transaction pooling. Neon recommends direct connections for schema migrations.
- **Credentials**: the same role and password already in the owner's secrets file (`DB_USER`/`DB_PASS`). The direct URL is derived from `DB_URL`, so no new secret is added.

## R5. Rehearsal on a disposable copy (FR-010)

- **Decision**: **Neon branches**, which are copy-on-write clones of `main`, created per rehearsal with `neonctl branches create --parent main --expires-at <now+1h>`. Migrations run against the branch's direct endpoint, and the branch is deleted immediately afterwards; the expiry is a safety net if the script dies.
- **Rationale**: the free plan includes 10 branches per project at no cost (SC-006). A branch has production's exact schema and data, so a rehearsal is a faithful preview.
- **Prerequisites**: `neonctl` authenticated, plus a new non-secret `NEON_PROJECT_ID` in the owner's secrets file.

## R6. "Only from main" guard (FR-009, clarification)

- **Decision**: `scripts/db-migrate.sh production` refuses to run unless:
  - the current branch is `main`
  - the working tree is clean
  - `HEAD` equals `origin/main` after a fetch.

  It then shows `flyway info`, asks for an explicit `yes`, and runs `migrate`. `rehearse` works from any branch.
- **Rationale**: with only production, the database schema must never get ahead of what's merged. CI (003) will run the same script from `main`.

## R7. Tests on the real schema (FR-007, SC-003)

- **Decision**:
  - Testcontainers image `postgres:18-alpine`, in both the shared `TestcontainersConfiguration` and the integration test, to match production's major version.
  - A new `SchemaMigrationTest` asserts that the migrated catalog matches V1: 8 tables, the 2 enum types with their labels, `pgcrypto`, the 4 `updated_at` triggers, the full-text GIN index, and `flyway_schema_history` at version 1.
- **Broken-migration check**: verified manually in the quickstart (add a temporary `V999__broken.sql` and expect the suite to fail at context start). Keeping a permanently broken fixture in the repo would break the build.

## R8. Preserving authorship (FR-012)

- **Decision**: the commit that adds `V1__initial_schema.sql` carries `Co-authored-by: CamillaMorenoA <178440498+CamillaMorenoA@users.noreply.github.com>`. The file header credits the original `db-script`.
