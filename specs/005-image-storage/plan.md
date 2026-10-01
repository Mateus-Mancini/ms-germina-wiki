# Implementation Plan: Image Storage

**Branch**: `005-image-storage` | **Date**: 2026-09-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-image-storage/spec.md`

## Summary

Images live in a **private Cloudflare R2 bucket**:
- **Upload**: browsers upload directly with **presigned PUTs** bound to type and size. The API verifies each object (`HEAD`), moves it from `pending/` to `images/`, and records it in `page_images`.
- **Read**: a stable address `GET /api/images/{id}` **302-redirects** to a 10-minute presigned GET.
- **Cleanup**: unconfirmed uploads expire through an R2 lifecycle rule. Files of deleted images are queued by a V2 migration trigger and removed by a **daily cleanup** that EventBridge Scheduler runs directly inside the API Lambda.
- **Implementation**: AWS SDK v2 with R2's checksum and chunked-encoding settings. Identity comes from the request principal (401 until auth-api lands).

## Technical Context

**Language/Version**: Java 21, SQL (Flyway V2)

**Primary Dependencies**:
- Spring Boot 4.1 (webmvc, jdbc, validation)
- AWS SDK for Java v2 `s3` + `url-connection-client` (BOM-managed)
- Tests: Testcontainers MinIO + PostgreSQL

**Storage**: Neon PostgreSQL (`page_images`, plus the new `image_object_deletions`); Cloudflare R2 bucket `germinawiki-images` (private)

**Testing**: JUnit 5, Mockito, `@WebMvcTest`, Testcontainers (PostgreSQL 18 + MinIO); `R2LiveSmokeTest` gated on env vars

**Target Platform**: the existing API Lambda (`sa-east-1`); R2 (Cloudflare edge)

**Project Type**: Web service (backend repo)

**Performance Goals**: warm redirect < 1 s; 3 MB upload flow < 10 s (SC-001, SC-007)

**Constraints**:
- USD 0
- no image bytes through the API
- presigned URLs ≤ 10 min
- JPEG/PNG/WebP/GIF ≤ 5 MB
- no public bucket

**Scale/Scope**: 5 endpoints, 1 migration, 1 scheduled job; tens of images per week

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| I. Spec as source of truth | ✅ | 13 FRs + 1 clarification. Auth, pages and RBAC are explicitly out of scope (FR-013). |
| II. Layered architecture | ✅ | `ImageController` (HTTP only) → `ImageService` (all rules: types, sizes, ownership, verification, delete permission) → `ImageRepository` (JDBC). The storage adapter sits behind an `ObjectStorage` port used by the service, like any other repository dependency. |
| III. REST contracts and DTOs | ✅ | `contracts/images.openapi.yaml`; request/response records with Bean Validation; coherent codes (201/204/302/400/401/403/404/409). |
| IV. Automated tests | ✅ | Unit (service, with a fake storage), MVC (every status code), integration (Postgres + MinIO, full flow + trigger), live R2 smoke (owner). |
| V. Simplicity | ✅ | Plain JDBC (one table, no entity graph needed); the official S3 SDK; no new infrastructure beyond one schedule. |
| Technical constraints (Java 21, Spring Boot, PostgreSQL, REST, DTOs) | ✅ | |
| Migrations stay backward-compatible | ✅ | V2 only adds a table and a trigger. |

**Post-design re-check**: ✅.

## Project Structure

### Documentation

```text
specs/005-image-storage/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/images.openapi.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code

```text
pom.xml                                              # + awssdk bom, s3, url-connection-client, spring-boot-starter-validation; test: testcontainers-minio
src/main/resources/db/migration/V2__image_object_deletions.sql
src/main/java/com/wikigerminare/
├── controller/ImageController.java
├── controller/ImageExceptionHandler.java            # {"error"} mapping for image exceptions, scoped to ImageController
├── service/ImageService.java
├── service/ImageCleanupService.java
├── service/exception/{ImageNotFound,UploadRejected,UploadMismatch,Forbidden,Unauthenticated}Exception.java
├── repository/ImageRepository.java                  # page_images + pageExists + deletion queue
├── dto/{UploadRequest,UploadPermission,ConfirmRequest,ImageResponse}.java
├── storage/ObjectStorage.java                       # port: presignPut, presignGet, head, copy, delete
├── storage/R2ObjectStorage.java                     # AWS SDK v2 adapter with R2 settings
├── config/StorageProperties.java + StorageConfig.java
└── lambda/StreamLambdaHandler.java                  # route {"source":"germinawiki.image-cleanup"} to the cleanup
src/test/java/com/wikigerminare/...                  # ImageServiceTest, ImageControllerTest, ImageFlowIntegrationTest, R2LiveSmokeTest
template.yaml                                        # R2 parameters/env, daily ScheduleV2 on the API alias
.github/workflows/release.yml                        # pass the R2 parameters from the environment
infra/r2/cors.json
docs/image-storage.md                                # owner setup (Wrangler, token), operations
```

**Structure Decision**: this follows the repository's layer packages. `storage/` holds the outbound adapter behind a port, so the service is unit-testable, and switching to S3 would be a configuration change.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Non-HTTP event routed inside `StreamLambdaHandler` | The daily cleanup needs DB + R2 access, which only the API function has; invoking the alias directly keeps it off the public Function URL | A separate Python function would need a Postgres driver layer and duplicate the R2 settings; an internal HTTP endpoint would be publicly reachable |
