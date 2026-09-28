# Quickstart & Validation: Image Storage

This proves feature `005-image-storage` works. Contract: [contracts/images.openapi.yaml](contracts/images.openapi.yaml).

## 1. Local tests (no cloud)

```bash
./mvnw test   # includes ImageFlowIntegrationTest: PostgreSQL 18 + MinIO (S3-compatible)
```
This covers presign → HTTP PUT → confirm → 302 redirect → delete; a type or size mismatch on confirm (409, object removed); a foreign upload key (403); no principal (401); a missing page (404); and the V2 trigger queueing keys when a page is deleted.

## 2. One-time R2 setup (owner)

See `docs/image-storage.md`: account, bucket `germinawiki-images`, CORS from `infra/r2/cors.json`, the lifecycle rule `pending/` 1 day, a bucket-scoped token, then the four values in `prod.env` and the GitHub `production` environment.

Verify:
```bash
npx wrangler r2 bucket cors list germinawiki-images
npx wrangler r2 bucket lifecycle list germinawiki-images   # pending/ expires after 1 day
```

## 3. Production (after merge; the release applies V2 and deploys)

Until auth-api exists, upload and confirm return **401**. Check that first:
```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$API_URL/api/pages/<any-uuid>/images/uploads" \
  -H 'Content-Type: application/json' -d '{"contentType":"image/png","size":10}'   # expect 401
curl -s -o /dev/null -w '%{http_code}\n' "$API_URL/api/images/00000000-0000-0000-0000-000000000000" # expect 404
```

End-to-end against **real R2** is checked by `R2LiveSmokeTest`, which runs only when the storage env vars are set and is skipped in CI. It uses the **same** `ObjectStorage` bean as the API: presign a PUT → upload a 1 KB PNG over HTTP → `HEAD` → copy → presign a GET → download and compare → delete. This proves the R2-specific SDK settings (research R2) without needing auth:
```bash
source ~/.config/germinawiki/prod.env
./mvnw test -Dtest=R2LiveSmokeTest
```

## 4. Cleanup job

```bash
aws lambda invoke --function-name germinawiki-api:live --cli-binary-format raw-in-base64-out \
  --payload '{"source":"germinawiki.image-cleanup"}' /dev/stdout   # {"deleted":N}
```

## 5. Cost

R2 dashboard → Metrics: Class A and B operations and storage all show as near zero. Billing shows USD 0.00.
