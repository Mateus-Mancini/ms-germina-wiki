package com.wikigerminare.repository;

import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.repository.comment.CommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
    "spring.sql.init.schema-locations=classpath:h2-comment-types.sql",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
class CommentRepositoryTest {
    @Autowired
    private CommentRepository repository;

    @Test
    void persistsOpenAndResolvedStatesAndFiltersOpenRootComments() {
        UUID pageId = UUID.randomUUID();
        Instant now = Instant.now();
        Comment open = new Comment(UUID.randomUUID(), pageId, UUID.randomUUID(), UUID.randomUUID(), "open", now);
        Comment resolved = new Comment(UUID.randomUUID(), pageId, UUID.randomUUID(), UUID.randomUUID(), "resolved", now.plusSeconds(1));
        resolved.resolve();
        repository.save(open);
        repository.save(resolved);

        var page = repository.findByPageIdAndParentCommentIsNullAndStatus(pageId, Comment.Status.OPEN,
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertEquals(1, page.getTotalElements());
        assertEquals(open.getId(), page.getContent().getFirst().getId());
    }
}
