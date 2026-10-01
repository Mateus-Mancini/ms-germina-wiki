package com.wikigerminare.config;

import com.wikigerminare.integration.AnchorInput;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.integration.ValidatedAnchor;
import com.wikigerminare.service.CommentException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DatabaseContentAnchorValidator implements ContentAnchorValidator {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseContentAnchorValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void assertPublishedContent(UUID pageId) {
        page(pageId);
    }

    @Override
    public ValidatedAnchor validateAnchor(UUID pageId, AnchorInput anchorInput) {
        if (anchorInput == null || anchorInput.blockId() == null) {
            throw new CommentException("ANCHOR_INVALID", "blockId is required");
        }
        PageRecord page = page(pageId);
        String marker = "<!--b:" + anchorInput.blockId() + "-->";
        if (!page.content().contains(marker)) {
            throw new CommentException("ANCHOR_INVALID", "Block does not belong to page");
        }
        return new ValidatedAnchor(anchorInput.blockId());
    }

    private PageRecord page(UUID pageId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT content, version FROM pages WHERE id = ?",
                    (resultSet, rowNum) -> new PageRecord(resultSet.getString("content"), resultSet.getInt("version")),
                    pageId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CommentException("CONTENT_NOT_FOUND", "Page not found");
        }
    }

    private record PageRecord(String content, int version) {
    }
}
