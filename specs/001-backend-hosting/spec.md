# Feature Specification: Backend Hosting

**Feature Branch**: `001-backend-hosting`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Provision production hosting for the GerminaWiki backend API (task infra-hosting, backend part). The API must be publicly reachable over HTTPS by the web app, cost nothing to run for about a dozen users, expose a health check the web app calls on load to wake the backend and its database, and minimize the delay users feel on the first request after the system has been idle. There is only a production environment."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The web app reaches a live API in production (Priority: P1)

A team member deploys the backend from the repository, and the GerminaWiki web app, served from its own public address, can call the API over a secure connection. Every other backend feature (auth, pages, folders, comments, images) is only usable by students once this exists.

**Why this priority**: Without a reachable production API nothing else the team builds can be used or demonstrated.

**Independent Test**: Deploy the current backend, then call it from a browser page served by the web app's origin and receive a valid response; call it from an unrelated origin and have the browser block the response.

**Acceptance Scenarios**:

1. **Given** the backend has been deployed, **When** the web app calls any public endpoint over HTTPS, **Then** it receives the endpoint's normal response.
2. **Given** the backend has been deployed, **When** a browser page on any origin other than the official web app calls the API, **Then** the browser refuses to expose the response to that page.
3. **Given** a request is made over plain HTTP, **When** it reaches the API address, **Then** it is not served unencrypted.

---

### User Story 2 - The web app knows when the backend is ready (Priority: P1)

When a student opens GerminaWiki, the web app first asks the backend whether it is ready. That same request wakes the backend and its database if they were idle, so by the time the student interacts, the system is warm. The answer tells the web app whether to proceed, keep showing its waiting screen, or show a retry option.

**Why this priority**: This is how the web app hides idle wake-up delays from students, and it's the minimum signal needed to tell "slow" apart from "broken".

**Independent Test**: With the system idle, call the readiness check and confirm it reports ready only once the database is actually reachable. With the database unreachable, confirm it reports not ready instead of hanging or reporting ready.

**Acceptance Scenarios**:

1. **Given** the backend and database are available, **When** the readiness check is called, **Then** it reports "ready".
2. **Given** the backend is running but the database cannot be reached, **When** the readiness check is called, **Then** it reports "not ready" within a bounded time and does not report "ready".
3. **Given** anyone, signed in or not, **When** they call the readiness check, **Then** it answers without requiring credentials and without revealing internal details (versions, hostnames, connection info, stack traces).

---

### User Story 3 - The first visit after idle still feels quick (Priority: P2)

A student opens GerminaWiki in the morning after nobody has used it for hours. The first response arrives fast enough that the web app's short waiting screen is enough, and later requests feel instant.

**Why this priority**: With a dozen users the system is idle most of the time, so almost every session starts from idle. Slow first loads would be the main thing students notice about the app's quality.

**Independent Test**: Leave the system idle for at least 30 minutes, call the readiness check, and measure the time to answer; then measure a follow-up request.

**Acceptance Scenarios**:

1. **Given** the system has been idle for at least 30 minutes, **When** the readiness check is called, **Then** it answers within the target in SC-003.
2. **Given** a request was answered in the last few minutes, **When** another request arrives, **Then** it answers within the target in SC-004.

---

### User Story 4 - The team can deploy, update and roll back safely at no cost (Priority: P2)

A team member ships a new backend version by following one documented procedure from the repository, without hand-editing cloud settings. If the new version is broken, they can put the previous version back. At no point does running the backend generate a bill, and if usage ever approaches a paid tier, the team is warned before being charged.

**Why this priority**: Production is the only environment, so every deploy goes straight to students. A repeatable deploy with a rollback path and a cost guard is what makes that acceptable.

**Independent Test**: Deploy version A, deploy version B, roll back to A and confirm A is serving; review the billing dashboard after a month of normal use and confirm the charge is zero.

**Acceptance Scenarios**:

1. **Given** a clean checkout of the repository and the documented prerequisites, **When** a team member follows the deploy procedure, **Then** the backend is created or updated without any manual cloud-console step.
2. **Given** a newly deployed version is faulty, **When** a team member follows the rollback procedure, **Then** the previous version serves traffic again.
3. **Given** a normal month of use, **When** the billing period closes, **Then** the hosting charge is zero.
4. **Given** spending is forecast or recorded above the alert threshold, **When** the threshold is crossed, **Then** the account owner is notified by email.

---

### Edge Cases

- A new version fails to start after deploy: traffic must keep going to the last working version, or be restored to it by rollback, without a manual rebuild.
- The database is waking up and takes several seconds: the readiness check reports ready once the database answers, as long as that's within its time limit, not an error.
- The database is down for longer than the readiness time limit: the check reports "not ready" and the web app can offer a retry.
- A burst of simultaneous first requests after idle (e.g., a class opening the app together) must all succeed, without failures caused by exhausted database connections.
- Secrets (database credentials, storage keys) must never appear in the repository, in logs, or in readiness responses.
- A request body larger than the platform limit (e.g., an image sent directly to the API) is rejected with a clear error. Large files go directly to storage, handled in a later feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The backend MUST be publicly reachable at a stable HTTPS address that doesn't change between deploys.
- **FR-002**: The backend MUST allow browser calls only from the official web app origin(s) and MUST reject cross-origin browser access from any other origin.
- **FR-003**: The backend MUST expose an unauthenticated readiness check that returns "ready" only after confirming the database answers a query.
- **FR-004**: The readiness check MUST return "not ready" within a bounded time when the database is unreachable, and MUST NOT expose internal details.
- **FR-005**: The backend MUST read all secrets and environment-specific settings (database connection, allowed origins) from its runtime configuration, never from files committed to the repository.
- **FR-006**: The complete hosting setup MUST be described in version-controlled files, so that deploying it requires no manual console configuration beyond one-time account setup.
- **FR-007**: Each deploy MUST produce an identifiable version, and the team MUST be able to route traffic back to the previous version.
- **FR-008**: The backend MUST run in the same geographic region as the database (South America – São Paulo).
- **FR-009**: The hosting setup MUST stay within the providers' permanent free usage limits at the expected load, and MUST NOT include any component billed per hour regardless of usage.
- **FR-010**: The account MUST have a spending alert that notifies the owner by email once actual or forecast monthly cost exceeds USD 1.
- **FR-011**: The backend MUST NOT keep itself or the database artificially awake (no scheduled keep-alive traffic), so that idle periods consume no free-tier compute.
- **FR-012**: Application logs MUST be kept long enough to diagnose recent incidents, and bounded in retention so storage stays within free limits.
- **FR-013**: The deploy and rollback procedures, prerequisites, and the required runtime configuration keys MUST be documented in the repository.

### Key Entities

- **Deployment version**: An immutable, identifiable release of the backend that can receive traffic; the "current" pointer can be moved between versions for rollback.
- **Runtime configuration**: The set of named settings and secrets the backend needs to run in production (database connection, allowed web origins). Values live outside the repository; names are documented.
- **Readiness status**: The result of a readiness check, either "ready" or "not ready", with no further detail exposed publicly.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The web app can call the production API from its official address, and 100% of calls from other browser origins are refused.
- **SC-002**: Monthly hosting cost is USD 0.00 for at least the first three months of operation at the expected load (≤ 15 active users).
- **SC-003**: After ≥ 30 minutes of idle, the first readiness check answers in under 3 seconds in at least 9 of 10 trials.
- **SC-004**: When the system is warm, readiness checks answer in under 300 ms in at least 9 of 10 trials, measured from the São Paulo region.
- **SC-005**: A team member who has never deployed the backend can complete a deploy by following the documentation alone in under 15 minutes, excluding one-time account setup.
- **SC-006**: A rollback to the previous version takes under 5 minutes from decision to the previous version serving traffic.
- **SC-007**: When the database is unreachable, the readiness check answers "not ready" in under 10 seconds in 100% of trials.

## Assumptions

- Expected load is about a dozen students, well under 10,000 requests per month.
- Only a production environment exists; there is no staging or dev deployment.
- The database already exists on a serverless Postgres provider in the São Paulo region; its wake-up delay after idle is outside this feature's control and is budgeted within SC-003.
- The provider's default HTTPS address is acceptable; a custom domain is out of scope.
- Frontend hosting (the web app's own deployment) is handled in the frontend repository and is out of scope. This feature only needs to know the web app's origin for FR-002.
- Automated deploys on merge (CI/CD) are a separate feature (`infra-cicd`). This feature delivers a documented, repeatable deploy procedure that the pipeline will later run.
- Image and file storage is a separate feature (`infra-storage`).
- The cloud account owner will create the account, enable MFA and perform one-time setup before the first deploy.
