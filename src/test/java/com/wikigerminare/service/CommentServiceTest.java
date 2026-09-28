package com.wikigerminare.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wikigerminare.dto.comment.AnchorRequest;
import com.wikigerminare.dto.comment.CreateCommentRequest;
import com.wikigerminare.dto.comment.UpdateCommentRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.repository.comment.AdminReplyRepository;
import com.wikigerminare.repository.comment.CommentRepository;
import com.wikigerminare.support.CommentTestFixtures;

class CommentServiceTest {
    private CommentRepository commentRepository;
    private AuthenticatedUserProvider userProvider;
    private ContentAnchorValidator contentValidator;
    private CommentService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        AdminReplyRepository replyRepository = mock(AdminReplyRepository.class);
        userProvider = mock(AuthenticatedUserProvider.class);
        contentValidator = CommentTestFixtures.publishedContentValidator();
        service = new CommentService(commentRepository, replyRepository, userProvider, contentValidator);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsCommentWithNormalizedTextAndValidatedAnchor() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());

        Comment comment = service.create(new CreateCommentRequest(CommentTestFixtures.CONTENT_ID,
                new AnchorRequest("paragraph", "intro", "1"), "  A dúvida  "));

        assertEquals("A dúvida", comment.getText());
        assertEquals("intro", comment.getAnchorValue());
        assertEquals(Comment.Status.ACTIVE, comment.getStatus());
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void rejectsInvalidAnchorBeforePersisting() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());

        assertThrows(CommentException.class, () -> service.create(new CreateCommentRequest(
                CommentTestFixtures.CONTENT_ID, new AnchorRequest("", "", "1"), "text")));

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void rejectsTextLongerThanMaximum() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());

        String text = "x".repeat(CommentTextPolicy.MAX_TEXT_LENGTH + 1);

        assertThrows(CommentException.class, () -> service.create(new CreateCommentRequest(
                CommentTestFixtures.CONTENT_ID, new AnchorRequest("paragraph", "intro", "1"), text)));
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void onlyAuthorCanEditComment() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        Comment comment = service.create(new CreateCommentRequest(CommentTestFixtures.CONTENT_ID,
                new AnchorRequest("paragraph", "intro", "1"), "original"));
        when(commentRepository.findByIdAndStatus(comment.getId(), Comment.Status.ACTIVE)).thenReturn(Optional.of(comment));
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.otherUser());

        CommentException exception = assertThrows(CommentException.class,
                () -> service.update(comment.getId(), new UpdateCommentRequest("changed")));

        assertEquals("FORBIDDEN", exception.code());
        assertEquals("original", comment.getText());
    }

    @Test
    void removesCommentLogicallyAndIsIdempotent() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        Comment comment = new Comment(java.util.UUID.randomUUID(), CommentTestFixtures.CONTENT_ID,
                CommentTestFixtures.AUTHOR_ID, "text", "paragraph", "intro", "1", java.time.Instant.now());
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        service.remove(comment.getId());
        service.remove(comment.getId());

        assertEquals(Comment.Status.REMOVED, comment.getStatus());
        verify(commentRepository).save(comment);
    }
}
