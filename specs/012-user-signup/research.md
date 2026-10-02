# Research: User Sign-up

## Existing registration gap

- **Decision**: Add registration and call existing login separately.
- **Rationale**: README Authentication and `auth/AuthService.java` require accounts to exist already; users-api only reads/updates profiles. A separate RegistrationController/Service avoids changing login constructors and test slices.
- **Alternatives considered**: Auto-login adds a new token issuing case; frontend work belongs to the consumer repository.

## Identity and schema

- **Decision**: Strip outside whitespace from name/email and preserve case-sensitive email comparison; reuse User and schema without migration.
- **Rationale**: `V1__initial_schema.sql` defines name VARCHAR(150), email VARCHAR(255) UNIQUE, password_hash TEXT, role user_role, and non-null timestamps. `UserRepositoryPostgresTest` explicitly requires case-sensitive findByEmail. The model uses an enum cast and manual UUID/timestamps.
- **Alternatives considered**: Lowercasing or citext would change identity semantics and require addressing existing case-distinct accounts. Native SQL creation would duplicate mapped entity persistence.

## Password and validation

- **Decision**: Reuse the BCrypt PasswordEncoder, validate 8 characters minimum and 72 UTF-8 bytes maximum before hashing; preserve password exactly.
- **Rationale**: Existing `AuthenticationConfiguration` defines BCrypt. Local Spring Security crypto source confirms UTF-8 conversion and encode rejection beyond 72 bytes. DTO size alone cannot enforce the byte limit. Jakarta Validator in Service preserves business validation outside MVC. No password complexity rule is added.
- **Alternatives considered**: Truncation loses password information; replacing the encoder or tightening LoginRequest affects existing accounts.

## Concurrent duplicate creation

- **Decision**: Precheck email, then saveAndFlush in a transaction; translate only SQLState 23505 with users_email_key from Hibernate's constraint exception into a domain conflict.
- **Rationale**: Precheck cannot serialize simultaneous requests. The existing unique constraint is authoritative. Flush exposes violation before returning and outer transaction rollback keeps original account unchanged.
- **Alternatives considered**: Translating every integrity error hides unrelated failures; save without flush may fail outside the service; locking introduces unnecessary coordination.

## DTO privacy and supported fields

- **Decision**: Reuse OwnUserProfileResponse; write-only password with masked toString; reject all unknown input fields; force member in the User factory.
- **Rationale**: Existing LoginRequest masks secrets and UpdateUserProfileRequest rejects unknown fields. Neither the response nor input requires a new role management mechanism.
- **Alternatives considered**: Returning an entity leaks stored credentials; accepting role creates privilege escalation; silently ignoring extra fields obscures consumer errors.

## Research evidence

The speckit-plan research agent inspected the schema, auth/users source, tests and the locally cached crypto source without modifying files. No external dependency or unsettled design question remains. Docker was unavailable during initial environment inspection; real PostgreSQL validation is still required and must be reported separately from unit/MVC validation.

Docker was subsequently enabled. Final validation passed all registration scenarios and the full Maven suite against disposable PostgreSQL/MinIO. An isolated JVM network probe identified an environment failure with the default Unix-domain socket temporary directory; the test command directed that directory to target. No application setting or source change was required. See tasks.md for final counts and execution details.
