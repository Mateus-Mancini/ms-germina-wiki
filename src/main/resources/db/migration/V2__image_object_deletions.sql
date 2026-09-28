-- V2: queue storage objects of deleted page images for removal (spec 005, FR-009).
-- Rows removed from page_images, directly or by the ON DELETE CASCADE from pages, queue their
-- object key; a daily job deletes the objects from storage and then the queue rows.
-- Additive only: safe for the previous API version (see docs/database-migrations.md).

CREATE TABLE image_object_deletions (
    object_key TEXT PRIMARY KEY,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION queue_image_object_deletion()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO image_object_deletions (object_key)
    VALUES (OLD.file_url)
    ON CONFLICT (object_key) DO NOTHING;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER page_images_queue_object_deletion
AFTER DELETE ON page_images
FOR EACH ROW
EXECUTE FUNCTION queue_image_object_deletion();
