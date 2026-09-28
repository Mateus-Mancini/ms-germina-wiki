package com.wikigerminare.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentsMigrationTest {
    @Test
    void migrationDeclaresCommentTablesAndIndexes() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("db/migration/V4__create_comments.sql")) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE comments"));
            assertTrue(sql.contains("CREATE TABLE admin_replies"));
            assertTrue(sql.contains("comments_content_anchor_status_created_idx"));
        }
    }
}
