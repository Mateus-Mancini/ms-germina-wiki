package com.wikigerminare.service;

import com.wikigerminare.dto.comment.AnchorRequest;
import com.wikigerminare.dto.comment.CreateCommentRequest;
import com.wikigerminare.dto.comment.UpdateCommentRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.repository.comment.CommentRepository;
import com.wikigerminare.support.CommentTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {
    private CommentRepository repository;
    private AuthenticatedUserProvider userProvider;
    private CommentService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommentRepository.class);
        userProvider = mock(AuthenticatedUserProvider.class);
        service = new CommentService(repository, userProvider, CommentTestFixtures.publishedContentValidator());
        when(repository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsCommentWithPageAndBlock() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());

        Comment comment = service.create(new CreateCommentRequest(CommentTestFixtures.PAGE_ID,
                new AnchorRequest(CommentTestFixtures.BLOCK_ID), "  dúvida  "));

        assertEquals(CommentTestFixtures.PAGE_ID, comment.getPageId());
        assertEquals(CommentTestFixtures.BLOCK_ID, comment.getBlockId());
        assertEquals("dúvida", comment.getContent());
        assertEquals(Comment.Status.OPEN, comment.getStatus());
    }

    @Test
    void rejectsInvalidBlockBeforePersisting() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        assertThrows(CommentException.class, () -> service.create(new CreateCommentRequest(
                CommentTestFixtures.PAGE_ID, new AnchorRequest(null), "text")));
        verify(repository, never()).save(any(Comment.class));
    }

    @Test
    void onlyAuthorCanEditComment() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        Comment comment = service.create(new CreateCommentRequest(CommentTestFixtures.PAGE_ID,
                new AnchorRequest(CommentTestFixtures.BLOCK_ID), "original"));
        when(repository.findByIdAndStatus(comment.getId(), Comment.Status.OPEN)).thenReturn(Optional.of(comment));
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.otherUser());

        CommentException exception = assertThrows(CommentException.class,
                () -> service.update(comment.getId(), new UpdateCommentRequest("changed")));

        assertEquals("FORBIDDEN", exception.code());
        assertEquals("original", comment.getContent());
    }

    @Test
    void resolvesCommentIdempotently() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        Comment comment = new Comment(java.util.UUID.randomUUID(), CommentTestFixtures.PAGE_ID,
                CommentTestFixtures.AUTHOR_ID, CommentTestFixtures.BLOCK_ID, "text", java.time.Instant.now());
        when(repository.findById(comment.getId())).thenReturn(Optional.of(comment));

        service.remove(comment.getId());
        service.remove(comment.getId());

        assertEquals(Comment.Status.RESOLVED, comment.getStatus());
        verify(repository).save(comment);
    }

    @Test
    void rejectsTextLongerThanMaximum() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        String text = "x".repeat(CommentTextPolicy.MAX_TEXT_LENGTH + 1);
        assertThrows(CommentException.class, () -> service.create(new CreateCommentRequest(
                CommentTestFixtures.PAGE_ID, new AnchorRequest(CommentTestFixtures.BLOCK_ID), text)));
    }
}
