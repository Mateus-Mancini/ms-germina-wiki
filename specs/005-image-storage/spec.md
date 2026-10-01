# Feature Specification: Image Storage

**Feature Branch**: `005-image-storage`

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "Tasks infra-storage (image bucket with public/signed URLs) and images-api (image upload and storage integration). Students writing wiki pages must be able to add images to a page, and every reader must be able to see them. Images go directly to object storage, never through the API (6 MB request limit, cost). Storage must stay free (Cloudflare R2 was chosen over S3). Authentication comes from the separate auth-api feature."

> Numbering note: `004` is left for the folders API (PR #12), per review.

## Clarifications

### Session 2026-09-28

- Q: How should readers' browsers load images? → A: Private bucket plus an API redirect. The stable address is an API URL that answers with a redirect to a short-lived signed storage URL. Deleting an image breaks its address immediately.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A student adds an image to a page (Priority: P1)

While editing a wiki page, a signed-in student picks an image file. The image uploads, the page records it, and the student gets a stable address to place the image in the page's content.

**Why this priority**: Images are core to documenting school experiences (events, projects, labs). Without uploads, pages are text-only.

**Independent Test**: As a signed-in user, request permission to upload a valid image for an existing page, upload the file with that permission, confirm it, and see it listed for the page with a working address.

**Acceptance Scenarios**:

1. **Given** a signed-in user and an existing page, **When** they request to upload an allowed image type within the size limit, **Then** they receive a short-lived, single-purpose permission to upload exactly that file.
2. **Given** a valid upload permission, **When** the file is uploaded and the user confirms it, **Then** the image is recorded for the page with its original file name, type, size and uploader, and a stable address is returned.
3. **Given** a request for a file type that isn't allowed, or larger than the limit, **When** the user asks to upload, **Then** it's rejected before anything is stored.
4. **Given** no signed-in user, **When** someone asks to upload, **Then** it's refused as unauthenticated.
5. **Given** a page that doesn't exist, **When** someone asks to upload for it, **Then** it's refused as not found.

---

### User Story 2 - Readers see images on pages (Priority: P1)

Anyone reading a wiki page sees its images load quickly. The addresses stored in page content keep working over time.

**Why this priority**: An uploaded image nobody can see has no value, and broken images after a while would damage every page that uses them.

**Independent Test**: Open an image's stable address from a browser, with and without being signed in, now and a day later, and see the image.

**Acceptance Scenarios**:

1. **Given** an image recorded for a page, **When** a browser requests its stable address, **Then** it's redirected to a short-lived storage URL and the image is displayed.
2. **Given** the stable address saved in a page's content, **When** the page is read days later, **Then** the image still displays.
3. **Given** an address of an image that was deleted, or never existed, **When** it's requested, **Then** a not-found response is returned.

---

### User Story 3 - Managing a page's images (Priority: P2)

An editor lists a page's images and removes one that's no longer needed. The file is removed from storage too, so storage doesn't fill up with unused files.

**Why this priority**: Storage is free only up to a limit, and stale images clutter pages. Removal also covers mistakes, such as a wrong or private photo.

**Independent Test**: List a page's images, delete one, and confirm it disappears from the list and its address returns not found.

**Acceptance Scenarios**:

1. **Given** a page with images, **When** its images are listed, **Then** each shows its file name, type, size, uploader, date and stable address.
2. **Given** a recorded image, **When** its uploader or an admin deletes it, **Then** the record and the stored file are both removed.
3. **Given** a signed-in user who is neither the uploader nor an admin, **When** they try to delete the image, **Then** it's refused.

---

### Edge Cases

- **Upload never confirmed** (browser closed, or the upload failed): the stored file isn't recorded for any page and is removed automatically within a bounded time.
- **Confirmed file differs from the request** (a different type, or a larger size than approved): the confirmation is rejected and the file removed.
- **Page deleted**: its image records are removed with it (existing database rule). Their files must not stay in storage indefinitely.
- **Upload permission used after it expires**: the storage refuses it, and the user can request a new one.
- **Many images at once** (e.g. a class uploading event photos): each upload goes directly to storage, so the API isn't a bottleneck, and no request passes through the API's size limit.
- **File names**: the original name is kept for display only. Stored object names are generated, so names can't collide or be used for path tricks.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Image bytes MUST go directly between the browser and object storage; the API MUST NOT receive or serve image bytes.
- **FR-002**: Only signed-in users MUST be able to request upload permission and confirm uploads; the uploader recorded MUST be the signed-in user.
- **FR-003**: An upload permission MUST be limited to one generated object name, the declared content type and the declared size, and MUST expire within 10 minutes.
- **FR-004**: Allowed image types MUST be JPEG, PNG, WebP and GIF, with a maximum size of 5 MB per image.
- **FR-005**: The API MUST record an image for a page only after verifying the stored file exists and matches the approved type and size.
- **FR-006**: Every recorded image MUST have a stable address that keeps working for as long as the image exists, suitable for embedding in page content. The address is an API URL that redirects the browser to a short-lived signed storage URL (valid at most 10 minutes). The storage bucket itself MUST NOT be publicly readable.
- **FR-007**: The API MUST list a page's images and MUST allow the uploader or an admin to delete one, removing both the record and the stored file.
- **FR-008**: Stored files not confirmed within 24 hours MUST be removed automatically.
- **FR-009**: Files of images whose records were removed by a page deletion MUST NOT remain in storage indefinitely.
- **FR-010**: Storage MUST stay within the provider's permanent free limits at the expected load, and MUST be configured as code or documented commands.
- **FR-011**: Storage credentials MUST be scoped to this project's bucket only, and kept outside the repository like other production secrets.
- **FR-012**: Browsers MUST be allowed to upload to storage only from the official web app origin and `http://localhost:3000`.
- **FR-013**: The feature MUST NOT implement authentication, users, pages or permissions beyond what's listed here; it relies on the auth, pages and RBAC features for identity, page existence and the admin role.

### Key Entities

- **Page image** (existing table `page_images`): belongs to a page; original file name, content type, size, uploader, creation time, and the stable address/storage reference.
- **Upload permission**: short-lived, single-object, type- and size-bound authorisation to write one file to storage.
- **Stored object**: the image file in the bucket, named by a generated identifier.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A signed-in student can add a 3 MB photo to a page in under 10 seconds on a typical school connection, from choosing the file to having its address.
- **SC-002**: 100% of disallowed types and oversize files are rejected before any file is stored.
- **SC-003**: Image addresses embedded in page content still display after 30 days.
- **SC-004**: Unconfirmed uploads are gone from storage within 48 hours in 100% of cases.
- **SC-005**: Storage adds USD 0.00 to the monthly bill at the expected load (≤ 15 users, ≤ 2 GB of images).
- **SC-006**: No image byte passes through the API (verifiable from the API's request logs and payload sizes).
- **SC-007**: When the API is warm, an image address resolves to a displayable image in under 1 second. After idle, the first image resolves within the API's cold-start target (under 3 seconds).

## Assumptions

- **Identity:** the auth-api feature (Lucca) provides the signed-in user as the request principal, with the user's UUID as its name. Until it lands, upload and confirm answer "unauthenticated" and can't be used in production.
- **Admin role:** comes from the RBAC feature. Until then, only the uploader can delete.
- **Pages:** pages are created by the pages-api feature (Camilla). This feature only checks that a page exists.
- **Storage provider:** Cloudflare R2 (10 GB, 1M write and 10M read operations per month free, no egress fees), accessed through its S3-compatible API.
- **Formats:** image processing (resizing, thumbnails, EXIF stripping) is out of scope for this version.
- **Load:** a few dozen images per week.
