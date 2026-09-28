package com.wikigerminare.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wikigerminare.dto.comment.AnchorRequest;
import com.wikigerminare.dto.comment.CreateAdminReplyRequest;
import com.wikigerminare.dto.comment.CreateCommentRequest;
import com.wikigerminare.dto.comment.UpdateCommentRequest;
import com.wikigerminare.entity.comment.AdminReply;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AnchorInput;
import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.integration.ValidatedAnchor;
import com.wikigerminare.repository.comment.AdminReplyRepository;
import com.wikigerminare.repository.comment.CommentRepository;

@Service
@Transactional
public class CommentService {
    private static final Sort COMMENT_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CommentRepository commentRepository;
    private final AdminReplyRepository adminReplyRepository;
    private final com.wikigerminare.integration.AuthenticatedUserProvider userProvider;
    private final ContentAnchorValidator contentValidator;

    public CommentService(CommentRepository commentRepository,
                           AdminReplyRepository adminReplyRepository,
                           com.wikigerminare.integration.AuthenticatedUserProvider userProvider,
                           ContentAnchorValidator contentValidator) {
        this.commentRepository = commentRepository;
        this.adminReplyRepository = adminReplyRepository;
        this.userProvider = userProvider;
        this.contentValidator = contentValidator;
    }

    public Comment create(CreateCommentRequest request) {
        AuthenticatedUser user = currentUser();
        if (request == null || request.contentId() == null || request.anchor() == null) {
            throw validation("contentId and anchor are required");
        }
        String text = normalizeText(request.text());
        ValidatedAnchor anchor = validateAnchor(request.contentId(), request.anchor());
        Instant now = Instant.now();
        Comment comment = new Comment(UUID.randomUUID(), request.contentId(), user.id(), text,
                anchor.type(), anchor.value(), anchor.revision(), now);
        return commentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public Comment get(UUID commentId) {
        return commentRepository.findByIdAndStatus(commentId, Comment.Status.ACTIVE)
                .orElseThrow(() -> new CommentException("COMMENT_NOT_FOUND", "Comment not found"));
    }

    public Comment update(UUID commentId, UpdateCommentRequest request) {
        AuthenticatedUser user = currentUser();
        Comment comment = get(commentId);
        requireOwner(comment, user);
        String text = normalizeText(request == null ? null : request.text());
        ValidatedAnchor anchor = validateAnchor(comment.getContentId(),
                new AnchorRequest(comment.getAnchorType(), comment.getAnchorValue(), comment.getContentRevision()));
        if (!anchor.type().equals(comment.getAnchorType()) || !anchor.value().equals(comment.getAnchorValue())) {
            throw new CommentException("ANCHOR_INVALID", "Comment anchor is no longer valid");
        }
        comment.updateText(text, Instant.now());
        return commentRepository.save(comment);
    }

    public void remove(UUID commentId) {
        AuthenticatedUser user = currentUser();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentException("COMMENT_NOT_FOUND", "Comment not found"));
        requireOwnerOrAdmin(comment, user);
        if (comment.getStatus() == Comment.Status.ACTIVE) {
            comment.remove();
            commentRepository.save(comment);
        }
    }

    @Transactional(readOnly = true)
    public Page<Comment> list(UUID contentId, String anchorType, String anchorValue, int page, int size) {
        if (contentId == null) {
            throw validation("contentId is required");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw validation("page must be >= 0 and size must be between 1 and 100");
        }
        if ((anchorType == null) != (anchorValue == null)) {
            throw validation("anchorType and anchorValue must be provided together");
        }
        contentValidator.assertPublishedContent(contentId);
        PageRequest request = PageRequest.of(page, size, COMMENT_ORDER);
        if (anchorType == null) {
            return commentRepository.findByContentIdAndStatus(contentId, Comment.Status.ACTIVE, request);
        }
        return commentRepository.findByContentIdAndAnchorTypeAndAnchorValueAndStatus(
                contentId, anchorType, anchorValue, Comment.Status.ACTIVE, request);
    }

    public AdminReply reply(UUID commentId, CreateAdminReplyRequest request) {
        AuthenticatedUser user = currentUser();
        if (!user.isAdmin()) {
            throw new CommentException("FORBIDDEN", "Administrator permission is required");
        }
        Comment comment = get(commentId);
        String text = normalizeText(request == null ? null : request.text());
        AdminReply reply = new AdminReply(UUID.randomUUID(), user.id(), text, Instant.now());
        comment.addReply(reply);
        adminReplyRepository.save(reply);
        commentRepository.save(comment);
        return reply;
    }

    private ValidatedAnchor validateAnchor(UUID contentId, AnchorRequest anchor) {
        if (anchor.type() == null || anchor.value() == null || anchor.revision() == null
                || anchor.type().isBlank() || anchor.value().isBlank()) {
            throw new CommentException("ANCHOR_INVALID", "Anchor is invalid");
        }
        contentValidator.assertPublishedContent(contentId);
        return contentValidator.validateAnchor(contentId,
                new AnchorInput(anchor.type(), anchor.value(), anchor.revision()));
    }

    private String normalizeText(String text) {
        try {
            return CommentTextPolicy.normalize(text);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        }
    }

    private void requireOwner(Comment comment, AuthenticatedUser user) {
        if (!comment.getAuthorId().equals(user.id())) {
            throw new CommentException("FORBIDDEN", "Only the comment author can edit it");
        }
    }

    private void requireOwnerOrAdmin(Comment comment, AuthenticatedUser user) {
        if (!comment.getAuthorId().equals(user.id()) && !user.isAdmin()) {
            throw new CommentException("FORBIDDEN", "User is not allowed to remove this comment");
        }
    }

    private CommentException validation(String message) {
        return new CommentException("VALIDATION_ERROR", message);
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = userProvider.currentUser();
        if (user == null || user.id() == null) {
            throw new CommentException("UNAUTHORIZED", "Authentication is required");
        }
        return user;
    }
}
