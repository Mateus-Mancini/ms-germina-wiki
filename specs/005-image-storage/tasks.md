---

description: "Task list for 005-image-storage"
---

# Tasks: Image Storage

**Input**: Design documents from `/specs/005-image-storage/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/images.openapi.yaml, quickstart.md

**Tests**: Included (constitution IV). Tests first, and they must fail before implementation.

**Organization**: by user story. Paths are relative to the repository root.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 Add to `pom.xml`: the AWS SDK v2 BOM, `s3` and `url-connection-client` (excluding the Apache and Netty clients), `spring-boot-starter-validation`; test-scoped Testcontainers MinIO (research R1, R2, R10)
- [X] T002 [P] Create `src/main/resources/db/migration/V2__image_object_deletions.sql`: table `image_object_deletions(object_key TEXT PRIMARY KEY, queued_at TIMESTAMPTZ NOT NULL DEFAULT now())` and an `AFTER DELETE ON page_images` trigger inserting `OLD.file_url` `ON CONFLICT DO NOTHING` (data-model.md)
- [X] T003 [P] Create `infra/r2/cors.json`: PUT, GET and HEAD from `https://germinawiki.web.app` and `http://localhost:3000`, allowed header `Content-Type`, max age 3600 (research R8)

---

## Phase 2: Foundational

- [X] T004 Create `config/StorageProperties.java` (`@ConfigurationProperties("app.storage")`: accountId, bucket, accessKeyId, secretAccessKey, optional endpoint override for tests) and `config/StorageConfig.java` building the singleton `S3Client` and `S3Presigner` with `requestChecksumCalculation(WHEN_REQUIRED)`, `responseChecksumValidation(WHEN_REQUIRED)`, `chunkedEncodingEnabled(false)`, path-style access, region `auto`, and `UrlConnectionHttpClient` (research R2, R9)
- [X] T005 Create the port `storage/ObjectStorage.java` (`presignPut(key, contentType, size, ttl)`, `presignGet(key, ttl)`, `head(key)` → `Optional<ObjectInfo>`, `copy(from, to)`, `delete(key)`) and its adapter `storage/R2ObjectStorage.java`
- [X] T006 Create `src/test/java/com/wikigerminare/StorageTestcontainersConfiguration.java`: a MinIO container, bucket creation, and `app.storage.*` pointing at it via a dynamic property registry

**Checkpoint**: the app starts with storage configured (tests use MinIO); `./mvnw test` is green

---

## Phase 3: User Story 1 - A student adds an image to a page (Priority: P1) 🎯 MVP

**Goal**: request permission → direct PUT → confirm → recorded image with a stable address

### Tests (write first, must fail)

- [X] T007 [P] [US1] `service/ImageServiceTest.java` (fake storage + mocked repository):
  - request → rejects disallowed types and sizes outside 1–5,242,880 (400), a missing page (404) and no user (401); returns the key `pending/{pageId}/{userId}/{uuid}` and a TTL of 10 min
  - confirm → foreign key prefix (403), missing object (404), HEAD type/size mismatch (409 + the object deleted), success (copy to `images/{uuid}`, delete pending, insert the row with the trimmed file name)
- [X] T008 [P] [US1] `controller/ImageControllerTest.java` (`@WebMvcTest`): `POST …/uploads` 201/400/401/404 bodies; `POST …/images` 201/400/401/403/404/409; request validation (`fileName` 1–255, `contentType` enum, `size` range)
- [X] T009 [P] [US1] `ImageFlowIntegrationTest.java` (PostgreSQL + MinIO, a seeded user and page): presign → real HTTP PUT with the returned headers → confirm → row present; a PUT with a different Content-Type is rejected by storage; a confirm after uploading a different size → 409

### Implementation

- [X] T010 [P] [US1] DTO records `dto/UploadRequest.java`, `dto/UploadPermission.java`, `dto/ConfirmRequest.java`, `dto/ImageResponse.java` with Bean Validation per the contract
- [X] T011 [P] [US1] `repository/ImageRepository.java` (`JdbcTemplate`): `pageExists`, `insert`, `findById`, `findByPage` (newest first), `delete`, plus the deletion-queue methods
- [X] T012 [US1] Service exceptions in `service/exception/` and `service/ImageService.java`: `requestUpload`, `confirmUpload` (research R3, R6)
- [X] T013 [US1] `controller/ImageController.java` (`POST /api/pages/{pageId}/images/uploads`, `POST /api/pages/{pageId}/images`) and `controller/ApiExceptionHandler.java` scoped to `ImageController`, mapping the exceptions to `{"error"}` bodies

**Checkpoint**: MVP. Uploads work end to end against MinIO

---

## Phase 4: User Story 2 - Readers see images on pages (Priority: P1)

- [ ] T014 [P] [US2] Tests: service `imageRedirect` (404 for an unknown id, a presigned GET with a 10-min TTL); MVC `GET /api/images/{id}` → 302 with `Location` and `Cache-Control: private, max-age=300`, no auth required; integration: follow the redirect and download identical bytes
- [ ] T015 [US2] Implement `ImageService.imageRedirect` and `GET /api/images/{id}` (research R4)
- [ ] T016 [US2] Extend `lambda/SnapStartPriming.java` to presign one dummy GET (no network) (research R9)

---

## Phase 5: User Story 3 - Managing a page's images (Priority: P2)

- [ ] T017 [P] [US3] Tests:
  - service list (404 for a missing page) and delete (uploader 204; another user 403; unknown 404; storage object deleted)
  - MVC `GET /api/pages/{pageId}/images` and `DELETE /api/images/{id}`
  - integration: delete removes the row and the object; deleting a **page** queues its image keys (V2 trigger); the cleanup deletes the queued objects and the queue rows
- [ ] T018 [US3] Implement `ImageService.list` and `delete` (`canDelete`: the uploader; the admin hook is noted for RBAC) and their endpoints
- [ ] T019 [US3] Implement `service/ImageCleanupService.java` (process the queue: delete the object, then the row; idempotent) and route the payload `{"source":"germinawiki.image-cleanup"}` in `lambda/StreamLambdaHandler.java` to it, returning `{"deleted":N}` (research R5)

---

## Phase 6: Infrastructure & Deploy

- [ ] T020 `template.yaml`:
  - parameters `R2AccountId`, `R2Bucket`, `R2AccessKeyId` (NoEcho), `R2SecretAccessKey` (NoEcho) → `APP_STORAGE_*` env vars
  - a daily `ScheduleV2` on `ApiFunction` with input `{"source":"germinawiki.image-cleanup"}` and the permissions boundary
- [ ] T021 `.github/workflows/release.yml` (pass the four R2 parameters from the `production` environment), and add the R2 values to `docs/ci-cd.md` §2
- [ ] T022 [P] `src/test/java/com/wikigerminare/R2LiveSmokeTest.java`, `@EnabledIfEnvironmentVariable(APP_STORAGE_ACCESS_KEY_ID)`: the full round trip against the configured bucket (quickstart §3)
- [ ] T023 [P] `docs/image-storage.md`: owner setup (Cloudflare account, `npx wrangler login`, bucket create, CORS from `infra/r2/cors.json`, lifecycle `pending/` 1 day, bucket-scoped R2 API token), secrets for `prod.env` and GitHub, operations (cleanup invocation, costs)
- [ ] T024 Owner: R2 setup per `docs/image-storage.md`; run `R2LiveSmokeTest` locally against R2
- [ ] T025 After merge: the release applies V2 and deploys; validate quickstart §3–§5 (401 before auth, 404 for an unknown image, cleanup `{"deleted":0}`, USD 0)

---

## Phase 7: Polish

- [ ] T026 [P] README: feature 005 row, storage section link; `docs/deployment.md`: the R2 parameters in the manual deploy command

## Dependencies

- Setup → Foundational → US1 → US2/US3 (US2 and US3 need recorded images) → Infra → Polish
- T024 (owner R2 setup) blocks T022's real run and T025; everything else runs on MinIO
