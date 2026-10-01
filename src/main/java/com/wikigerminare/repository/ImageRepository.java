package com.wikigerminare.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Persistence for page images (table page_images, V1) and the storage deletion queue
 * (image_object_deletions, V2).
 */
@Repository
public class ImageRepository {

	private static final RowMapper<PageImage> PAGE_IMAGE = ImageRepository::mapPageImage;

	private final JdbcTemplate jdbc;

	public ImageRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean pageExists(UUID pageId) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pages WHERE id = ?)", Boolean.class, pageId));
	}

	public PageImage insert(UUID pageId, String fileName, String objectKey, String contentType, long size,
			UUID uploadedBy) {
		return jdbc.queryForObject("""
				INSERT INTO page_images (page_id, file_name, file_url, mime_type, file_size, uploaded_by)
				VALUES (?, ?, ?, ?, ?, ?)
				RETURNING id, page_id, file_name, file_url, mime_type, file_size, uploaded_by, created_at
				""", PAGE_IMAGE, pageId, fileName, objectKey, contentType, size, uploadedBy);
	}

	public Optional<PageImage> findById(UUID id) {
		return jdbc.query("""
				SELECT id, page_id, file_name, file_url, mime_type, file_size, uploaded_by, created_at
				FROM page_images WHERE id = ?
				""", PAGE_IMAGE, id).stream().findFirst();
	}

	public List<PageImage> findByPage(UUID pageId) {
		return jdbc.query("""
				SELECT id, page_id, file_name, file_url, mime_type, file_size, uploaded_by, created_at
				FROM page_images WHERE page_id = ? ORDER BY created_at DESC, id
				""", PAGE_IMAGE, pageId);
	}

	/**
	 * Deletes the row; the V2 trigger queues its object key for storage cleanup.
	 */
	public void delete(UUID id) {
		jdbc.update("DELETE FROM page_images WHERE id = ?", id);
	}

	public List<String> queuedObjectKeys(int limit) {
		return jdbc.queryForList("SELECT object_key FROM image_object_deletions ORDER BY queued_at LIMIT ?",
				String.class, limit);
	}

	public void dequeue(String objectKey) {
		jdbc.update("DELETE FROM image_object_deletions WHERE object_key = ?", objectKey);
	}

	private static PageImage mapPageImage(ResultSet rs, int rowNum) throws SQLException {
		return new PageImage(rs.getObject("id", UUID.class), rs.getObject("page_id", UUID.class),
				rs.getString("file_name"), rs.getString("file_url"), rs.getString("mime_type"),
				rs.getLong("file_size"), rs.getObject("uploaded_by", UUID.class),
				rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
	}

	/**
	 * A row of page_images; {@code objectKey} is the file_url column (never a signed URL).
	 */
	public record PageImage(UUID id, UUID pageId, String fileName, String objectKey, String contentType, long size,
			UUID uploadedBy, Instant createdAt) {
	}

}
