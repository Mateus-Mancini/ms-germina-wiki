# Quickstart & Validation: Backend Hosting

This is the run guide that proves feature `001-backend-hosting` works end to end. Contracts: [health.openapi.yaml](contracts/health.openapi.yaml), [runtime-config.md](contracts/runtime-config.md).

## Prerequisites

**One-time account setup** (owner):
- An AWS account with MFA on root.
- An IAM admin user with MFA and console access only (no access keys). The CLI authenticates with short-lived credentials via `aws login`; root is never used day to day (FR-016). IAM Identity Center isn't used: without AWS Organizations it can't grant account access, and joining an Organization would move the account off the Free plan.
- Free Tier usage alerts enabled in Billing preferences.

**Tools on the owner's machine**:
- JDK 21
- Docker (for Testcontainers)
- AWS CLI v2
- AWS SAM CLI

**Secrets**: the Neon **pooled** connection string plus username and password, exported in the shell only:
```bash
export DB_URL='jdbc:postgresql://<pooler-host>/neondb?sslmode=require' DB_USER='...' DB_PASS='...'
```

## 1. Local validation (no cloud, no prod DB)

```bash
./mvnw test                   # unit + @WebMvcTest + Testcontainers integration tests
./mvnw spring-boot:test-run   # app on :8080 with a throwaway Postgres container
curl -i localhost:8080/health # expect 200 {"status":"ready"}, Cache-Control: no-store
```

Stop the Postgres container (`docker stop <id>`) and call `/health` again. Expect `503 {"status":"not_ready"}` in under 10 s (SC-007).

## 2. Deploy (US4, SC-005: time this, target < 15 min)

```bash
./mvnw -Plambda -DskipTests package
source ~/.config/germinawiki/prod.env
sam deploy --parameter-overrides DbUrl="$DB_URL" DbUsername="$DB_USER" DbPassword="$DB_PASS" \
  WebAppOrigin="$WEB_APP_ORIGIN" AlertEmail="$ALERT_EMAIL"
```
Then confirm the SNS subscription email (one click), and note the `ApiUrl`, `ApiFunctionName` and `LiveVersion` outputs.

## 3. Reachability and CORS (US1, SC-001)

```bash
curl -i "$API_URL/health"                                          # 200 ready
curl -i -H 'Origin: https://germinawiki.web.app' "$API_URL/health" # has Access-Control-Allow-Origin
curl -i -H 'Origin: http://localhost:3000' "$API_URL/health"       # has Access-Control-Allow-Origin
curl -i -H 'Origin: https://evil.example' "$API_URL/health"        # NO Access-Control-Allow-Origin
curl -i "http://${API_URL#https://}/health"                        # not served over plain HTTP
```

## 4. Cold and warm latency (US3, SC-003, SC-004)

Leave the API idle for at least 30 minutes, then:
```bash
for i in 1 2; do curl -o /dev/null -s -w '%{http_code} %{time_total}s\n' "$API_URL/health"; done
```
Expect the first call < 3 s and the second < 300 ms. Repeat on 10 separate cold occasions for SC-003. For the server-side view, check `Restore Duration` / `Duration` in the latest `REPORT` lines:
```bash
sam logs --stack-name ms-germina-wiki --tail
```

## 5. Rollback (US4, SC-006: target < 5 min)

```bash
aws lambda list-versions-by-function --function-name "$FN" --query 'Versions[-2:].Version'
aws lambda update-alias --function-name "$FN" --name live --function-version <previous>
curl -s "$API_URL/health"   # served by the previous version
```

## 6. Concurrency cap and shutoff (FR-014, FR-015, SC-008)

1. Check the cap:
   ```bash
   aws lambda get-account-settings --query 'AccountLimit.ConcurrentExecutions'
   ```
   Expect `10`; this is the cap per research R6.
2. Check that over-cap requests are refused:
   ```bash
   seq 60 | xargs -P 60 -I{} curl -s -o /dev/null -w '%{http_code}\n' "$API_URL/health" | sort | uniq -c
   ```
   Expect a mix of `200` and `429`, and no timeouts.
3. Simulate the shutoff by invoking the guard with a test threshold:
   ```bash
   aws lambda invoke --function-name <GuardFunctionName> --payload '{"thresholdOverride":0}' --cli-binary-format raw-in-base64-out /dev/stdout
   ```
   Expect the email to arrive, `get-function-concurrency` to show `0`, and `/health` to return `429`.
4. Check that a redeploy doesn't clear the shutoff: run `sam deploy` again, and `get-function-concurrency` must still show `0` (research R7-b).
5. Re-enable:
   ```bash
   aws lambda delete-function-concurrency --function-name "$FN"
   ```
   `/health` returns `200` again.

## 7. Cost checks (FR-009, FR-010, SC-002)

- Billing → Budgets: `germinawiki-monthly` exists at USD 1 with actual and forecast alerts.
- Billing → Bills, at each month end: total USD 0.00.
- CloudWatch → Log groups: retention shows 7 days (FR-012).
