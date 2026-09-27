# Contract: Migration Files and the `db-migrate` Command

These are the team-facing interfaces of this feature: how a migration file must look, and the command that applies migrations. CI/CD (feature 003) calls the same command.

## Migration files

- **Location**: `src/main/resources/db/migration/`
- **Name**: `V<version>__<snake_case_description>.sql`, with version a positive integer, strictly increasing, no gaps required. Examples: `V1__initial_schema.sql`, `V2__add_pages_published_flag.sql`.
- **Content**: plain PostgreSQL 18 SQL. Each file runs in one transaction, so don't include statements that can't run in a transaction (e.g. `CREATE INDEX CONCURRENTLY`) without discussing it first.
- **Immutability**: once a file is merged into `main`, never edit or rename it. Fix mistakes with a new `V<n+1>` file.
- **Scope**: schema only. No environment-specific data.

## `scripts/db-migrate.sh`

| Invocation | Allowed from | Target | Behaviour |
|---|---|---|---|
| `scripts/db-migrate.sh rehearse` | any branch | a new Neon branch of `main` (expires in 1 h) | Creates the branch, runs `flyway info` → `migrate` → `info` against its direct endpoint, prints the result, deletes the branch. Exit ≠ 0 on failure. |
| `scripts/db-migrate.sh production` | `main` only (clean tree, `HEAD == origin/main`) | production direct endpoint | Runs `flyway info`, asks the operator to type `yes`, then `migrate` and `info`. Refuses otherwise. |
| `scripts/db-migrate.sh info` | any branch | production direct endpoint | Read-only `flyway info` (current version, pending migrations). |

**Environment** (from the owner's secrets file; never committed):

| Variable | Used for |
|---|---|
| `DB_URL` | Pooled JDBC URL. The script derives the direct URL by removing `-pooler` from the host |
| `DB_USER`, `DB_PASS` | Database role credentials |
| `NEON_PROJECT_ID` | Rehearsal branch creation (non-secret) |

For non-interactive use (CI, feature 003), `DB_MIGRATE_CONFIRM=yes` replaces the prompt in `production`. The branch guards still apply.

## Flyway settings (fixed)

- `validateOnMigrate=true` (default): reject checksum mismatches, duplicate versions and missing files
- `outOfOrder=false`: reject older versions added after newer ones were applied
- `cleanDisabled=true`: `flyway clean` (drop everything) can never run against any database
- Connection retries of 10 × ~1 s, so a suspended Neon compute has time to wake
