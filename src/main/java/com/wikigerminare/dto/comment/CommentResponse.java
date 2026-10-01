package com.wikigerminare.dto.comment;

import com.wikigerminare.entity.comment.Comment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(UUID id, UUID pageId, UUID userId, AnchorResponse anchor, String text,
                              String status, Instant createdAt, Instant updatedAt,
                              List<AdminReplyResponse> adminReplies) {
    public static CommentResponse from(Comment comment) {
        List<AdminReplyResponse> replies = comment.getReplies().stream()
                .filter(reply -> reply.getStatus() == Comment.Status.OPEN)
                .map(AdminReplyResponse::from)
                .toList();
        return new CommentResponse(comment.getId(), comment.getPageId(), comment.getUserId(),
                new AnchorResponse(comment.getBlockId()), comment.getContent(),
                comment.getStatus().name(), comment.getCreatedAt(), comment.getUpdatedAt(), replies);
    }
}
