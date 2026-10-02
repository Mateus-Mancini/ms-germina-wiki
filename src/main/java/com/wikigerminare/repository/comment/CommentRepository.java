package com.wikigerminare.repository.comment;

import com.wikigerminare.entity.comment.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    // Fetch replies only for the selected roots; never join a collection into a paginated query.
    @Query("select c from Comment c left join fetch c.replies where c.id in :ids")
    List<Comment> findWithRepliesByIdIn(@Param("ids") Collection<UUID> ids);

    Optional<Comment> findByIdAndStatus(UUID id, Comment.Status status);

    Page<Comment> findByPageIdAndParentCommentIsNullAndStatus(
            UUID pageId, Comment.Status status, Pageable pageable);

    Page<Comment> findByPageIdAndBlockIdAndParentCommentIsNullAndStatus(
            UUID pageId, UUID blockId, Comment.Status status, Pageable pageable);
}
