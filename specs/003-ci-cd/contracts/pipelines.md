# Contract: Pipelines, Environments, Secrets and Variables

This is the interface between the repositories and GitHub Actions: what each workflow does, when it runs, and exactly which settings it needs.

## Backend (`Mateus-Mancini/ms-germina-wiki`)

### Workflows

| File | Trigger | Jobs (in order) | Touches production? |
|---|---|---|---|
| `.github/workflows/ci.yml` | `pull_request` to `main`, `workflow_call` | `build-test-package` (required check); `rehearse-migrations` (only if `src/main/resources/db/migration/**` changed; env `neon-rehearsal`, needs owner approval) | No (the rehearsal uses a disposable Neon branch) |
| `.github/workflows/release.yml` | `push` to `main`, `workflow_dispatch` | `checks` (calls `ci.yml`) → `release` (env `production`, concurrency `production`): migrate → record live version → deploy → verify → rollback on failure | Yes |

### Environments

| Environment | Branch policy | Reviewers | Holds |
|---|---|---|---|
| `production` | `main` only | none (the merge was already reviewed) | secrets `DB_URL`, `DB_USER`, `DB_PASS`; variables `AWS_ROLE_ARN`, `ALERT_EMAIL`, `WEB_APP_ORIGIN` |
| `neon-rehearsal` | any | **owner required** | secret `NEON_API_KEY`; variable `NEON_PROJECT_ID` |

### AWS trust (`infra/github-oidc.yaml`, deployed once by the owner)

- The OIDC provider is `token.actions.githubusercontent.com`.
- The role `germinawiki-github-deploy` trusts `sub = repo:Mateus-Mancini/ms-germina-wiki:environment:production` and `aud = sts.amazonaws.com`.
- Its permissions are least-privilege, as listed in research R2. The stack outputs `RoleArn`, which becomes the variable `AWS_ROLE_ARN`.

### Release outcomes (job summary)

| Readiness after deploy | Pipeline result | Action |
|---|---|---|
| `200 {"status":"ready"}` within 60 s | ✅ success | none |
| `429` (cost guard stopped the API) | ❌ failed | no rollback; the owner follows the re-enable procedure |
| anything else | ❌ failed | `live` → previous version; both versions reported |
| migrations failed | ❌ failed | nothing deployed |

## Web app (`Mateus-Mancini/GerminaWiki`)

### Workflows

| File | Trigger | Jobs | Touches production? |
|---|---|---|---|
| `.github/workflows/ci.yml` | `pull_request` to `main`, `workflow_call` | `lint-build` (required check): `npm ci`, `npm run lint`, `npm run build` (static export to `out/`) | No |
| `.github/workflows/release.yml` | `push` to `main`, `workflow_dispatch` | `checks` (calls `ci.yml`) → `deploy` (env `production`, concurrency `production`): build → Firebase Hosting `live` channel | Yes |

### Environments and secrets

| Environment | Branch policy | Holds |
|---|---|---|
| `production` | `main` only | secret `FIREBASE_SERVICE_ACCOUNT_GERMINAWIKI` (Hosting-only service account, research R7) |

### Hosting config

- `firebase.json`: `hosting.public = "out"`, `cleanUrls: true`
- `.firebaserc`: default project `germinawiki`
- `next.config.ts`: `output: "export"`, `images.unoptimized: true`

## Required status checks (branch protection on `main`)

| Repository | Required checks | Plus |
|---|---|---|
| ms-germina-wiki | `build-test-package` | 1 approving review (existing) |
| GerminaWiki | `lint-build` | 1 approving review (existing) |
