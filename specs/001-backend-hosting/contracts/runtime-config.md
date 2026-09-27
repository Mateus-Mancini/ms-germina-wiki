# Contract: Runtime Configuration and Deployment Interface

This is the interface between the repository and the production environment: what a deploy must be given, and what it produces. The CI/CD feature (`infra-cicd`) consumes this contract.

## Deploy parameters (`sam deploy --parameter-overrides`)

| Parameter | Required | Secret | Example | Purpose |
|---|---|---|---|---|
| `DbUrl` | yes | yes (NoEcho) | `jdbc:postgresql://ep-xxx-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require` | Neon **pooled** endpoint |
| `DbUsername` | yes | yes (NoEcho) | `neondb_owner` | |
| `DbPassword` | yes | yes (NoEcho) | — | |
| `WebAppOrigin` | yes | no | `https://germinawiki.web.app` | Official web app origin for CORS (FR-002) |
| `AlertEmail` | yes | no | owner's email | Budget alert + guard shutoff notifications |

Never commit values for the secret parameters. `samconfig.toml` holds only non-secret defaults (stack name, region, capabilities, `WebAppOrigin`).

## Stack outputs

| Output | Description |
|---|---|
| `ApiUrl` | Function URL of alias `live`. The web app's API base URL, stable across deploys |
| `ApiFunctionName` | Needed for rollback and re-enable commands |
| `LiveVersion` | Version currently behind `live` |

## Owner-only operations

| Operation | Command (summary; exact steps in `quickstart.md`) |
|---|---|
| Deploy | `./mvnw -Plambda -DskipTests package && sam deploy` |
| Roll back | `aws lambda update-alias --function-name <ApiFunctionName> --name live --function-version <N-1>` |
| Re-enable after shutoff | `aws lambda delete-function-concurrency --function-name <ApiFunctionName>` |
| Check stopped state | `aws lambda get-function-concurrency --function-name <ApiFunctionName>` (a value of `0` means stopped) |

## Fixed platform settings (template constants, not parameters)

- Region `sa-east-1`, runtime `java21`, architecture `arm64`, memory 2048 MB, timeout 20 s
- SnapStart on published versions; alias `live`
- Function URL `AuthType: NONE`; CORS origins = `WebAppOrigin` + `http://localhost:3000`; methods GET/POST/PUT/PATCH/DELETE; headers `Authorization`, `Content-Type`
- Log retention 7 days
- Budget USD 1/month (actual + forecast)
- Guard schedule `rate(10 minutes)`, threshold 80%
- No VPC
