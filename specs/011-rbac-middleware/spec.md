# Feature Specification: RBAC Middleware (Admin-Only Guard)

**Feature Branch**: `011-rbac-middleware`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Crie a specification da feature `rbac-middleware` do GerminaWiki. A feature deve fornecer um mecanismo reutilizável para proteger operações administrativas do backend. Objetivo: permitir que uma rota ou operação seja marcada como \"somente administrador\"; negar usuários autenticados sem permissão administrativa; negar usuários anônimos; permitir administradores; produzir semântica HTTP consistente (401 não autenticado, 403 autenticado sem permissão, permitido para administrador). A feature deve reutilizar o papel administrativo já estabelecido pela autenticação e não criar um novo modelo de usuário, role ou autenticação. O guard deve ser reutilizável por outras APIs do projeto, especialmente pages-api e comments-api, sem que cada feature precise implementar sua própria verificação de administrador. Limites: não implementar login, JWT, User API, gerenciamento de roles; não modificar regras de negócio específicas de pages, comments ou images; não reescrever funcionalidades existentes apenas para adotar o guard."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Anonymous user is blocked from an administrative operation (Priority: P1)

An anonymous (unauthenticated) visitor attempts to perform an operation that has been marked as "administrator only." The system must refuse the request without revealing whether the operation would otherwise succeed, and must clearly communicate that authentication is required.

**Why this priority**: This is the baseline protection the whole feature exists for. Without it, administrative operations would be reachable by anyone.

**Independent Test**: Call any operation marked as admin-only without supplying any credentials and confirm the request is rejected with a "not authenticated" outcome (HTTP 401).

**Acceptance Scenarios**:

1. **Given** an operation marked as administrator-only, **When** a request arrives with no authenticated identity, **Then** the system rejects it with HTTP 401 and the operation's normal behavior never executes.
2. **Given** an operation marked as administrator-only, **When** a request arrives with an invalid or unrecognizable identity, **Then** the system treats it the same as an anonymous request and rejects it with HTTP 401.

---

### User Story 2 - Authenticated non-administrator is denied (Priority: P1)

A signed-in user who does not hold administrative permission attempts to perform an operation marked as "administrator only." The system must recognize that the user is authenticated but still refuse the action because the user lacks the required permission.

**Why this priority**: This distinguishes "not logged in" from "logged in but not allowed," which is required for correct, predictable API behavior and for client applications to react appropriately (e.g., show a permission error instead of prompting login again).

**Independent Test**: Authenticate as a regular (non-administrator) user, call an operation marked as admin-only, and confirm the request is rejected with a "forbidden" outcome (HTTP 403), distinct from the anonymous case.

**Acceptance Scenarios**:

1. **Given** an operation marked as administrator-only, **When** a request arrives from an authenticated user without administrative permission, **Then** the system rejects it with HTTP 403 and the operation's normal behavior never executes.
2. **Given** a user with no administrative permission, **When** the same user accesses an operation that is not marked as admin-only, **Then** the request proceeds normally (this feature does not affect non-protected operations).

---

### User Story 3 - Administrator is granted access (Priority: P1)

A signed-in user who holds administrative permission performs an operation marked as "administrator only." The system must let the operation proceed exactly as if the guard were not present.

**Why this priority**: The guard is only useful if it reliably lets legitimate administrators through; a false denial would block core administrative workflows.

**Independent Test**: Authenticate as a user with administrative permission, call an operation marked as admin-only, and confirm the request proceeds to the operation's normal behavior (no 401/403).

**Acceptance Scenarios**:

1. **Given** an operation marked as administrator-only, **When** a request arrives from an authenticated administrator, **Then** the system allows the request to proceed to the operation's own logic without alteration.

---

### User Story 4 - Other APIs reuse the guard without reimplementing checks (Priority: P2)

A developer building or maintaining a different part of the backend (for example, an operation under pages-api or comments-api) needs to restrict an operation to administrators. Instead of writing custom code to inspect the caller's identity and role, the developer marks the operation as admin-only using the same reusable mechanism used elsewhere in the project.

**Why this priority**: Reusability is the core value proposition of this feature; without it, every feature would keep re-implementing ad-hoc admin checks (as already happens today), increasing the chance of inconsistent behavior and security gaps.

**Independent Test**: Apply the admin-only marking to a new operation in a different feature area (without writing any custom permission-checking code for that operation) and confirm it produces the same 401/403/allow behavior as the operations covered in User Stories 1-3.

**Acceptance Scenarios**:

1. **Given** a new operation in any feature area of the backend, **When** a developer marks it as administrator-only using the shared mechanism, **Then** the operation exhibits the same 401/403/allow behavior without any feature-specific permission-checking code being written.
2. **Given** two operations in different feature areas (e.g., one under pages-api, one under comments-api) both marked as administrator-only, **When** the same non-administrator identity calls each of them, **Then** both are rejected with HTTP 403 using the same underlying mechanism.

### Edge Cases

- What happens when an operation is marked as admin-only but the request also fails other validation (e.g., malformed input)? The authentication/permission check runs independently of business validation; a non-admin or anonymous caller still receives 401/403 rather than a validation error, since the operation's own logic must never execute for a denied caller.
- What happens when a feature that already has its own hand-written admin check (e.g., an existing operation in comments or images) does not adopt the guard? It continues to behave as it does today; this feature does not require existing operations to migrate.
- What happens when an authenticated caller's identity cannot be resolved to a valid user id even though a token/credential was presented? The caller is treated as not authenticated (401), consistent with current behavior for malformed identities.
- What happens when an operation is not marked as admin-only at all? The guard has no effect; the operation behaves exactly as it does without this feature.
- What happens when the same operation is checked more than once (e.g., called from more than one code path in the same request)? The outcome is deterministic and consistent — the same caller against the same admin-only operation always yields the same decision (401, 403, or allow).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a reusable mechanism that lets any backend operation (an HTTP route or an equivalent backend action) be explicitly marked as "administrator only."
- **FR-002**: System MUST reject requests to an administrator-only operation with HTTP 401 when the caller has no valid authenticated identity, and MUST prevent the operation's own logic from running in that case.
- **FR-003**: System MUST reject requests to an administrator-only operation with HTTP 403 when the caller has a valid authenticated identity but does not hold administrative permission, and MUST prevent the operation's own logic from running in that case.
- **FR-004**: System MUST allow requests to an administrator-only operation to proceed unmodified when the caller is authenticated and holds administrative permission.
- **FR-005**: System MUST determine administrative permission using the administrative role/signal already established by the existing authentication mechanism, and MUST NOT introduce a new user model, role model, or authentication scheme to make this determination.
- **FR-006**: The mechanism MUST be consumable by any backend feature or API — explicitly including pages-api and comments-api — so that marking an operation as admin-only does not require that feature to write or maintain its own permission-checking logic.
- **FR-007**: The mechanism MUST produce the same 401/403/allow decision logic regardless of which feature or API applies it, so that admin-only behavior is consistent project-wide.
- **FR-008**: Operations that are not marked as administrator-only MUST be unaffected by this feature.
- **FR-009**: Adopting the mechanism for an existing operation MUST be optional; existing operations that already implement their own administrator checks are not required to be rewritten or migrated as part of this feature.
- **FR-010**: The mechanism MUST NOT change, override, or duplicate feature-specific business authorization rules (for example, ownership checks on comments, or page/image-specific rules) — it only governs the administrator-only allow/deny decision.

### Key Entities

- **Protected Operation**: A backend operation (route or equivalent action) that has been designated as requiring administrative permission before it executes. Key attribute: whether it is marked admin-only.
- **Access Decision**: The outcome produced when a request reaches a Protected Operation — one of "denied, not authenticated" (401), "denied, insufficient permission" (403), or "allowed." Determined from the caller's existing authenticated identity and administrative role signal.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of requests from callers with no authenticated identity against any administrator-only operation result in a 401 outcome, and the operation's own behavior never executes.
- **SC-002**: 100% of requests from authenticated, non-administrator callers against any administrator-only operation result in a 403 outcome, and the operation's own behavior never executes.
- **SC-003**: 100% of requests from authenticated administrator callers against any administrator-only operation are allowed to proceed to the operation's own behavior.
- **SC-004**: A developer can designate a new operation in any feature area as administrator-only using a single, documented, reusable declaration — with zero custom permission-checking code written for that operation.
- **SC-005**: The same reusable mechanism is demonstrably exercised by operations in at least two distinct feature areas (pages-api and comments-api) with identical 401/403/allow behavior.
- **SC-006**: Existing operations that are not updated to use the mechanism continue to behave exactly as before, with no observable behavior change.

## Assumptions

- The existing authentication mechanism already establishes, for each authenticated request, a reliable signal of whether the caller holds administrative permission (today surfaced as an admin role/authority and reflected in the project's authenticated-user information). This feature reuses that signal rather than computing or storing permission itself.
- "Anonymous" means any request without a valid, resolvable authenticated identity, consistent with how the project already treats missing or invalid credentials (rejected before reaching application logic).
- Error responses for denied requests follow the project's existing conventions for authentication/authorization error outcomes; this feature does not introduce a new error response format.
- "pages-api" and "comments-api" refer to existing/ongoing backend feature areas of this project; this feature does not implement or change their business logic, only provides the protection mechanism those areas may choose to use for administrator-only operations.
- Operations that already implement their own hand-written administrator checks are not required to adopt this mechanism; adoption is left to each feature's own future work and is out of scope here.
- No new administrative capability levels (e.g., "moderator," "super-admin") are introduced; the only distinction this feature makes is administrator vs. non-administrator, matching what authentication already provides today.
