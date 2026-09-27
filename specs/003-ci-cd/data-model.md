# Data Model: CI/CD Pipelines

**Feature**: `003-ci-cd` | **Date**: 2026-09-27

This feature has no application data. Its entities are pipeline and platform state, fully listed in [contracts/pipelines.md](contracts/pipelines.md).

| Entity | Attributes | Rules |
|---|---|---|
| Check run | repo, PR, job name, status | `build-test-package` / `lint-build` must be green to merge (FR-003) |
| Release run | repo, commit, steps, outcome, previous/new version | one at a time per repo (`concurrency: production`); ordered steps (FR-005–FR-007) |
| Environment | name, branch policy, reviewers, secrets, variables | `production` is `main`-only; `neon-rehearsal` is owner-approved |
| Deploy identity | AWS role ARN, trust subject, permissions | trusted only for `environment:production` of the backend repo; least privilege; 1 h sessions |

## Release state machine (backend)

```
checks ──fail──> FAILED (nothing changed)
  │ pass
migrate ──fail──> FAILED (DB at last good migration; API untouched)
  │ ok
record live=N ─> deploy (live=N+1) ─> verify /health
                                          ├─ 200 ready ──> SUCCESS
                                          ├─ 429 ────────> FAILED (cost guard; no rollback)
                                          └─ other ──────> rollback live=N ──> FAILED
```
