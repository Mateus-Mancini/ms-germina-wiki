# Quickstart & Validation: CI/CD Pipelines

This proves feature `003-ci-cd` works. Contract: [contracts/pipelines.md](contracts/pipelines.md). The one-time setup commands are in `docs/ci-cd.md` (backend repo).

## 1. PR checks (US1, SC-001, SC-002)

- **Backend:** open a PR with a trivial change → `build-test-package` goes green in under 10 min. Then push a commit that breaks a test → the check turns red and **Merge** is blocked.
- **Web app:** open a PR → `lint-build` goes green in under 5 min. Then push a lint error → red, merge blocked.
- **Fork safety:** check that `ci.yml` jobs without an environment receive no secrets (the workflow file declares none).

## 2. Migration rehearsal on PRs (FR-004)

Open a backend PR that adds a harmless migration (e.g. `V2__noop.sql` containing `SELECT 1;`). Expect:
1. `rehearse-migrations` waits for owner approval (environment `neon-rehearsal`).
2. After approval, the log shows the branch created → migrated → deleted.
3. `npx neonctl branches list` shows only `production`.

Close the PR without merging.

## 3. Backend release (US2, SC-003)

Merge a trivial backend PR. In Actions → `release`, expect:
1. `checks` passes
2. migrate reports "up to date"
3. deploy publishes version N+1
4. verify gets `200 ready`

The job summary lists the previous and new versions. Total under 15 min.

## 4. Automatic rollback (FR-014, SC-008)

Run `release` via **workflow_dispatch** with the input `simulate_failed_verify=true`: the verify step then targets `/__rollback-drill`, which returns 404. Expect `live` to move back to the recorded version within 2 min, the run to fail, and `/health` to stay `200 ready`.

## 5. Failed migration stops the release (FR-006, SC-007)

Covered by construction (the deploy step only runs after migrate succeeds) and by 002's validation that a failed migration is atomic. Optionally, drill it on a Neon branch with the rehearsal job.

## 6. Web app release (US3, SC-004)

Merge a web app PR. The `deploy` job publishes to the `live` channel, and `https://germinawiki.web.app` serves the new build in under 5 min.

## 7. Credentials (FR-008, SC-005)

- In IAM, the role `germinawiki-github-deploy` has a trust subject of exactly `repo:Mateus-Mancini/ms-germina-wiki:environment:production`.
- In repo settings, no `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` exist.
- A workflow run from a non-`main` branch that tries to use environment `production` is refused by the environment's branch policy.

## 8. Cost (SC-006)

Actions minutes are free for public repos. There are no new AWS resources besides IAM (free), and Firebase Hosting stays on Spark.
