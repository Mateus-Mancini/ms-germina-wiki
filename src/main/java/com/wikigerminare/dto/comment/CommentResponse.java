package com.wikigerminare.dto.comment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wikigerminare.entity.comment.Comment;

public record CommentResponse(UUID id, UUID contentId, UUID authorId, AnchorResponse anchor, String text,
                              String status, Instant createdAt, Instant updatedAt,
                              List<AdminReplyResponse> adminReplies) {
    public static CommentResponse from(Comment comment) {
        List<AdminReplyResponse> replies = comment.getAdminReplies().stream()
                .map(AdminReplyResponse::from)
                .toList();
        return new CommentResponse(comment.getId(), comment.getContentId(), comment.getAuthorId(),
                new AnchorResponse(comment.getAnchorType(), comment.getAnchorValue(), comment.getContentRevision()),
                comment.getText(), comment.getStatus().name(), comment.getCreatedAt(), comment.getUpdatedAt(), replies);
    }
}
