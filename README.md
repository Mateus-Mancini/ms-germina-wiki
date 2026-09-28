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

## Running locally

Requires JDK 21 and Docker. Nothing local ever connects to the production database.

```bash
./mvnw spring-boot:test-run   # API on http://localhost:8080 with a throwaway PostgreSQL (Testcontainers)
curl localhost:8080/health    # {"status":"ready"}
```

The `local` profile allows CORS from `http://localhost:3000` for the web app.

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

## Database

The schema is versioned with Flyway; tests and local runs are migrated automatically. To change the schema, follow [docs/database-migrations.md](docs/database-migrations.md).

## Deployment

There is only production. **Every merge to `main` releases automatically**: checks, database migrations, deploy, health check, and rollback if unhealthy. See [docs/ci-cd.md](docs/ci-cd.md). Manual deploys ([docs/deployment.md](docs/deployment.md)) are the owner's fallback.

## Contributing

- Work on a branch per feature or task (Spec Kit features use `NNN-short-name`), and open a PR into `main`. `main` is protected and needs one approving review.
- Use [Conventional Commits](https://www.conventionalcommits.org/) in English, one logical change per commit, referencing task IDs from `tasks.md` where applicable.
