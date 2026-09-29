package com.wikigerminare.repository.comment;

import com.wikigerminare.entity.comment.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Optional<Comment> findByIdAndStatus(UUID id, Comment.Status status);

    Page<Comment> findByPageIdAndParentCommentIsNullAndStatus(
            UUID pageId, Comment.Status status, Pageable pageable);

    Page<Comment> findByPageIdAndBlockIdAndParentCommentIsNullAndStatus(
            UUID pageId, UUID blockId, Comment.Status status, Pageable pageable);
}
