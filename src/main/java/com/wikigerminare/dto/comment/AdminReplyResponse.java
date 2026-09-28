package com.wikigerminare.dto.comment;

import java.time.Instant;
import java.util.UUID;

import com.wikigerminare.entity.comment.AdminReply;

public record AdminReplyResponse(UUID id, UUID commentId, UUID adminId, String text, Instant createdAt) {
    public static AdminReplyResponse from(AdminReply reply) {
        return new AdminReplyResponse(reply.getId(), reply.getComment().getId(), reply.getAdminId(),
                reply.getText(), reply.getCreatedAt());
    }
}
