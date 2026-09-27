# Research: pages-api

**Feature**: [spec.md](spec.md)
**Date**: 2026-09-27

## Project findings

- Single Maven application: Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, PostgreSQL driver, Springdoc OpenAPI, Bean Validation, and Spring Boot MVC/JPA test modules are present.
- `spring.jpa.hibernate.ddl-auto=validate`; database connection properties are environment supplied. No `Page` class, pages DDL/migration, test schema, or page tests are present.
- Existing `Folder` entity and `FolderRepository` are in `com.wikigerminare.folders`. `FolderController.create` obtains a UUID from `Principal.getName()` and passes it to the Service. Pages can reuse this convention without implementing authentication or modifying the folders API.
- No `psql` executable or process-level `DATABASE_URL` was available during planning. `.env` was not read. Exact `pages` column metadata must be verified in the provisioned PostgreSQL environment before entity mapping and integration validation.

## Decisions

### 1. Map the existing numeric version field with JPA optimistic locking

**Decision**: Map the actual existing numeric version column on the `Page` entity with `@Version` and an explicit `@Column(name = ...)` after its name and SQL type have been verified. Use only a Jakarta Persistence supported numeric Java type compatible with the PostgreSQL type (`short`/`Short`, `int`/`Integer`, or `long`/`Long`). Do not expose a writable version property in create/update DTOs and do not increment it manually. Keep Hibernate DDL mode at `validate`; add no migration or schema object.

**Rationale**: JPA providers use the version field for compare-and-update behavior. A concurrent update between read and write causes an optimistic locking failure, protecting against lost updates. The user confirmed that an existing numeric version column is available, but its exact type, name, nullability, current values and insert default are not documented in the repository.

**Implementation prerequisite**: Inspect the existing PostgreSQL schema before creating the entity. Confirm no persisted rows have null/incompatible versions, determine the insert initial value, and verify all writers that update page representations advance the same version. Do not assume Hibernate's numeric initial value is identical to a PostgreSQL column default.

**Alternatives considered**: Manual `UPDATE ... WHERE version = ?` is possible but duplicates provider behavior and risks bypassing managed-entity semantics. A new column, trigger, table, or migration is prohibited by the feature scope.

**Reference**: [Jakarta Persistence `@Version`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/version) documents supported types and optimistic-lock detection.

### 2. Use a strong, page-specific ETag with `If-Match`

**Decision**: Emit a strong ETag derived from page UUID and numeric version, such as `"<page-uuid>-v<version>"`, on create, individual-read, and successful-update responses. Require exactly one strong page ETag in `If-Match` for PATCH. Missing header returns `428 Precondition Required`; unsupported/malformed values return `400 Bad Request`; a well-formed but stale tag returns `412 Precondition Failed`. The response body also exposes the numeric current version. List items include numeric versions; a collection ETag is not an item ETag.

**Rationale**: `If-Match` uses strong comparison to prevent lost updates; `428` is explicitly defined for requiring conditional requests. Including the page ID prevents a tag from being reused against another page. The API deliberately narrows the RFC's permitted list and wildcard forms because those do not express the single version the client read.

**Alternatives considered**: A version field in the JSON body with `409` would work, but the user selected HTTP conditional requests and `412`. Weak entity tags are unsuitable for `If-Match` strong comparison.

**References**: [RFC 9110 §8.8.3 (ETag)](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.8.3), [§13.1.1 (If-Match)](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1), and [RFC 6585 §3 (428)](https://www.rfc-editor.org/rfc/rfc6585.html#section-3).

### 3. Handle the race after transaction rollback

**Decision**: Compare the supplied ETag to the loaded entity for a direct stale response, while relying on `@Version` for the atomic database race check. Flush within the update transaction before constructing the success response so the incremented version is available. If a provider optimistic-lock failure occurs, allow that transaction to roll back; then obtain current version in a separate read-only transaction and construct `412` with the current ETag/version.

**Rationale**: Jakarta Persistence allows optimistic-lock exceptions during an API call, flush, or commit, and marks an active transaction rollback-only. Catching inside the failed transaction and trying to write or read state there is unreliable. Post-rollback lookup handles both early compare failures and truly concurrent updates.

**Alternative considered**: Catching only around repository save is incomplete because the provider can defer the failure until flush/commit. A manual increment without `@Version` is not an adequate race guard.

**References**: [Jakarta Persistence `OptimisticLockException`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/optimisticlockexception) and [Spring `JpaOptimisticLockingFailureException`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaOptimisticLockingFailureException.html).

### 4. Reuse current folder and principal boundaries

**Decision**: Keep page behavior under `com.wikigerminare.pages`; depend on the existing `FolderRepository` to confirm `folderId` exists at creation. Map the existing folder entity as a lazy reference only if that matches the verified FK mapping. Read creator UUID from `Principal.getName()` in the pages HTTP boundary, pass it to Service, and never accept it from the request DTO. `folderId` is immutable after creation.

**Rationale**: This matches the implemented folders API and the clarified feature contract without implementing auth/users or changing folders-api.

**References**: Current local pattern: `src/main/java/com/wikigerminare/folders/FolderController.java` and `FolderService.java`.

### 5. Validate against the deployed PostgreSQL schema

**Decision**: Use unit and MVC slice tests for service behavior, headers, DTOs and status mapping. Run persistence and concurrency integration tests against a PostgreSQL database provisioned with the existing `pages`, `folders` and `users` schema. Do not add H2, generated schema, migration or replacement test table. Do not add Testcontainers unless the team separately supplies a canonical existing-schema fixture and approves the dependency.

**Rationale**: `ddl-auto=validate` requires the real mapped schema. There is no local pages schema/fixture or PostgreSQL test service in the repository. Provider/database version behavior must be checked against PostgreSQL with two independent transactions.

**Test coverage**: create/read/list/delete success and missing IDs; unknown folder; raw Markdown round trip; title/content validation; ETag on create/get/update; missing/malformed/stale `If-Match`; two competing updates using the same tag where at most one succeeds; stale response returns the latest numeric version after rollback; delete handling according to actual FK actions.

**Reference**: [Spring Boot test slices](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) and [Testcontainers integration](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) (considered, not selected for this plan).

## Resolved planning context

- No numeric performance, latency, scale, or pagination goal is specified; the list returns all pages as defined by the spec.
- Page folder is immutable after creation. No page move behavior is planned.
- Authentication remains external; this feature only consumes the host-provided principal UUID.
- Exact column metadata and FK delete actions remain environment facts to inspect before finalizing mappings and PostgreSQL tests; they do not authorize DDL or changes to other APIs.
