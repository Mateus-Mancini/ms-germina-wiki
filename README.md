# ms-germina-wiki

[![ci](https://github.com/Mateus-Mancini/ms-germina-wiki/actions/workflows/ci.yml/badge.svg)](https://github.com/Mateus-Mancini/ms-germina-wiki/actions/workflows/ci.yml) [![release](https://github.com/Mateus-Mancini/ms-germina-wiki/actions/workflows/release.yml/badge.svg)](https://github.com/Mateus-Mancini/ms-germina-wiki/actions/workflows/release.yml)

Backend API for **GerminaWiki**, a wiki where students of Germinare (Instituto J&F) document their experience at the school. It's built with Spec-Driven Development using [Spec Kit](https://github.com/github/spec-kit).

**Stack:** Java 21 · Spring Boot 4 · PostgreSQL (Neon) · AWS Lambda (SnapStart) behind a Function URL, in `sa-east-1`.

## Spec-Driven Development

Every feature goes through the Spec Kit cycle: `specify → clarify → plan → tasks → analyze → implement`. Each phase's artifacts live in [`specs/`](specs/), one folder per feature, and the project principles are in the [constitution](.specify/memory/constitution.md).

The Spec Kit commands are installed for **GitHub Copilot** (`.github/skills/`, PowerShell scripts) and **Claude Code** (`.claude/skills/`, bash scripts).

| Feature | Status |
|---|---|
| [001-backend-hosting](specs/001-backend-hosting/) | Production hosting, `/health` readiness, cold-start and cost guards |
| [002-database-migrations](specs/002-database-migrations/) | Versioned schema (Flyway), tests on the real schema, safe production migrations |
| [003-ci-cd](specs/003-ci-cd/) | PR checks and automatic releases (migrations, deploy, rollback) for API and web app |
| [005-image-storage](specs/005-image-storage/) | Page images: direct uploads to private Cloudflare R2, stable redirect addresses, cleanup |
| [011-rbac-middleware](specs/011-rbac-middleware/) | Reusable `@AdminOnly` guard: 401/403/allow authorization for admin-only operations, see [docs/rbac-middleware.md](docs/rbac-middleware.md) |
| [012-user-signup](specs/012-user-signup/) | Public member registration, validation and duplicate-email protection; reuses existing login and profile APIs |

## Running locally

Requires JDK 21 and Docker. Nothing local ever connects to the production database.

```bash
./mvnw spring-boot:test-run   # API on http://localhost:8080 with throwaway PostgreSQL + MinIO (Testcontainers)
curl localhost:8080/health    # {"status":"ready"}
```

The `local` profile allows CORS from `http://localhost:3000` for the web app.

### Authentication

The API requires a local JWT signing key and token lifetime. Generate a disposable
32-byte key for the current PowerShell session (never commit or reuse it in production):

```powershell
$key = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($key)
$rng.Dispose()
$env:APP_AUTH_JWT_SECRET_BASE64 = [Convert]::ToBase64String($key)
$env:APP_AUTH_JWT_TTL_SECONDS = "900"
```

Create a member account through public `POST /api/auth/register`:

```json
{"name":"Student","email":"student@example.com","password":"correct password"}
```

Registration returns `201 Created` with the user's safe profile and a `Location`
header. Then call `POST /api/auth/login` with `email` and `password`, and use the
returned Bearer token for `GET /api/users/me` and other protected routes. Registration
does not issue a token or accept administrator roles.

Name and email have outside whitespace removed; email remains case-sensitive as
in the existing login. Name is required and at most 150 characters, email must be
valid and at most 255 characters, and password must be nonblank, at least 8 characters
and at most 72 UTF-8 bytes (without trimming or truncation). Invalid requests and
unknown fields return `400`; an existing email returns `409`, including simultaneous
registration attempts. Passwords are stored only as BCrypt hashes and are never
returned. See the [registration contract](specs/012-user-signup/contracts/registration.md)
and [validation guide](specs/012-user-signup/quickstart.md).

Existing accounts in `users` continue to use a BCrypt-compatible `password_hash`
and a `role` of `admin` or `member`.
Production must receive the same variables from the approved secrets manager through
environment infrastructure.

## Tests

```bash
./mvnw test                                    # all JVM tests (unit, @WebMvcTest, Testcontainers)
./mvnw test -Dtest=HealthServiceTest           # one class
./mvnw test -Dtest=HealthServiceTest#reportsReadyWhenDatabaseAnswers   # one method
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s infra/guard   # usage-guard Lambda
```

## Project layout

```text
src/main/java/com/wikigerminare/
├── controller/  service/  repository/  dto/   # Controller → Service → Repository (constitution II)
├── config/                                    # Spring configuration
└── lambda/                                    # Lambda entry point + SnapStart priming (AWS only)
src/main/resources/db/migration/               # Flyway migrations (V1 = team schema); see docs/database-migrations.md
template.yaml, samconfig.toml                  # production infrastructure (AWS SAM)
infra/guard/                                   # usage guard: stops the API near free-tier limits
scripts/smoke-lambda-package.sh                # pre-deploy smoke test of the Lambda zip
scripts/db-migrate.sh                          # rehearse | production | info (database migrations)
docs/deployment.md                             # deploy, rollback, re-enable
```

## Image storage

Images go straight from the browser to a private Cloudflare R2 bucket (presigned URLs); `GET /api/images/{id}` redirects to a short-lived signed URL. Tests use MinIO. Setup and operations: [docs/image-storage.md](docs/image-storage.md).

## Database

The schema is versioned with Flyway; tests and local runs are migrated automatically. To change the schema, follow [docs/database-migrations.md](docs/database-migrations.md).

## Deployment

There is only production. **Every merge to `main` releases automatically**: checks, database migrations, deploy, health check, and rollback if unhealthy. See [docs/ci-cd.md](docs/ci-cd.md). Manual deploys ([docs/deployment.md](docs/deployment.md)) are the owner's fallback.

## Contributing

- Work on a branch per feature or task (Spec Kit features use `NNN-short-name`), and open a PR into `main`. `main` is protected and needs one approving review.
- Use [Conventional Commits](https://www.conventionalcommits.org/) in English, one logical change per commit, referencing task IDs from `tasks.md` where applicable.
