package com.wikigerminare.dto.comment;

import java.util.List;

import org.springframework.data.domain.Page;

public record CommentPageResponse(List<CommentResponse> items, int page, int size,
                                  long totalItems, int totalPages) {
    public static CommentPageResponse from(Page<com.wikigerminare.entity.comment.Comment> comments) {
        return new CommentPageResponse(comments.getContent().stream().map(CommentResponse::from).toList(),
                comments.getNumber(), comments.getSize(), comments.getTotalElements(), comments.getTotalPages());
    }
}
