# Implementation Plan: User Sign-up

**Branch**: `feat/user-signup` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-user-signup/spec.md`

## Summary

Add anonymous `POST /api/auth/register`, then reuse `POST /api/auth/login` and `GET /api/users/me`. A dedicated RegistrationController delegates to RegistrationService and the existing UserRepository. Reuse User, OwnUserProfileResponse and the configured PasswordEncoder. Store only BCrypt hashes; force member role; reject invalid and unknown fields; translate only collisions of users_email_key to 409, including concurrent inserts. Preserve existing case-sensitive email identity. No migration or new dependency is needed.

## Technical Context

**Language/Version**: Java 21 (Maven release target).
**Primary Dependencies**: Existing Spring Boot 4.1.1, Spring MVC, Security, Data JPA, Jakarta Validation and Hibernate; no additions.
**Storage**: Existing PostgreSQL users table and user_role enum, V1 migration plus V2 unrelated image migration.
**Testing**: JUnit, AssertJ, Mockito, MockMvc with the actual security chain, PostgreSQL Testcontainers.
**Target Platform**: Existing AWS Lambda backend and local Spring Boot runtime.
**Project Type**: Single backend Maven module.
**Performance Goals**: One account lookup, one BCrypt encoding and one insert per successful registration. No extra remote integration or latency SLA is introduced.
**Constraints**: name <=150 characters; email <=255; password >=8 characters and <=72 UTF-8 bytes; only member accounts; existing login must remain compatible.
**Scale/Scope**: Registration only; no frontend, email verification, administrator provisioning or automatic login.

## Constitution Check

*GATE: Checked before research and again after design.*

| Principle | Evidence | Before/after |
|---|---|---|
| I. Specification | FR-001..011 and SC-001..004 mapped to tasks; assumptions explicit; ambiguity scan found no blocking clarification. | PASS / PASS |
| II. Layers | Controller handles HTTP; Service validates, hashes and coordinates persistence; Repository performs persistence; User factory initializes mapped values. | PASS / PASS |
| III. REST/DTOs | RegisterRequest and existing OwnUserProfileResponse; 201 with Location, 400 for input, 409 for duplicate, no entity serialization. | PASS / PASS |
| IV. Tests | Unit tests, real-security MVC tests and PostgreSQL flow/concurrency tests required. | PASS / PASS |
| V. Simplicity | Existing BCrypt, JPA model, repository and DTO reused. Registration is isolated from login; no broad refactor or dependency. | PASS / PASS |

Schema review confirmed all fields, enum casts, timestamps and uniqueness match User. Constitution remains unchanged. Existing uncommitted index entries under specs/001-rbac-middleware are unrelated and must be preserved. Human review remains required before integration.

## Project Structure

### Documentation (this feature)

```text
specs/012-user-signup/
  spec.md
  checklists/requirements.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/registration.md
  tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/wikigerminare/
  auth/RegistrationController.java
  auth/RegistrationService.java
  auth/RegistrationValidationException.java
  auth/EmailAlreadyRegisteredException.java
  auth/AuthExceptionHandler.java
  auth/dto/RegisterRequest.java
  config/SecurityConfig.java
  users/User.java
src/test/java/com/wikigerminare/auth/
  RegistrationServiceTest.java
  RegistrationControllerTest.java
  RegistrationPostgresIntegrationTest.java
README.md
```

**Structure Decision**: Follow existing feature packages auth/users while preserving Controller -> Service -> Repository responsibilities. Keep RegistrationController separate so existing login slices and service constructors require no changes.

## Design and Validation

Validate DTO constraints in Service using Jakarta Validator, including when invoked without HTTP; setters strip only name/email. The password property is write-only and toString masks it. Capture unknown fields as a boolean with JsonAnySetter and reject in Service. Add User.registerMember factory taking service-generated UUID, normalized values, encoded password and timestamp, with member role fixed and null optional profile fields.

Check findByEmail before BCrypt; saveAndFlush inside one transaction catches race failures before returning. Translate DataIntegrityViolationException only when its Hibernate ConstraintViolationException cause has SQLState 23505 and constraint users_email_key. Propagate all other storage errors without using exception details as a response. Do not query after failed flush; the transaction rolls back.

MVC tests import SecurityConfig and JwtConfiguration to prove anonymous POST access and protected other methods. PostgreSQL tests prove enum mapping, timestamps, password hashing, register -> login -> profile, member-only authorization and concurrent outcomes. Unit tests force the flush-collision branch and different integrity failures. Run existing auth/users/RBAC tests as regression validation. Report unavailable Docker honestly, leaving PostgreSQL verification task open until executed.
