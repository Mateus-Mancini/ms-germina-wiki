package com.wikigerminare.service;

import com.wikigerminare.dto.comment.CreateAdminReplyRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.repository.comment.CommentRepository;
import com.wikigerminare.support.CommentTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCommentServiceTest {
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
    void adminReplyIsPersistedAsChildComment() {
        Comment parent = activeComment();
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.admin());
        when(repository.findByIdAndStatus(parent.getId(), Comment.Status.OPEN)).thenReturn(Optional.of(parent));

        var reply = service.reply(parent.getId(), new CreateAdminReplyRequest("resposta"));

        assertEquals(parent.getId(), reply.commentId());
        assertEquals(CommentTestFixtures.ADMIN_ID, reply.adminId());
        var saved = org.mockito.ArgumentCaptor.forClass(Comment.class);
        verify(repository).save(saved.capture());
        assertEquals(reply.id(), saved.getValue().getId());
        assertEquals(parent.getId(), saved.getValue().getParentComment().getId());
    }

    @Test
    void regularUserCannotReplyAsAdmin() {
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.author());
        CommentException exception = assertThrows(CommentException.class,
                () -> service.reply(UUID.randomUUID(), new CreateAdminReplyRequest("reply")));
        assertEquals("FORBIDDEN", exception.code());
    }

    @Test
    void adminCanResolveAnotherUsersComment() {
        Comment comment = activeComment();
        when(userProvider.currentUser()).thenReturn(CommentTestFixtures.admin());
        when(repository.findById(comment.getId())).thenReturn(Optional.of(comment));

        service.remove(comment.getId());

        assertEquals(Comment.Status.RESOLVED, comment.getStatus());
        verify(repository).save(comment);
    }

    private Comment activeComment() {
        return new Comment(UUID.randomUUID(), CommentTestFixtures.PAGE_ID, CommentTestFixtures.AUTHOR_ID,
                CommentTestFixtures.BLOCK_ID, "question", Instant.now());
    }
}
