package com.wikigerminare.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentsMigrationTest {
    @Test
    void migrationDeclaresCommentTablesAndIndexes() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("db/migration/V1__initial_schema.sql")) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("CREATE TABLE comments"));
            assertTrue(sql.contains("page_id UUID NOT NULL"));
            assertTrue(sql.contains("parent_comment_id UUID"));
            assertTrue(sql.contains("block_id UUID NOT NULL"));
            assertTrue(sql.contains("comment_status"));
        }
    }
}
