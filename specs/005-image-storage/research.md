# Research: Image Storage

**Feature**: `005-image-storage` | **Date**: 2026-09-28

## R1. Storage provider and access

- **Decision**: **Cloudflare R2**, **private** bucket `germinawiki-images`, accessed through its S3-compatible API with the **AWS SDK for Java v2** (`software.amazon.awssdk:s3`, BOM-managed), region `auto`, endpoint `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`.
- **Rationale**: R2's free tier is permanent: 10 GB-month, 1M Class A (writes, lists) and 10M Class B (reads) operations per month, and **no egress fees**. The same SDK code would work against S3 if the team ever moved (FR-010).
- **Alternatives considered**: S3 (the new account has no 12-month free tier, and egress is billed); Firebase Storage (needs the Blaze plan).

## R2. SDK settings required by R2

- **Decision**: `S3Client` and `S3Presigner` built with:
  - `requestChecksumCalculation(WHEN_REQUIRED)` and `responseChecksumValidation(WHEN_REQUIRED)`
  - `S3Configuration.chunkedEncodingEnabled(false)`
  - `pathStyleAccessEnabled(true)`
  - the lightweight `url-connection-client` HTTP client (no Apache or Netty), to keep the Lambda zip and cold start small.
- **Rationale**: since SDK 2.30 the SDK sends CRC32 checksum headers by default, and R2 rejects them (`x-amz-checksum-algorithm … not implemented`). Chunked encoding causes signature mismatches on R2. Both fixes are Cloudflare's documented settings.

## R3. Upload flow (FR-001–FR-005, FR-008)

- **Decision**: a two-step direct upload.
  1. `POST /api/pages/{pageId}/images/uploads` (signed-in user; the page must exist) validates the content type (JPEG, PNG, WebP, GIF) and the declared size (1 B–5 MB). It returns a **presigned PUT** URL for the key `pending/{pageId}/{userId}/{uuid}`, which expires after 10 min. `Content-Type` and `Content-Length` are part of the signature, so R2 rejects a different type or size.
  2. The browser `PUT`s the file straight to R2.
  3. `POST /api/pages/{pageId}/images` with `{uploadKey, fileName}`:
     - checks that the key belongs to this page **and this user** (the prefix), so nobody can confirm someone else's upload
     - `HEAD`s the object and rejects it if the type or size doesn't match what was allowed (deleting it)
     - `CopyObject` to `images/{uuid}`, deletes the pending object, and inserts `page_images`.
- **Cleanup**: an R2 lifecycle rule expires prefix `pending/` after **1 day** (SC-004: under 48 h). The API never lists the bucket.
- **Cost**: per upload, 1 PUT + 1 HEAD + 1 COPY + 1 DELETE, i.e. about 3 Class A ops. At a few dozen images a week, that's far below 1M a month.

## R4. Reading images (FR-006, clarification)

- **Decision**: `GET /api/images/{id}` (public, no auth) looks up the row and answers **302** with `Location` = a presigned GET (valid 10 min) and `Cache-Control: private, max-age=300`, so a browser re-uses the redirect for 5 min, within the signature's life. Unknown or deleted id → 404.
- **Rationale**: this is what the clarification chose. The bucket stays private, deleting an image breaks its address immediately, and each view is one cheap Lambda request plus one Class B read.
- **Stored reference**: `page_images.file_url` holds the **object key** (`images/{uuid}`), never a signed URL (signed URLs expire). The public stable address is derived from the row id.

## R5. Deleting images and orphans (FR-007, FR-009)

- **Decision**:
  - `DELETE /api/images/{id}`: uploader only for now (admin once RBAC lands) → delete the row and the object.
  - **Orphans from page deletion**: migration **V2** adds a table `image_object_deletions(object_key, queued_at)` and an `AFTER DELETE` trigger on `page_images`, so every removed row (including cascades from `pages`) queues its key.
  - A **daily cleanup** deletes the queued objects and their queue rows. It runs inside the API Lambda: EventBridge Scheduler invokes the `live` alias **directly** (not through the public Function URL) with the payload `{"source":"germinawiki.image-cleanup"}`, and `StreamLambdaHandler` routes that payload to the cleanup service instead of the HTTP adapter. The scheduler role only has `lambda:InvokeFunction`, which is within the existing permissions boundary.
- **Rationale**: the pages API (Camilla) needs no changes, a failure just retries the next day, and the migration is purely additive (safe with automatic rollback, per the migration guide).
- **Alternatives considered**: a Python function (would need a Postgres driver layer); an internal HTTP endpoint (the Function URL is public); listing the bucket and diffing (more Class A ops, racy with new uploads).

## R6. Identity and roles (FR-002, FR-013)

- **Decision**: the controllers take `java.security.Principal`. If it's null, they return **401**. The UUID comes from `principal.getName()`, the convention auth-api will provide. *Updated after merging main (Spring Security):* signed-out requests get 401 from the security entry point, and admins are recognised from the `ROLE_ADMIN` authority of the authenticated principal, so the uploader **or an admin** may delete (FR-007).
- **Rationale**: this is the same pattern suggested in the folders review. The feature works as soon as auth lands, and fails safely (401, not 500) until then.

## R7. Page existence (FR-013)

- **Decision**: `ImageRepository.pageExists(pageId)` runs `SELECT EXISTS(SELECT 1 FROM pages WHERE id = ?)` through `JdbcTemplate`. There's no `Page` entity here; that's the pages-api's job.

## R8. Configuration and secrets (FR-011, FR-012)

- **Decision**: new SAM parameters `R2AccountId` (non-secret), `R2Bucket`, `R2AccessKeyId` and `R2SecretAccessKey` (both NoEcho) → env vars `APP_STORAGE_ACCOUNT_ID`, `APP_STORAGE_BUCKET`, `APP_STORAGE_ACCESS_KEY_ID`, `APP_STORAGE_SECRET_ACCESS_KEY` → `@ConfigurationProperties("app.storage")`. The owner's `prod.env` and the GitHub `production` environment each get the four values. The R2 API token is **scoped to the one bucket** with Object Read & Write.
- **Bucket CORS** (`infra/r2/cors.json`, applied with Wrangler): `PUT`, `GET` and `HEAD` from `https://germinawiki.web.app` and `http://localhost:3000`, header `Content-Type`.
- **Lifecycle**: `npx wrangler r2 bucket lifecycle add germinawiki-images pending-expiry pending/ --expire-days 1`.

## R9. Cold start

- **Decision**: build `S3Client` and `S3Presigner` as singleton beans at startup, so they're captured in the SnapStart snapshot. Priming also **presigns one dummy URL**, which is local computation with no network call, to load the signer classes. No bucket call happens at init.

## R10. Testing

- **Decision**:
  - Unit tests of `ImageService` with a fake `ObjectStorage` port.
  - `@WebMvcTest` for status codes (201, 302, 400, 401, 403, 404, 409) and exact bodies.
  - **Integration** tests with Testcontainers **MinIO** (S3-compatible) plus PostgreSQL 18. They presign, upload via real HTTP PUT, confirm, redirect, delete, and cover a type mismatch on confirm and the V2 trigger queueing a key on page delete.
  - A packaged-zip smoke test (existing).
- **Note**: MinIO accepts the default checksums that R2 rejects. The R2-specific settings (R2) are therefore verified in the production validation (quickstart), not by the integration tests.
