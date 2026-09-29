# Implementation Plan: Database Migrations

**Branch**: `002-database-migrations` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-database-migrations/spec.md`

## Summary

Put the schema under **Flyway** (Boot-managed 12.4.0):
- **V1**: the team's `db-script` copied verbatim, credited to its author.
- **Tests and local runs**: Flyway is added in test scope only and migrates every Testcontainers database (`postgres:18-alpine`, matching Neon's 18.6). A `SchemaMigrationTest` checks the resulting catalog.
- **Production**: migrations are applied only by `scripts/db-migrate.sh`, which wraps `flyway-maven-plugin`. It uses Neon's **direct** endpoint, rehearses on a self-expiring **Neon branch**, and applies to production only from an up-to-date, clean `main` after typing `yes`.
- **API**: the Lambda never contains Flyway.

## Technical Context

**Language/Version**: Java 21 (tests); Bash + SQL (migrations and script)

**Primary Dependencies**: Flyway 12.4.0 via `spring-boot-starter-flyway` (test scope) + `flyway-database-postgresql`; `flyway-maven-plugin` 12.4.0; `neonctl` (owner machine and CI only)

**Storage**: Neon PostgreSQL 18.6 (`sa-east-1`). Migrations use the direct endpoint; the API keeps the pooled one.

**Testing**: JUnit 5 + Testcontainers `postgres:18-alpine`; `SchemaMigrationTest` for catalog verification; quickstart for rehearsal, production and guard checks

**Target Platform**: the owner's Linux machine now, GitHub Actions in feature 003; the target database is Neon

**Project Type**: Web service (backend repo), infrastructure change only; no new endpoints

**Performance Goals**: re-running an up-to-date migration < 30 s (SC-002); no change to API cold start (SC-007)

**Constraints**: USD 0; production only; apply to production only from `main`; `flyway clean` disabled everywhere

**Scale/Scope**: 1 migration now (8 tables); expected to grow as teammates' features land

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| I. Spec as source of truth | ✅ | Spec + 1 clarification; V1 is exactly the approved script, and no schema is invented here. |
| II. Layered architecture | ✅ N/A | No application code paths change. Schema ownership moves out of ad-hoc scripts into `db/migration`. |
| III. REST contracts and DTOs | ✅ N/A | No endpoints. The team-facing contract is `contracts/migrations.md`. |
| IV. Automated tests | ✅ | `SchemaMigrationTest`; all DB tests now run on the migrated schema; guard behaviour covered in the quickstart. |
| V. Simplicity | ✅ | Plain SQL files and the Boot-managed tool. Flyway kept out of the runtime classpath. One small script instead of new infrastructure. |
| Technical constraints (Java 21, Spring Boot, PostgreSQL) | ✅ | |
| Workflow (branch, commits, PR, review) | ✅ | The production guard enforces "merged via PR first" for schema changes. |

**Post-design re-check**: ✅ no violations.

## Project Structure

### Documentation (this feature)

```text
specs/002-database-migrations/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/migrations.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
pom.xml                                          # + Flyway (test scope), flyway-maven-plugin (+ postgres plugin deps)
src/main/resources/db/migration/
└── V1__initial_schema.sql                       # team's db-script, verbatim
scripts/db-migrate.sh                            # rehearse | production | info
scripts/smoke-lambda-package.sh                  # image → postgres:18-alpine
src/test/java/com/wikigerminare/
├── TestcontainersConfiguration.java             # image → postgres:18-alpine
├── HealthIntegrationTest.java                   # image → postgres:18-alpine
└── SchemaMigrationTest.java                     # catalog matches V1
docs/database-migrations.md                      # team guide (FR-011)
```

**Structure Decision**: migrations live under `src/main/resources/db/migration`, Flyway's default location for both the Spring integration and the Maven plugin, so the two always read the same files. The files also end up in the Lambda zip as inert resources (a few KB), which is harmless because Flyway isn't on the runtime classpath.

## Complexity Tracking

No constitution violations to justify.
