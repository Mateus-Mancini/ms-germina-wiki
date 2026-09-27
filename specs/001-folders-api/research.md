# Research: folders-api

**Feature**: [spec.md](spec.md)
**Data**: 2026-09-27

## Project findings

- The project is a single Maven Spring Boot 4.1.1 application on Java 21. Existing dependencies include Spring Web MVC, Spring Data JPA, PostgreSQL JDBC, Springdoc OpenAPI, and Boot MVC/JPA test modules.
- The source tree currently has only the application entry point and a context-load test. No folder entity/repository/controller, migration/schema file, authentication implementation, or package conventions exist yet.
- `application.properties` uses `spring.jpa.hibernate.ddl-auto=validate` and environment-provided PostgreSQL connection settings. The feature must map to the externally existing schema and must not introduce schema generation or migrations.
- The updated Constitution requires Java 21, Spring Boot, PostgreSQL, REST/DTOs, Controller -> Service -> Repository, tests, and Service-owned business rules. The spec now aligns with it.

## Decisions

### 1. Map only the owning side of the folder hierarchy

**Decision**: Map `Folder.parentFolder` as an optional, lazy JPA `ManyToOne` self-reference over the existing `parent_folder_id` foreign key. Do not add a bidirectional JPA child collection unless implementation demonstrates a concrete need. Store `createdBy` as a UUID value in this feature; do not add a User entity or alter users-api.

**Rationale**: `ManyToOne` maps the foreign-key-owning side directly. Avoiding an entity child collection reduces accidental graph traversal, JSON recursion, and cascade-delete risk. The API's `children` relation is constructed in response DTOs, not by serializing entities.

**Alternatives considered**: A bidirectional `OneToMany(mappedBy = "parentFolder")` collection is valid JPA, but is unnecessary for the planned single-query tree assembly and would increase lifecycle/serialization coupling. `CascadeType.REMOVE` and `orphanRemoval` are excluded because deletion semantics belong to the existing database foreign keys.

**Reference**: [Jakarta Persistence `ManyToOne`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/manytoone) and [`OneToMany`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/onetomany).

### 2. Detect cycles under a hierarchy mutation lock

**Decision**: Run folder parent changes in a Service transaction. Acquire one fixed PostgreSQL transaction-scoped advisory lock before reading ancestry, then walk the proposed parent's ancestor chain and reject if it reaches the folder being moved. Track visited IDs so pre-existing corrupt data cannot make the traversal infinite. Persist only after validation. All folders-api operations that can change an existing parent must use the same lock key.

**Rationale**: A plain read-then-write cycle check can race when two concurrent reparent operations each validate an older tree. The advisory transaction lock serializes those API operations, needs no new table or migration, and is proportionate to the unquantified initial workload. The lock is released automatically with the transaction.

**Boundary and risk**: PostgreSQL does not enforce use of advisory locks. The guarantee covers writers that use this folders-api protocol; any other process that directly updates `folders.parent_folder_id` must use the same lock or otherwise coordinate. This feature does not add a trigger or schema-level cycle constraint.

**Alternatives considered**: `SERIALIZABLE` transactions with a retry policy may permit more concurrent work but add transaction retry/error-handling complexity. Locking only the moved row does not coordinate all ancestry changes. A database trigger or recursive schema mechanism would modify the existing database structure and is excluded by scope.

**Reference**: [PostgreSQL explicit and advisory locks](https://www.postgresql.org/docs/current/explicit-locking.html), including transaction-scoped locks and the requirement that advisory-lock protocols are enforced by applications.

### 3. Build tree responses from a flat projection

**Decision**: Query folder scalar fields once, including the parent UUID, and assemble response DTO nodes in memory by ID and parent ID. Return root nodes where parent ID is null, with each node exposing `children`; do not serialize JPA entities. Keep response ordering unspecified as in the spec.

**Rationale**: This avoids recursive entity JSON serialization and avoids fetching each lazy parent or child separately. It also makes the API's tree representation independent from the entity relationship shape.

**Alternatives considered**: Recursive ORM traversal risks N+1 database reads and serialization recursion. A recursive CTE is not required for the initial contract and would tie tree retrieval to database-specific SQL without measured need.

### 4. Preserve database-owned delete behavior and classify FK failures

**Decision**: Do not pre-delete children, reparent them, or configure JPA removal cascades. Let the existing PostgreSQL foreign-key actions govern deletion. When the database rejects a delete due to a foreign key, map SQLSTATE `23503` to `409 Conflict` and let the transaction roll back. Do not map every integrity exception to this status indiscriminately.

**Rationale**: This preserves configured `NO ACTION`, `RESTRICT`, `CASCADE`, or `SET NULL` behavior instead of imposing new semantics in the API. The exact FK actions are not present in the repository and must be inspected in the target database before implementation/testing.

**Alternatives considered**: API-owned cascade or reparenting would contradict the spec and could affect out-of-scope records. Parsing localized exception message text is less stable than SQLSTATE classification.

**Reference**: [PostgreSQL foreign-key actions](https://www.postgresql.org/docs/current/ddl-constraints.html) and [SQLSTATE error codes](https://www.postgresql.org/docs/current/errcodes-appendix.html), where `23503` is `foreign_key_violation`.

### 5. Test at MVC, Service, and PostgreSQL boundaries

**Decision**: Reuse the existing JUnit/Spring Boot test dependencies. Use MVC slice tests for route/status/validation/DTO semantics, Service unit tests for business rules, and PostgreSQL-backed integration tests for JPA mapping, foreign-key delete behavior, and concurrent cycle prevention. Reuse the existing Springdoc dependency for the OpenAPI contract; add no dependency as part of planning.

**Rationale**: Spring Boot provides MVC and JPA test slices. The repository has no embedded DB dependency or schema fixture, and `ddl-auto=validate` expects the real schema. PostgreSQL semantics such as FK actions, advisory locks, and SQLSTATE should be tested against PostgreSQL, not assumed from H2.

**Test environment constraint**: A PostgreSQL test database with the existing `folders` and `users` schema is a prerequisite. Do not add a migration or schema fixture that creates a competing database model. If the team later provides a canonical schema fixture, it can be reused without changing the feature schema.

**References**: [Spring Boot auto-configured MVC and JPA tests](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html).

### 6. Add Bean Validation support only where required

**Decision**: Add the Spring Boot validation starter because the current Maven dependencies do not include a Bean Validation provider. Use Jakarta Bean Validation constraints on creation DTOs and supplied update fields; keep hierarchy checks in the Service.

**Rationale**: The feature explicitly requires Bean Validation where applicable. Spring MVC applies Bean Validation to `@RequestBody` command objects annotated with `@Valid` when an implementation is available. The validation starter supplies the provider without introducing a separate validation framework.

**Reference**: [Spring Boot validation support](https://docs.spring.io/spring-boot/reference/io/validation.html) and [Spring MVC validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html).

## Resolved technical context

- No explicit throughput, latency, or data-volume target is specified; do not introduce a performance SLA. The list and tree endpoints return the complete set, matching the spec.
- The authenticated principal must be supplied by infrastructure outside this feature. The HTTP layer passes its UUID into the Service; this feature does not implement auth-api or rbac-middleware.
- Since no migration files exist and Hibernate is configured to validate the schema, implementation must confirm the actual column types and FK actions against the target PostgreSQL database before finalizing entity annotations and delete integration tests.
