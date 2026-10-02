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
        // Pages currently have no publication/privacy states. Check existence without
        // loading the entire document a second time during anchor validation.
        Boolean exists = jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM pages WHERE id = ?)",
                Boolean.class, pageId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new CommentException("CONTENT_NOT_FOUND", "Page not found");
        }
    }

    @Override
    public ValidatedAnchor validateAnchor(UUID pageId, AnchorInput anchorInput) {
        if (anchorInput == null || anchorInput.blockId() == null) {
            throw new CommentException("ANCHOR_INVALID", "blockId is required");
        }
        String content = pageContent(pageId);
        String marker = "<!--b:" + anchorInput.blockId() + "-->";
        if (!content.contains(marker)) {
            throw new CommentException("ANCHOR_INVALID", "Block does not belong to page");
        }
        return new ValidatedAnchor(anchorInput.blockId());
    }

    private String pageContent(UUID pageId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT content FROM pages WHERE id = ?", String.class,
                    pageId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new CommentException("CONTENT_NOT_FOUND", "Page not found");
        }
    }

}
