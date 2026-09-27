# CI/CD

Both repositories use GitHub Actions. Design: [`specs/003-ci-cd`](../specs/003-ci-cd/); contract: [`pipelines.md`](../specs/003-ci-cd/contracts/pipelines.md).

| Repository | On pull request | On merge to `main` |
|---|---|---|
| **ms-germina-wiki** (API) | `build-test-package`: tests, guard tests, Lambda package + smoke test, template lint. `rehearse-migrations`: only if migrations changed, after owner approval | `release`: checks → **migrations** → deploy → `/health` → **automatic rollback** if unhealthy |
| **GerminaWiki** (web app) | `lint-build`: lint + static export | `release`: checks → publish to Firebase Hosting (`https://germinawiki.web.app`) |

Releases run one at a time (`concurrency: production`). The job summary shows the previous and deployed API versions and the readiness result.

## How a backend release behaves

- **Migrations fail** → the release stops. The API isn't deployed, and the database stays at the last good migration.
- **`/health` isn't `200 ready` within ~60 s** → `live` is moved back to the previous version automatically, and the run fails.
- **`/health` returns `429`** → the usage guard has stopped the API (cost protection). The run fails without a rollback; re-enable per [deployment.md](deployment.md).
- **Rollback drill**: Actions → `release` → *Run workflow* → tick `simulate_failed_verify`.
- If two merges land close together, the older run may stop at the migration step, because `main` has moved on. The newer run, queued right behind it, does the release.

Rollback restores **code, not schema**, so migrations must stay backward-compatible with the previous release (see [database-migrations.md](database-migrations.md)).

---

## One-time setup (account owner)

Run from `ms-germina-wiki/` with your secrets loaded. Nothing below prints a secret.

```bash
source ~/.config/germinawiki/prod.env
R=Mateus-Mancini/ms-germina-wiki
F=Mateus-Mancini/GerminaWiki
```

### 1. AWS: OIDC trust and the deploy role

```bash
aws cloudformation deploy --stack-name germinawiki-github-oidc --template-file infra/github-oidc.yaml \
  --capabilities CAPABILITY_NAMED_IAM --region sa-east-1
ROLE_ARN=$(aws cloudformation describe-stacks --stack-name germinawiki-github-oidc --region sa-east-1 \
  --query "Stacks[0].Outputs[?OutputKey=='RoleArn'].OutputValue" --output text)
echo "$ROLE_ARN"   # arn:aws:iam::<account>:role/germinawiki-github-deploy
```
The role only trusts GitHub tokens from `ms-germina-wiki`'s **production** environment. Every IAM role the app stack creates must carry the `germinawiki-workload-boundary` permissions boundary.

### 2. Backend environment `production` (main only)

```bash
gh api -X PUT "repos/$R/environments/production" --input - <<<'{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' >/dev/null
gh api -X POST "repos/$R/environments/production/deployment-branch-policies" -f name=main -f type=branch >/dev/null
printf %s "$DB_URL"  | gh secret set DB_URL  --env production -R "$R"
printf %s "$DB_USER" | gh secret set DB_USER --env production -R "$R"
printf %s "$DB_PASS" | gh secret set DB_PASS --env production -R "$R"
gh variable set AWS_ROLE_ARN   --env production -R "$R" --body "$ROLE_ARN"
gh variable set ALERT_EMAIL    --env production -R "$R" --body "$ALERT_EMAIL"
gh variable set WEB_APP_ORIGIN --env production -R "$R" --body "$WEB_APP_ORIGIN"
```

### 3. Backend environment `neon-rehearsal` (owner approval)

1. 🌐 Create a Neon API key: Neon console → your avatar → **Account settings → API keys → Create**. Copy it once.
2. Then:
   ```bash
   OWNER_ID=$(gh api user --jq .id)
   gh api -X PUT "repos/$R/environments/neon-rehearsal" --input - <<<"{\"reviewers\":[{\"type\":\"User\",\"id\":$OWNER_ID}]}" >/dev/null
   gh secret set NEON_API_KEY --env neon-rehearsal -R "$R"          # paste the key when prompted
   gh variable set NEON_PROJECT_ID --env neon-rehearsal -R "$R" --body "$NEON_PROJECT_ID"
   ```

### 4. Web app: environment `production` and the Firebase service account

```bash
gh api -X PUT "repos/$F/environments/production" --input - <<<'{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' >/dev/null
gh api -X POST "repos/$F/environments/production/deployment-branch-policies" -f name=main -f type=branch >/dev/null
cd ../GerminaWiki
git fetch && git switch 003-ci-cd   # or main, once it has firebase.json
firebase init hosting:github --project germinawiki
```
Answers:
- **Repository:** `Mateus-Mancini/GerminaWiki`
- **"Set up the workflow to run a build script before every deploy?"** → **No**
- **"Set up automatic deployment … when a PR is merged?"** → **No**

The command creates a Hosting-only service account and stores its key as the repo secret `FIREBASE_SERVICE_ACCOUNT_GERMINAWIKI`. If it generates any `.github/workflows/firebase-*.yml` or `firebase.json` changes, discard them (`git checkout -- . && git clean -fd .github/workflows/firebase-*`). Our workflows are already in the repo.

> **Accepted risk**: this key is long-lived and stored at repository level, so a workflow added in a same-repo PR could read it. It can only publish to Firebase Hosting. The upgrade path is keyless Workload Identity Federation (needs `gcloud`).

### 5. Required checks (after each workflow has run once)

```bash
protect() {  # $1 = repo, $2 = required check
  gh api -X PUT "repos/$1/branches/main/protection" --input - >/dev/null <<EOF
{"required_status_checks":{"strict":false,"contexts":["$2"]},"enforce_admins":false,
 "required_pull_request_reviews":{"required_approving_review_count":1,"dismiss_stale_reviews":true},
 "restrictions":null,"allow_force_pushes":false,"allow_deletions":false}
EOF
}
protect "$R" build-test-package
protect "$F" lint-build
```

### Check

```bash
gh api "repos/$R/environments" --jq '.environments[].name'   # neon-rehearsal, production
gh secret list --env production -R "$R"                      # DB_PASS, DB_URL, DB_USER
gh variable list --env production -R "$R"                    # ALERT_EMAIL, AWS_ROLE_ARN, WEB_APP_ORIGIN
gh secret list -R "$F"                                       # FIREBASE_SERVICE_ACCOUNT_GERMINAWIKI
```
