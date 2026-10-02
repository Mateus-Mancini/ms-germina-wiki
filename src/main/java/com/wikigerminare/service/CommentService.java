package com.wikigerminare.service;

import com.wikigerminare.dto.comment.AnchorRequest;
import com.wikigerminare.dto.comment.CommentResponse;
import com.wikigerminare.dto.comment.CommentPageResponse;
import com.wikigerminare.dto.comment.AdminReplyResponse;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(timeout = 8)
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

    // HTTP responses are fully materialized before the service transaction commits.
    public CommentResponse create(CreateCommentRequest request) {
        return CommentResponse.from(createEntity(request));
    }

    @Transactional(readOnly = true, timeout = 8)
    public CommentResponse get(UUID id) {
        return CommentResponse.from(getActiveComment(id));
    }

    public CommentResponse update(UUID id, UpdateCommentRequest request) {
        return CommentResponse.from(updateEntity(id, request));
    }

    public AdminReplyResponse reply(UUID id, CreateAdminReplyRequest request) {
        return AdminReplyResponse.from(createReply(id, request));
    }

    @Transactional(readOnly = true, timeout = 8)
    public CommentPageResponse list(UUID pageId, UUID blockId, int page, int size) {
        Page<Comment> roots = findRoots(pageId, blockId, page, size);
        List<CommentResponse> items = List.of();
        if (!roots.isEmpty()) {
            Map<UUID, Comment> loaded = commentRepository.findWithRepliesByIdIn(
                    roots.stream().map(Comment::getId).toList()).stream()
                    .collect(Collectors.toMap(Comment::getId, Function.identity()));
            items = roots.stream().map(root -> CommentResponse.from(loaded.get(root.getId()))).toList();
        }
        return new CommentPageResponse(items, roots.getNumber(), roots.getSize(),
                roots.getTotalElements(), roots.getTotalPages());
    }

    private Comment createEntity(CreateCommentRequest request) {
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

    private Comment getActiveComment(UUID commentId) {
        Comment comment = commentRepository.findByIdAndStatus(commentId, Comment.Status.OPEN)
                .orElseThrow(() -> new CommentException("COMMENT_NOT_FOUND", "Comment not found"));
        if (comment.getParentComment() != null && comment.getParentComment().getStatus() != Comment.Status.OPEN) {
            throw new CommentException("COMMENT_NOT_FOUND", "Comment not found");
        }
        contentValidator.assertPublishedContent(comment.getPageId());
        return comment;
    }

    private Comment updateEntity(UUID commentId, UpdateCommentRequest request) {
        AuthenticatedUser user = currentUser();
        Comment comment = getActiveComment(commentId);
        requireOwner(comment, user);
        if (comment.getParentComment() != null) {
            throw new CommentException("FORBIDDEN", "Administrative replies cannot be edited");
        }
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

    private Page<Comment> findRoots(UUID pageId, UUID blockId, int page, int size) {
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

    private Comment createReply(UUID commentId, CreateAdminReplyRequest request) {
        AuthenticatedUser user = currentUser();
        if (!user.isAdmin()) {
            throw new CommentException("FORBIDDEN", "Administrator permission is required");
        }
        Comment parent = getActiveComment(commentId);
        if (parent.getParentComment() != null) {
            throw validation("Replies can only target a root comment");
        }
        validateAnchor(parent.getPageId(), new AnchorRequest(parent.getBlockId()));
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
