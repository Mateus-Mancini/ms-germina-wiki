package com.wikigerminare.entity.comment;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {
    public enum Status { OPEN, RESOLVED }

    @Id
    private UUID id;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @Column(name = "block_id", nullable = false)
    private UUID blockId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "comment_status")
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "parentComment", fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<Comment> replies = new ArrayList<>();

    protected Comment() {
    }

    public Comment(UUID id, UUID pageId, UUID userId, UUID blockId, String content, Instant createdAt) {
        this.id = id;
        this.pageId = pageId;
        this.userId = userId;
        this.blockId = blockId;
        this.content = content;
        this.status = Status.OPEN;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Comment reply(UUID id, Comment parent, UUID userId, String content, Instant createdAt) {
        Comment reply = new Comment(id, parent.getPageId(), userId, parent.getBlockId(), content, createdAt);
        reply.parentComment = parent;
        return reply;
    }

    public void updateContent(String content, Instant updatedAt) {
        this.content = content;
        this.updatedAt = updatedAt;
    }

    public void resolve() {
        this.status = Status.RESOLVED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPageId() { return pageId; }
    public UUID getUserId() { return userId; }
    public Comment getParentComment() { return parentComment; }
    public UUID getBlockId() { return blockId; }
    public String getContent() { return content; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Comment> getReplies() { return replies; }
}
