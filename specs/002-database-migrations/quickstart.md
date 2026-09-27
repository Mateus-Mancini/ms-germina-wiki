# Quickstart & Validation: Database Migrations

This proves feature `002-database-migrations` works. Contract: [contracts/migrations.md](contracts/migrations.md).

## Prerequisites

- JDK 21 and Docker.
- For rehearsal and production: the owner's secrets file with `DB_URL`, `DB_USER`, `DB_PASS`, plus `NEON_PROJECT_ID`, and `npx neonctl auth` done once.

## 1. Tests run on the migrated schema (US2, SC-003)

```bash
./mvnw test   # SchemaMigrationTest + every Testcontainers test start from V1 on postgres:18-alpine
```

**Broken-migration check**: add a temporary broken migration and confirm the suite fails, then remove it:
```bash
echo 'CREATE TABLE broken (' > src/main/resources/db/migration/V999__broken.sql
./mvnw test          # expect failure at context start (Flyway migration error)
rm src/main/resources/db/migration/V999__broken.sql
```

## 2. Local run

```bash
./mvnw spring-boot:test-run   # local Postgres 18 container, migrated to the latest version
```

## 3. Rehearse (US1-AS2, FR-010)

```bash
source ~/.config/germinawiki/prod.env
scripts/db-migrate.sh rehearse   # expect: V1 applied on the rehearsal branch, branch deleted
```

## 4. Apply to production (US1, SC-001, SC-002), from `main` only

```bash
git switch main && git pull --ff-only
scripts/db-migrate.sh production   # type 'yes'; expect V1 applied
scripts/db-migrate.sh production   # run again: expect "Schema is up to date", in < 30 s
```
Then compare the production catalog with `data-model.md` (8 tables, 2 enums, 4 triggers, GIN full-text index, `flyway_schema_history` at version 1).

## 5. Guards (FR-005, FR-009, SC-004)

- Run `scripts/db-migrate.sh production` from a feature branch: expect a refusal, with nothing applied.
- **Checksum guard**: edit a comment inside `V1__initial_schema.sql` locally, then run `scripts/db-migrate.sh rehearse`. Expect a validation failure (checksum mismatch) and no change. Revert the edit afterwards.

## 6. API unaffected (FR-008, SC-007)

```bash
./mvnw -Plambda -DskipTests package
unzip -l target/wikigerminare-lambda.zip | grep -i flyway   # expect: no output
scripts/smoke-lambda-package.sh                              # SMOKE OK
```
