# Data Model: Image Storage

**Feature**: `005-image-storage` | **Date**: 2026-09-28

## PageImage (existing table `page_images`, from V1)

| Column | Use in this feature | Rules |
|---|---|---|
| `id` UUID PK | The stable address is `/api/images/{id}` | generated |
| `page_id` UUID FK → `pages` ON DELETE CASCADE | Owning page | the page must exist at upload and at confirm |
| `file_name` VARCHAR(255) | The original name, **display only** | 1–255 chars after trimming; never used as a storage key |
| `file_url` TEXT | The **object key** `images/{uuid}` | never a signed URL |
| `mime_type` VARCHAR(100) | One of `image/jpeg`, `image/png`, `image/webp`, `image/gif` | must equal the approved type and R2's `HEAD` result |
| `file_size` BIGINT | Bytes | 1 … 5,242,880; must equal the approved size and R2's `HEAD` result |
| `uploaded_by` UUID FK → `users` | The signed-in user | from the principal, never from the client |
| `created_at` TIMESTAMPTZ | Response field | DB default |

## ImageObjectDeletion (new, migration V2)

| Column | Type | Rules |
|---|---|---|
| `object_key` | TEXT PK | the key of a removed `page_images` row |
| `queued_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | |

A trigger `AFTER DELETE ON page_images FOR EACH ROW` inserts `OLD.file_url` (`ON CONFLICT DO NOTHING`). It fires for direct deletes and for cascades from `pages`. The daily cleanup deletes each queued object from R2 (deleting a missing object is a no-op), then deletes the queue row.

## Object keys (bucket `germinawiki-images`, private)

| Prefix | Lifecycle | Written by |
|---|---|---|
| `pending/{pageId}/{userId}/{uuid}` | expires after 1 day (R2 lifecycle rule) | the browser, via presigned PUT |
| `images/{uuid}` | kept until the image is deleted | the API (`CopyObject` on confirm) |

## Upload state

```
requested ──(browser PUT within 10 min)──> pending object ──(confirm: prefix + HEAD type/size OK)──> images/{uuid} + page_images row
    │                                            │
    └─ expired permission: R2 rejects the PUT     └─ never confirmed: removed by the lifecycle rule after 1 day
                                                    confirm mismatch: pending object deleted, 409
```
