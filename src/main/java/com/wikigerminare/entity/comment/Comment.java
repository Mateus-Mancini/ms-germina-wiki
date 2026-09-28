package com.wikigerminare.entity.comment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {
    public enum Status { ACTIVE, REMOVED }

    @Id
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "anchor_type", nullable = false, length = 100)
    private String anchorType;

    @Column(name = "anchor_value", nullable = false, columnDefinition = "text")
    private String anchorValue;

    @Column(name = "content_revision", nullable = false, length = 100)
    private String contentRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<AdminReply> adminReplies = new ArrayList<>();

    protected Comment() {
    }

    public Comment(UUID id, UUID contentId, UUID authorId, String text, String anchorType,
                   String anchorValue, String contentRevision, Instant createdAt) {
        this.id = id;
        this.contentId = contentId;
        this.authorId = authorId;
        this.text = text;
        this.anchorType = anchorType;
        this.anchorValue = anchorValue;
        this.contentRevision = contentRevision;
        this.status = Status.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void updateText(String text, Instant updatedAt) {
        this.text = text;
        this.updatedAt = updatedAt;
    }

    public void remove() {
        this.status = Status.REMOVED;
    }

    public void addReply(AdminReply reply) {
        adminReplies.add(reply);
        reply.attachTo(this);
    }

    public UUID getId() { return id; }
    public UUID getContentId() { return contentId; }
    public UUID getAuthorId() { return authorId; }
    public String getText() { return text; }
    public String getAnchorType() { return anchorType; }
    public String getAnchorValue() { return anchorValue; }
    public String getContentRevision() { return contentRevision; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AdminReply> getAdminReplies() { return adminReplies; }
}
