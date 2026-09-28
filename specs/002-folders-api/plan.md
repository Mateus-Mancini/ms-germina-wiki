# Implementation Plan: folders-api

**Branch**: `002-folders-api` (feature identifier; no Git branch created) | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Owner**: Camilla

**Input**: Feature specification in `specs/002-folders-api/spec.md`

## Summary

Deliver the REST CRUD and full-tree endpoints for `folders`, mapped to the existing PostgreSQL table. Use a feature-local Controller -> Service -> Repository structure, DTO-only HTTP boundaries, a lazy owning-side self-reference in JPA, and a transaction-scoped PostgreSQL advisory lock before validating and saving parent changes. Build nested tree responses from a flat query result. Do not create or migrate database structures or alter out-of-scope APIs.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1; existing Spring Web MVC, Spring Data JPA, Springdoc OpenAPI and PostgreSQL driver. Add `spring-boot-starter-validation` because no Bean Validation provider is currently declared.

**Storage**: Existing PostgreSQL `folders` table and its existing foreign keys; `spring.jpa.hibernate.ddl-auto=validate` remains enabled. No migrations or schema generation.

**Testing**: Existing JUnit and Spring Boot MVC/JPA test modules; unit and MVC slice tests plus PostgreSQL-backed integration tests against the existing schema.

**Target Platform**: JVM 21 server

**Project Type**: Single Spring Boot REST backend

**Performance Goals**: No numeric SLA or volume target is specified. List/tree return the full folder collection as specified.

**Constraints**: Business rules stay in Service. Use DTOs and Bean Validation. Use the current authenticated principal as `createdBy` but do not implement auth-api or rbac-middleware. Honor actual PostgreSQL foreign-key delete actions. Do not modify users-api, pages-api, wikilinks-backlinks, comments-api, images-api, or search-api.

**Scale/Scope**: One `folders-api` feature: create, get by UUID, list, partial update, delete, and full forest retrieval.

## Constitution Check

*Initial gate before research: PASS. Post-design gate recheck: PASS; the selected data model, OpenAPI contract, and validation approach retain the same constitutional alignment, subject to the external environment and branch prerequisites below.*

- **Java and platform**: PASS. Java 21, Spring Boot, PostgreSQL, REST and DTOs match the current Constitution text.
- **Layering**: PASS. Controller handles HTTP only; Service owns validation/use cases and cycle prevention; Repository handles persistence and locking SQL.
- **Contract and validation**: PASS. OpenAPI contract uses the specified HTTP methods/statuses and DTOs; Bean Validation is enabled by the required Boot starter.
- **Automated tests**: PASS at design level. Unit, MVC and PostgreSQL integration coverage is planned for success, validation, hierarchy, delete constraints and regressions.
- **Simplicity and scope**: PASS. Existing dependencies are reused except the required validation starter; no new database structures or out-of-scope features are introduced.
- **External prerequisites**: Before implementation, verify the actual column types, FK names/actions and test schema in PostgreSQL, and ensure the hosting authentication layer supplies the authenticated user's UUID. These are environment dependencies, not unresolved feature behavior.
- **Branch gate**: This planning command did not create or switch Git branches. Per the Constitution, implementation must run from the feature branch `002-folders-api` (or the team's corresponding feature branch) before source changes begin.
- **Constitution recordkeeping**: The normative technical restriction now says Java 21. Its sync-impact/version/date metadata still appears to describe the earlier 2026-09-25 amendment and does not mention this technology change. Constitution governance requires amendment metadata to be recorded; reconcile that separately before adopting the Constitution change. This plan does not edit the Constitution.

## Design and Implementation Sequence

1. Add the Boot validation starter; map `Folder` and `FolderRepository` to the existing schema without schema generation. Confirm actual PostgreSQL column/FK metadata first.
2. Implement transactional Service operations. Check parent existence, reject self-parenting, and validate the ancestor chain while holding the shared transaction-scoped advisory lock for hierarchy mutations.
3. Add request/response DTOs, `FolderController`, and feature-local exception translation. Preserve omitted-vs-explicit-null semantics for partial updates. Map PostgreSQL FK violation `23503` on delete to `409` without imposing API cascades.
4. Implement list/tree retrieval from one flat projection and assemble response DTO nodes by parent UUID; never serialize JPA entities.
5. Add Service unit tests, MVC contract tests, and PostgreSQL integration tests including real FK delete behavior and concurrent reparent attempts. Update no unrelated tests except the existing application-context test if feature bean registration requires it.

## Project Structure

### Documentation (this feature)

```text
specs/002-folders-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── folders-api.openapi.json
└── tasks.md                 # Created later by /speckit.tasks
```

### Source Code (repository root)

```text
src/main/java/com/wikigerminare/
├── WikigerminareApplication.java
└── folders/
    ├── Folder.java
    ├── FolderRepository.java
    ├── FolderService.java
    ├── FolderController.java
    ├── FolderExceptionHandler.java
    └── dto/
        ├── CreateFolderRequest.java
        ├── UpdateFolderRequest.java
        ├── FolderResponse.java
        └── FolderTreeNodeResponse.java

src/test/java/com/wikigerminare/
├── WikigerminareApplicationTests.java
└── folders/
    ├── FolderServiceTest.java
    ├── FolderControllerTest.java
    └── FolderPostgresIntegrationTest.java
```

**Structure Decision**: Keep the application in its existing single Maven module and root package. Add a feature-local `folders` package with the requested controller/service/repository layers and a small DTO package. No shared API/error framework exists to extend; keep exception translation local unless a shared convention is introduced separately.

## Risks and Boundaries

- Advisory locking serializes hierarchy mutations that use the same lock key. Any other writer that directly changes `parent_folder_id` must coordinate using the same protocol; PostgreSQL advisory locks are cooperative.
- Actual `ON DELETE` behavior is unknown in this repository. The feature must inspect and honor the deployed schema; tests must use PostgreSQL rather than assuming H2 behavior.
- The repository currently has no authentication implementation. The feature consumes a UUID from the host-provided principal and does not add an authentication mechanism.
- Partial PATCH DTOs must distinguish omitted fields from explicit null. Do this with presence-aware request DTOs; do not add a new JSON helper library unless implementation proves the local DTO approach inadequate.
- No test schema or PostgreSQL test service is present. End-to-end database tests require a provisioned test database with the existing `users` and `folders` tables.

## Complexity Tracking

No Constitution violations require justification. The advisory lock is a narrowly scoped PostgreSQL mechanism required to uphold the specified no-cycle invariant without adding schema objects.

