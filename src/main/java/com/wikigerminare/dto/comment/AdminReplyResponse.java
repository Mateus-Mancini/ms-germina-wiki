package com.wikigerminare.dto.comment;

import com.wikigerminare.entity.comment.Comment;

import java.time.Instant;
import java.util.UUID;

public record AdminReplyResponse(UUID id, UUID commentId, UUID adminId, String text, Instant createdAt) {
    public static AdminReplyResponse from(Comment reply) {
        return new AdminReplyResponse(reply.getId(), reply.getParentComment().getId(), reply.getUserId(),
                reply.getContent(), reply.getCreatedAt());
    }
}
