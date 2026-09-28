package com.wikigerminare.repository.comment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wikigerminare.entity.comment.Comment;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Optional<Comment> findByIdAndStatus(UUID id, Comment.Status status);

    Page<Comment> findByContentIdAndStatus(UUID contentId, Comment.Status status, Pageable pageable);

    Page<Comment> findByContentIdAndAnchorTypeAndAnchorValueAndStatus(
            UUID contentId, String anchorType, String anchorValue, Comment.Status status, Pageable pageable);
}
