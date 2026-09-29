package com.wikigerminare.service;

import com.wikigerminare.dto.comment.AnchorRequest;
import com.wikigerminare.dto.comment.CreateAdminReplyRequest;
import com.wikigerminare.dto.comment.CreateCommentRequest;
import com.wikigerminare.dto.comment.UpdateCommentRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AnchorInput;
import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.integration.ValidatedAnchor;
import com.wikigerminare.repository.comment.CommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class CommentService {
    private static final Sort COMMENT_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CommentRepository commentRepository;
    private final AuthenticatedUserProvider userProvider;
    private final ContentAnchorValidator contentValidator;

    public CommentService(CommentRepository commentRepository,
                           AuthenticatedUserProvider userProvider,
                           ContentAnchorValidator contentValidator) {
        this.commentRepository = commentRepository;
        this.userProvider = userProvider;
        this.contentValidator = contentValidator;
    }

    public Comment create(CreateCommentRequest request) {
        AuthenticatedUser user = currentUser();
        if (request == null || request.pageId() == null || request.anchor() == null) {
            throw validation("pageId and blockId are required");
        }
        String content = normalizeText(request.text());
        ValidatedAnchor anchor = validateAnchor(request.pageId(), request.anchor());
        Instant now = Instant.now();
        Comment comment = new Comment(UUID.randomUUID(), request.pageId(), user.id(), anchor.blockId(), content, now);
        return commentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public Comment get(UUID commentId) {
        return commentRepository.findByIdAndStatus(commentId, Comment.Status.OPEN)
                .orElseThrow(() -> new CommentException("COMMENT_NOT_FOUND", "Comment not found"));
    }

    public Comment update(UUID commentId, UpdateCommentRequest request) {
        AuthenticatedUser user = currentUser();
        Comment comment = get(commentId);
        requireOwner(comment, user);
        String content = normalizeText(request == null ? null : request.text());
        validateAnchor(comment.getPageId(), new AnchorRequest(comment.getBlockId()));
        comment.updateContent(content, Instant.now());
        return commentRepository.save(comment);
    }

    public void remove(UUID commentId) {
        AuthenticatedUser user = currentUser();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentException("COMMENT_NOT_FOUND", "Comment not found"));
        requireOwnerOrAdmin(comment, user);
        if (comment.getStatus() == Comment.Status.OPEN) {
            comment.resolve();
            commentRepository.save(comment);
        }
    }

    @Transactional(readOnly = true)
    public Page<Comment> list(UUID pageId, UUID blockId, int page, int size) {
        if (pageId == null) {
            throw validation("pageId is required");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw validation("page must be >= 0 and size must be between 1 and 100");
        }
        contentValidator.assertPublishedContent(pageId);
        if (blockId != null) {
            contentValidator.validateAnchor(pageId, new AnchorInput(blockId));
        }
        PageRequest request = PageRequest.of(page, size, COMMENT_ORDER);
        if (blockId == null) {
            return commentRepository.findByPageIdAndParentCommentIsNullAndStatus(pageId, Comment.Status.OPEN, request);
        }
        return commentRepository.findByPageIdAndBlockIdAndParentCommentIsNullAndStatus(
                pageId, blockId, Comment.Status.OPEN, request);
    }

    public Comment reply(UUID commentId, CreateAdminReplyRequest request) {
        AuthenticatedUser user = currentUser();
        if (!user.isAdmin()) {
            throw new CommentException("FORBIDDEN", "Administrator permission is required");
        }
        Comment parent = get(commentId);
        String content = normalizeText(request == null ? null : request.text());
        Comment reply = Comment.reply(UUID.randomUUID(), parent, user.id(), content, Instant.now());
        return commentRepository.save(reply);
    }

    private ValidatedAnchor validateAnchor(UUID pageId, AnchorRequest anchor) {
        if (anchor == null || anchor.blockId() == null) {
            throw new CommentException("ANCHOR_INVALID", "blockId is required");
        }
        contentValidator.assertPublishedContent(pageId);
        return contentValidator.validateAnchor(pageId, new AnchorInput(anchor.blockId()));
    }

    private String normalizeText(String text) {
        try {
            return CommentTextPolicy.normalize(text);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        }
    }

    private void requireOwner(Comment comment, AuthenticatedUser user) {
        if (!comment.getUserId().equals(user.id())) {
            throw new CommentException("FORBIDDEN", "Only the comment author can edit it");
        }
    }

    private void requireOwnerOrAdmin(Comment comment, AuthenticatedUser user) {
        if (!comment.getUserId().equals(user.id()) && !user.isAdmin()) {
            throw new CommentException("FORBIDDEN", "User is not allowed to remove this comment");
        }
    }

    private AuthenticatedUser currentUser() {
        AuthenticatedUser user = userProvider.currentUser();
        if (user == null || user.id() == null) {
            throw new CommentException("UNAUTHORIZED", "Authentication is required");
        }
        return user;
    }

    private CommentException validation(String message) {
        return new CommentException("VALIDATION_ERROR", message);
    }
}
