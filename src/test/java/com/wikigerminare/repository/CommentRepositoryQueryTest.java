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
class CommentRepositoryQueryTest {
    @Autowired
    private CommentRepository repository;

    @Test
    void filtersByBlockAndExcludesResolvedComments() {
        UUID pageId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Instant now = Instant.now();
        Comment matching = new Comment(UUID.randomUUID(), pageId, UUID.randomUUID(), blockId, "matching", now);
        Comment differentBlock = new Comment(UUID.randomUUID(), pageId, UUID.randomUUID(), UUID.randomUUID(), "different", now.plusSeconds(1));
        Comment resolved = new Comment(UUID.randomUUID(), pageId, UUID.randomUUID(), blockId, "resolved", now.plusSeconds(2));
        resolved.resolve();
        repository.save(matching);
        repository.save(differentBlock);
        repository.save(resolved);

        var page = repository.findByPageIdAndBlockIdAndParentCommentIsNullAndStatus(pageId, blockId,
                Comment.Status.OPEN, PageRequest.of(0, 20,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertEquals(1, page.getTotalElements());
        assertEquals("matching", page.getContent().getFirst().getContent());
    }
}
