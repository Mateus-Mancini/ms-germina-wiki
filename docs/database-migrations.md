# Database migrations

The database schema lives in versioned SQL files applied by [Flyway](https://documentation.red-gate.com/fd). **Never change the production schema by hand.** Design: [`specs/002-database-migrations`](../specs/002-database-migrations/).

## Rules

1. **One file per change**, in `src/main/resources/db/migration/`, named `V<next number>__<what_it_does>.sql`, e.g. `V2__add_pages_published_flag.sql`. Check the highest existing number first; two branches can't both use the same number, so rebase and renumber before merging if needed.
2. **Plain PostgreSQL 18 SQL.** Each file runs in a single transaction: it either applies completely or not at all.
3. **Never edit or rename a migration once it's merged into `main`.** Flyway stores a checksum of every applied file and refuses to run if one changes. To fix a mistake, add a new migration (fix forward).
4. **Schema only.** No test or environment-specific data.
5. **Stay compatible with the previous release.** A failed release rolls the API back to the previous version automatically, but migrations stay applied. So a migration must never break the version that's currently live: add columns or tables first, and remove or rename them in a *later* release, once no deployed code uses them.

## Workflow for a schema change

```bash
# 1. Add the migration on your feature branch
$EDITOR src/main/resources/db/migration/V2__add_pages_published_flag.sql

# 2. Test: every Testcontainers database (and ./mvnw spring-boot:test-run) is migrated automatically
./mvnw test
```
If you change or add tables, extend [`SchemaMigrationTest`](../src/test/java/com/wikigerminare/SchemaMigrationTest.java) where it helps.

3. **Rehearse on a copy of production.** On your PR this happens automatically in the `rehearse-migrations` check, once the owner approves it. Locally (with Neon access) it creates a disposable Neon branch, migrates it, and deletes it:
   ```bash
   source ~/.config/germinawiki/prod.env
   scripts/db-migrate.sh rehearse
   ```
4. **Open a PR** and mention the migration and the rehearsal output in its description.
5. **After the PR is merged**, the account owner applies it to production **from `main`**, before deploying code that depends on it:
   ```bash
   git switch main && git pull --ff-only
   scripts/db-migrate.sh production   # shows status, asks you to type 'yes'
   ```
   The script refuses to run from any other branch, with uncommitted changes, or if local `main` differs from `origin/main`. **The release pipeline now runs this step automatically on every merge** (feature 003); the manual command is the owner's fallback.

Check the current production version at any time (read-only):
```bash
scripts/db-migrate.sh info
```

## If a production migration fails

- The failed migration is rolled back automatically: no partial changes, and it isn't recorded as applied.
- Read the error, correct the file in a **new** PR, rehearse, merge, and run `production` again. A migration that failed was never recorded as applied, so this is the only case where editing a merged migration is allowed.
- If `production` reports a checksum mismatch, someone edited an applied migration. Restore the file from `main`; don't "repair" production.

## Good to know

- Tests use `postgres:18-alpine`, the same major version as production (Neon, PostgreSQL 18).
- Migrations connect through Neon's **direct** endpoint (the host without `-pooler`), because the pooler breaks Flyway's lock. The API itself keeps using the pooled endpoint.
- The API never runs migrations. Flyway isn't even in the Lambda package, so deploys can't change the schema by accident.
- `flyway clean` (drop everything) is disabled.
