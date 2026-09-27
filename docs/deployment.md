# Deploying the GerminaWiki API

There is **only production**. The API runs on AWS Lambda in `sa-east-1` (São Paulo), next to the Neon database, and everything is defined in [`template.yaml`](../template.yaml). Design and rationale: [`specs/001-backend-hosting`](../specs/001-backend-hosting/).

Only the **account owner** deploys, rolls back or re-enables by hand (FR-016). Teammates ship through the CI/CD pipeline on merge (feature `infra-cicd`).

## What's deployed

| Resource | Purpose |
|---|---|
| `germinawiki-api` (Lambda, java21, arm64, 2048 MB, SnapStart) | Spring Boot API, behind a Function URL on alias `live` |
| `germinawiki-usage-guard` (Lambda, python3.13, every 10 min) | Stops the API at 80% of the Lambda free tier and emails the owner |
| `germinawiki-alerts` (SNS) | Shutoff emails |
| `germinawiki-monthly` (Budget, USD 1) | Actual and forecast cost alerts for the stack |
| Log groups (7-day retention) | `/aws/lambda/germinawiki-api`, `/aws/lambda/germinawiki-usage-guard` |

There is also an account-wide `account-safety-net` budget (USD 1), created by hand during account setup so it exists even without the stack.

## One-time account setup

1. Create the AWS account on the **Free plan**. Enable MFA on root and never create root access keys.
   - ⚠️ Upgrade to the Paid plan before the 6-month Free plan ends, or AWS closes the account. **Don't** join AWS Organizations or enable an organization instance of IAM Identity Center; that immediately upgrades the account and expires the credits.
2. Create the everyday admin as an **IAM user with console access, MFA and no access keys**, with `AdministratorAccess` attached. Use root only for billing-plan changes.
3. Sign the CLI in with short-lived credentials:
   ```bash
   aws login --profile germinawiki   # region sa-east-1; sessions last up to 12 h
   ```
4. Create the `account-safety-net` budget (USD 1, actual and forecast email alerts), and enable *Free Tier usage alerts* in Billing preferences.
5. Keep the deploy secrets in a local file outside the repo, readable only by you (`chmod 600`), e.g. `~/.config/germinawiki/prod.env`:
   ```bash
   export AWS_PROFILE=germinawiki
   export DB_URL='jdbc:postgresql://<endpoint>-pooler.<region>.aws.neon.tech/neondb?sslmode=require'
   export DB_USER='...' DB_PASS='...'
   export WEB_APP_ORIGIN='https://germinawiki.web.app'
   export ALERT_EMAIL='...'
   ```

**Tools:** JDK 21, Docker, AWS CLI v2, AWS SAM CLI.

## Runtime configuration

These are the stack parameters (contract: [`runtime-config.md`](../specs/001-backend-hosting/contracts/runtime-config.md)). Pass **all five** on every deploy: `--parameter-overrides` replaces, not merges, and `samconfig.toml` deliberately holds none.

| Parameter | Secret | Becomes |
|---|---|---|
| `DbUrl` | yes | `SPRING_DATASOURCE_URL`. Must be Neon's **pooled** (`-pooler`) endpoint |
| `DbUsername` | yes | `SPRING_DATASOURCE_USERNAME` |
| `DbPassword` | yes | `SPRING_DATASOURCE_PASSWORD` |
| `WebAppOrigin` | no | Function URL CORS origin (plus fixed `http://localhost:3000`) |
| `AlertEmail` | no | Budget and shutoff notifications. **Confirm** the SNS subscription email after the first deploy |

Changing any parameter publishes a new version (`AutoPublishAliasAllProperties`), so config changes reach `live` like code changes.

## Deploy

```bash
source ~/.config/germinawiki/prod.env
./mvnw test                                  # unit, web and Testcontainers tests (never touch Neon)
python3 -m unittest discover -s infra/guard  # usage guard tests
./mvnw -Plambda -DskipTests package          # target/wikigerminare-lambda.zip (reproducible)
scripts/smoke-lambda-package.sh              # runs the packaged zip against a throwaway Postgres
sam deploy --parameter-overrides DbUrl="$DB_URL" DbUsername="$DB_USER" DbPassword="$DB_PASS" \
  WebAppOrigin="$WEB_APP_ORIGIN" AlertEmail="$ALERT_EMAIL"
```

`sam deploy` shows the CloudFormation change set and asks before applying it (`confirm_changeset = true`). Read it: an API code or config change should show only `ApiFunction` (Modify), a new `ApiFunctionVersion…` (Add), `ApiFunctionAliaslive` (Modify), and the previous version's resource (Delete). That "Delete" only removes it from the template; SAM retains published versions, so rollback still works.

Then check it:
```bash
API_URL=$(aws cloudformation describe-stacks --stack-name ms-germina-wiki --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)
curl -s "${API_URL}health"     # {"status":"ready"}
```

**After the very first deploy only**, expire deploy artifacts in SAM's bucket after 1 day, so S3 storage stays at ~USD 0:
```bash
B=$(aws s3api list-buckets --query "Buckets[?starts_with(Name,'aws-sam-cli-managed-default')].Name" --output text)
aws s3api put-bucket-lifecycle-configuration --bucket "$B" --lifecycle-configuration \
  '{"Rules":[{"ID":"expire-deploy-artifacts","Status":"Enabled","Filter":{},"Expiration":{"Days":1},"NoncurrentVersionExpiration":{"NoncurrentDays":1},"AbortIncompleteMultipartUpload":{"DaysAfterInitiation":1}}]}'
```

## Roll back (target < 5 min)

Point `live` back at the previous version. No rebuild is needed, and the Function URL follows the alias:
```bash
aws lambda list-versions-by-function --function-name germinawiki-api --query 'Versions[-3:].Version'
aws lambda update-alias --function-name germinawiki-api --name live --function-version <previous>
```
The next `sam deploy` moves `live` to whatever the template builds, so fix forward from there.

## Automatic shutoff and re-enable

The usage guard checks month-to-date Lambda requests and GB-seconds every 10 minutes. At **80%** of either free limit it sets the API's reserved concurrency to **0**, so every request gets `429` and nothing is billed, and it emails the owner. It never re-enables the API by itself.

```bash
aws lambda get-function-concurrency --function-name germinawiki-api     # 0 = stopped
aws lambda delete-function-concurrency --function-name germinawiki-api  # re-enable (owner only)
```
Before re-enabling, find out what caused the traffic, because the next check will stop the API again while usage stays above 80%.

To drill the shutoff without real traffic (it stops production until re-enabled):
```bash
aws lambda invoke --function-name germinawiki-usage-guard --cli-binary-format raw-in-base64-out \
  --payload '{"thresholdOverride":0}' /dev/stdout
```

## Limits to know

- **At most 10 requests run at once**, account-wide. That's the quota of a new account and the API's burst cap. Excess requests get an immediate `429`, and clients should retry with backoff.
- **Request bodies are limited to 6 MB.** Images go straight to storage, never through the API.
- **After idle, the first request wakes Lambda (SnapStart restore) and Neon**, which takes ~1–2 s. The web app hides this with a `/health` call on load.
