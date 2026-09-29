# Feature Specification: Database Migrations

**Feature Branch**: `002-database-migrations`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Complete task infra-database: the production database is empty (the team's schema script was never applied) and there is no migration runner. Put the schema under version control as ordered, repeatable migrations, apply the initial schema to production safely, make every automated test run against the real schema, and give the team a documented way to evolve the schema. There is only a production environment."

## Clarifications

### Session 2026-09-27

- Q: Must a migration be merged into `main` (after an approved PR) before it can be applied to production? → A: Yes. Only migrations already on `main` may be applied to production; rehearsals on a disposable copy may use any branch.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Production has the team's schema, applied safely (Priority: P1)

The account owner applies the team's approved initial schema (users, folders, pages, page images, comments, tags, page tags, page links) to the production database, after first rehearsing it on a disposable copy of that database. Afterwards the database records which schema version it's on.

**Why this priority**: Every API feature the team is building (auth, folders, pages, comments, images, search) reads and writes these tables. Without them, nothing beyond the health check can work in production.

**Independent Test**: Rehearse on a disposable copy and confirm success; apply to production; list production tables, types, indexes and triggers and compare them with the schema definition; confirm the recorded version is 1.

**Acceptance Scenarios**:

1. **Given** the empty production database, **When** the owner applies the pending migrations, **Then** all 8 tables, 2 enum types, the indexes and the `updated_at` triggers exist, and the database records version 1 as applied.
2. **Given** a disposable copy of the production database, **When** the owner rehearses the migration there, **Then** the result is reported without touching production.
3. **Given** production is already at the latest version, **When** the owner applies migrations again, **Then** nothing changes and the run reports that the schema is up to date.

---

### User Story 2 - Tests run against the real schema (Priority: P1)

A developer runs the test suite, and every test that needs a database gets a throwaway database built from the same migrations as production, on the same major PostgreSQL version.

**Why this priority**: Teammates' features (pages, folders, comments) will be tested against tables. If tests used a hand-made or empty schema, broken queries or broken migrations would only show up in production, and production is the only environment.

**Independent Test**: Run the test suite on a clean machine with Docker. Confirm the throwaway database contains all migration-created tables, and that a deliberately broken migration makes the suite fail.

**Acceptance Scenarios**:

1. **Given** the migrations in the repository, **When** the test suite starts a database-backed test, **Then** the test database has the complete schema at the latest version.
2. **Given** a migration with a SQL error, **When** the test suite runs, **Then** it fails before any test passes against a partial schema.

---

### User Story 3 - The team can evolve the schema safely (Priority: P2)

A teammate who needs a schema change, e.g. a new column for their feature, adds a new migration file following a documented convention, sees it applied in tests, rehearses it on a disposable copy, and once it's merged into `main` the owner applies it to production the same way as V1. An applied migration can't be silently edited afterwards.

**Why this priority**: The schema will change several times as the pending API features land. A clear, enforced convention prevents drift between teammates' machines, tests and production.

**Independent Test**: Add a sample migration on a branch, run tests, rehearse on a disposable copy, and confirm a modified already-applied migration is rejected.

**Acceptance Scenarios**:

1. **Given** a new migration following the convention, **When** tests run, **Then** it's applied after the existing ones.
2. **Given** a migration that was already applied has been edited, **When** migrations run against that database, **Then** the run fails with a clear message and changes nothing.
3. **Given** the documentation, **When** a teammate reads it, **Then** they know how to name, test, rehearse and request production application of a migration.

---

### Edge Cases

- An attempt to apply migrations to production from a branch other than `main` is refused before anything is applied.
- A migration fails halfway in production: the database must not be left with a half-applied migration, and the failure must be visible to the owner.
- The production database is suspended when migrations run: the run waits for it to wake instead of failing immediately.
- Two migrations with the same version number: the run is rejected before anything is applied.
- The API is serving while a migration runs: the initial migration only creates new objects, so running requests are unaffected. Later destructive changes are out of scope and need their own planning.
- The API must not apply migrations by itself at startup, so cold starts stay fast and a deploy never changes the schema implicitly.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The complete database schema MUST be defined in version-controlled, ordered migration files in the backend repository.
- **FR-002**: The first migration MUST reproduce the team's approved schema script exactly (tables, enum types, extension, constraints, indexes, full-text index, `updated_at` function and triggers).
- **FR-003**: The production database MUST record which migrations have been applied, in order, with a checksum of each.
- **FR-004**: Applying migrations MUST be idempotent: re-running when up to date changes nothing.
- **FR-005**: A run MUST refuse to proceed if an already-applied migration has been modified, if versions are duplicated, or if the database has unknown newer migrations.
- **FR-006**: Each migration MUST be applied atomically: it either completes or leaves no partial changes.
- **FR-007**: Every database-backed automated test MUST run against a throwaway database built from the migrations, using the same PostgreSQL major version as production (18).
- **FR-008**: The API MUST NOT apply migrations at startup in production.
- **FR-009**: Production migrations MUST be applied by an explicit, documented command, run only by the account owner (and later by the CI/CD pipeline), using the credentials already kept outside the repository, and only from the `main` branch, i.e. from migrations that were merged through an approved pull request. Rehearsals on a disposable copy may use any branch.
- **FR-010**: The owner MUST be able to rehearse pending migrations against a disposable copy of the production database before applying them to production, at no cost.
- **FR-011**: The repository MUST document the migration naming convention, how to add, test and rehearse a migration, and how production application is requested and performed.
- **FR-012**: The authorship of the original schema script MUST be preserved in the version history.

### Key Entities

- **Migration**: An immutable, versioned SQL change set with a description. Its version determines order, and its checksum detects edits.
- **Schema history**: The database's own record of applied migrations: version, description, checksum, when, success.
- **Disposable database copy**: A short-lived copy of production used only to rehearse migrations, deleted afterwards.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After application, production contains 100% of the objects defined by the schema script (8 tables, 2 enum types, all declared indexes, 4 `updated_at` triggers), verified by a catalog comparison.
- **SC-002**: Re-applying migrations to an up-to-date database completes with zero changes in under 30 seconds.
- **SC-003**: 100% of database-backed tests run on a migrated schema; a deliberately broken migration fails the suite every time.
- **SC-004**: A modified, already-applied migration is rejected in 100% of attempts, with no schema change.
- **SC-005**: A teammate can add and test a new migration by following the documentation alone, without help.
- **SC-006**: The feature adds USD 0.00 to monthly cost.
- **SC-007**: API cold-start time is unchanged by this feature (the API doesn't run migrations at startup).

## Assumptions

- The production database is empty today (verified 2026-09-27: no tables, types or migration history), so the first migration applies without any baseline step.
- The team's schema script (authored by Camilla) is the approved initial schema and needs no changes. It was verified to run cleanly on PostgreSQL 18.
- The database provider's free plan allows disposable database copies (branches) at no cost.
- Automated migration during deploys is delivered by the CI/CD feature (`003`). Until then, the owner applies migrations by hand with the documented command.
- Seed or reference data is out of scope. Only schema is migrated.
- Rollback of migrations is done by writing a new forward migration. Automatic "undo" migrations are out of scope.
