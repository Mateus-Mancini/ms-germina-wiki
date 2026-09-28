package com.wikigerminare.performance;

import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.repository.comment.CommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CommentQueryPerformanceTest {
    @Autowired
    private CommentRepository repository;

    @Test
    void p95OfFirstPageQueriesIsUnderOneSecond() {
        UUID contentId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        List<Comment> comments = new ArrayList<>();
        for (int index = 0; index < 1000; index++) {
            comments.add(new Comment(UUID.randomUUID(), contentId, UUID.randomUUID(), "comment " + index,
                    "paragraph", "anchor-" + (index % 20), "1", createdAt.plusNanos(index)));
        }
        repository.saveAll(comments);
        PageRequest page = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        for (int warmup = 0; warmup < 3; warmup++) {
            repository.findByContentIdAndStatus(contentId, Comment.Status.ACTIVE, page);
        }

        long[] durations = new long[20];
        for (int index = 0; index < durations.length; index++) {
            long start = System.nanoTime();
            repository.findByContentIdAndStatus(contentId, Comment.Status.ACTIVE, page);
            durations[index] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(durations);
        long p95Milliseconds = durations[(int) Math.ceil(durations.length * 0.95) - 1] / 1_000_000;

        assertTrue(p95Milliseconds < 1000,
                () -> "Expected p95 under 1000ms but was " + p95Milliseconds + "ms");
    }
}
