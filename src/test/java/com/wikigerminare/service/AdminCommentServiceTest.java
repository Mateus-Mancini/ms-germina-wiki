package com.wikigerminare.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wikigerminare.dto.comment.CreateAdminReplyRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.repository.comment.AdminReplyRepository;
import com.wikigerminare.repository.comment.CommentRepository;
import com.wikigerminare.support.CommentTestFixtures;

class AdminCommentServiceTest {
    private CommentRepository commentRepository;
    private AdminReplyRepository replyRepository;
    private AuthenticatedUserProvider userProvider;
    private CommentService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        replyRepository = mock(AdminReplyRepository.class);
        userProvider = mock(AuthenticatedUserProvider.class);
        ContentAnchorValidator validator = CommentTestFixtures.publishedContentValidator();
        service = new CommentService(commentRepository, replyRepository, userProvider, validator);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(replyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void adminCanReplyToActiveComment() {
        Comment comment = activeComment();
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.admin());
        when(commentRepository.findByIdAndStatus(comment.getId(), Comment.Status.ACTIVE)).thenReturn(Optional.of(comment));

        var reply = service.reply(comment.getId(), new CreateAdminReplyRequest("  resposta  "));

        assertEquals("resposta", reply.getText());
        assertEquals(CommentTestFixtures.ADMIN_ID, reply.getAdminId());
        verify(replyRepository).save(reply);
    }

    @Test
    void regularUserCannotReplyAsAdmin() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());

        CommentException exception = assertThrows(CommentException.class,
                () -> service.reply(UUID.randomUUID(), new CreateAdminReplyRequest("reply")));

        assertEquals("FORBIDDEN", exception.code());
    }

    @Test
    void adminCanRemoveAnotherUsersComment() {
        Comment comment = activeComment();
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.admin());
        when(commentRepository.findById(comment.getId())).thenReturn(Optional.of(comment));

        service.remove(comment.getId());

        assertEquals(Comment.Status.REMOVED, comment.getStatus());
        verify(commentRepository).save(comment);
    }

    private Comment activeComment() {
        return new Comment(UUID.randomUUID(), CommentTestFixtures.CONTENT_ID, CommentTestFixtures.AUTHOR_ID,
                "question", "paragraph", "intro", "1", Instant.now());
    }
}
