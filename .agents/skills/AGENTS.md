# AGENTS.md

## Project Overview

This repository is a Spring Boot application built with Java and Maven.

The project uses PostgreSQL as its primary database and follows a layered backend architecture.

The repository also uses Spec Kit to organize software development through specifications, plans, tasks, and implementation workflows.

The project contains reusable Spec Kit skills under:

.agents/skills/

These skills should be used whenever they are relevant to the requested task.

---

## Core Principles

### 1. Preserve Existing Architecture

Before making changes, inspect the existing project structure and conventions.

Do not introduce new architectural patterns, frameworks, libraries, or abstractions unless explicitly required.

Prefer consistency with the existing implementation over introducing a theoretically better architecture.

Do not perform unrelated refactoring while implementing a task.

---

### 2. Database Schema Is the Source of Truth

The current database schema and database scripts are authoritative.

Before implementing or modifying database-dependent functionality:

1. Inspect the current database scripts.
2. Inspect the relevant entities and relationships.
3. Inspect repositories and queries.
4. Inspect existing migrations or schema definitions.
5. Compare the application model with the actual database model.

Never assume that an older implementation, specification, entity, or migration represents the current database structure.

If the database schema has changed, analyze the impact on the existing implementation before modifying code.

---

### 3. Never Reimplement Existing Work Without Investigation

If a feature or task has already been implemented, do not recreate it from scratch.

First determine:

- What was already implemented.
- Which parts are still valid.
- Which parts are incompatible with the current requirements or database schema.
- Which files are actually affected.

Prefer incremental corrections over complete rewrites.

---

## Spec Kit Workflow

This repository uses Spec Kit.

Relevant skills are located under:

.agents/skills/

Available Spec Kit workflows may include:

- speckit-specify
- speckit-clarify
- speckit-plan
- speckit-tasks
- speckit-implement
- speckit-analyze
- speckit-checklist
- speckit-constitution
- speckit-converge
- speckit-tasksissues

Before performing a Spec Kit-related task, inspect the relevant `SKILL.md`.

Do not assume the workflow or skill behavior. Read the applicable skill instructions before executing them.

Follow the project's existing Spec Kit workflow instead of inventing a parallel workflow.

---

## Specifications and Tasks

Specifications are stored under:

specs/

Before implementing a task:

1. Identify the relevant specification.
2. Read the specification.
3. Read the relevant plan if available.
4. Read the relevant task list.
5. Inspect the current implementation.
6. Inspect the current database schema when the task interacts with persistence.

The specification describes the intended behavior.

The current implementation describes the current state of the code.

The current database schema describes the actual persistence model.

When these sources disagree, do not silently choose one.

Analyze the discrepancy and determine which source is authoritative for the specific decision.

---

## Implementation Workflow

When implementing a task:

### Step 1: Understand

Inspect the relevant:

- Specification
- Plan
- Tasks
- Existing source code
- Database schema
- Tests

Do not modify files during the initial investigation unless explicitly requested.

### Step 2: Plan

Identify:

- Files that must change
- Files that may need changes
- Database implications
- API implications
- Test implications
- Potential compatibility issues

Keep the change set as small as reasonably possible.

### Step 3: Implement

Implement only the required changes.

Preserve existing behavior that is unrelated to the task.

Follow existing project conventions.

### Step 4: Validate

Run the relevant tests and build commands.

If something fails:

1. Determine whether the failure was caused by the current change.
2. Fix the underlying problem.
3. Re-run the relevant validation.

Do not hide or ignore failing tests.

---

## Database Rules

The application uses PostgreSQL.

Database-dependent changes require special attention.

Before changing:

- Entities
- JPA mappings
- Repositories
- Queries
- DTOs
- Services
- Controllers
- Migrations
- Database scripts
- Enum mappings
- Foreign keys
- Relationships

inspect the current database schema.

### PostgreSQL Compatibility

Do not assume that behavior observed in H2 is equivalent to PostgreSQL.

When PostgreSQL-specific behavior is relevant, prefer PostgreSQL-compatible tests and validation.

Pay particular attention to:

- PostgreSQL enums
- Foreign keys
- Constraints
- UUIDs
- Native queries
- Column types
- Naming
- Nullability
- Default values
- Cascading behavior
- Unique constraints

If Testcontainers is already configured for PostgreSQL, prefer it for integration tests involving database behavior.

---

## Entity and Database Mapping

When modifying JPA entities:

1. Verify table names.
2. Verify column names.
3. Verify primary keys.
4. Verify foreign keys.
5. Verify relationship cardinality.
6. Verify nullable/non-nullable fields.
7. Verify enum mappings.
8. Verify generated/default values.
9. Verify cascade and orphan-removal behavior when applicable.

Do not modify entity mappings merely to make a test pass without first verifying the actual database schema.

---

## Testing

Tests are part of the implementation.

When modifying behavior, update or add the relevant tests.

Prefer the existing testing patterns used by the project.

Before considering a task complete:

- Run the relevant unit tests.
- Run relevant integration tests.
- Run the Maven build when appropriate.

Do not remove or weaken an existing test simply because the implementation fails it.

If an existing test contradicts the current intended behavior, investigate the discrepancy before changing the test.

---

## Build and Tooling

The project uses Maven.

Prefer the project's Maven wrapper when available.

Typical commands include:

```bash
./mvnw test