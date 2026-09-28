package com.wikigerminare.repository;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.repository.comment.CommentRepository;

@DataJpaTest
class CommentRepositoryTest {
    @Autowired
    private CommentRepository repository;

    @Test
    void persistsActiveAndRemovedStatesAndFiltersActiveComments() {
        UUID contentId = UUID.randomUUID();
        Instant now = Instant.now();
        Comment active = new Comment(UUID.randomUUID(), contentId, UUID.randomUUID(), "active",
                "paragraph", "one", "1", now);
        Comment removed = new Comment(UUID.randomUUID(), contentId, UUID.randomUUID(), "removed",
                "paragraph", "two", "1", now.plusSeconds(1));
        removed.remove();
        repository.save(active);
        repository.save(removed);

        var page = repository.findByContentIdAndStatus(contentId, Comment.Status.ACTIVE,
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertEquals(1, page.getTotalElements());
        assertEquals(active.getId(), page.getContent().getFirst().getId());
    }
}
