# Data Model: Backend Hosting

**Feature**: `001-backend-hosting` | **Date**: 2026-09-27

This feature adds **no database tables**. Its entities are either API response shapes or operational state held by the cloud platform.

## ReadinessStatus (API response, DTO)

| Field | Type | Rules |
|---|---|---|
| `status` | enum string | `ready` or `not_ready`. There are no other fields. |

- `ready` ⇔ the DB answered `SELECT 1` within the statement timeout → HTTP 200.
- `not_ready` ⇔ connection failure, timeout, or any exception → HTTP 503.
- It never carries versions, hostnames, error messages or timings (FR-004).

## Deployment version (platform state)

| Attribute | Description |
|---|---|
| version number | An immutable Lambda version, published on every deploy (`AutoPublishAlias`) |
| alias `live` | A pointer to the version that receives traffic. The Function URL is bound to it |
| snapshot | The SnapStart snapshot created when the version is published |

State transitions:

```
deploy ──> version N published ──> snapshot ready ──> alias live → N
rollback: alias live → N-1   (no rebuild, no new snapshot)
```

## Runtime configuration (platform state)

| Key | Source | Secret |
|---|---|---|
| `SPRING_DATASOURCE_URL` | SAM parameter `DbUrl` (NoEcho) | yes |
| `SPRING_DATASOURCE_USERNAME` | SAM parameter `DbUsername` (NoEcho) | yes |
| `SPRING_DATASOURCE_PASSWORD` | SAM parameter `DbPassword` (NoEcho) | yes |
| `SPRING_PROFILES_ACTIVE` | template constant `lambda` | no |
| allowed CORS origins | SAM parameter `WebAppOrigin` + constant `http://localhost:3000`, applied to the Function URL | no |
| alert email | SAM parameter `AlertEmail` | no |

The full contract is in [contracts/runtime-config.md](contracts/runtime-config.md).

## Usage guard state (platform state)

| Attribute | Description |
|---|---|
| month-to-date requests | Sum of `AWS/Lambda Invocations` (all functions) since the 1st, 00:00 UTC |
| month-to-date GB-s | Σ per function of `Duration` (ms) / 1000 × memory (GB) |
| threshold | 80% of 1,000,000 requests / 400,000 GB-s |
| API state | `serving` (no reserved concurrency) or `stopped` (reserved concurrency = 0) |

Transitions:

```
serving ──(guard: usage ≥ 80%)──> stopped + email
stopped ──(owner: delete-function-concurrency)──> serving
```

The guard never re-enables the API automatically.
