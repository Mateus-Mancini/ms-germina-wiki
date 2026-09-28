package com.wikigerminare.entity.comment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_replies")
public class AdminReply {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminReply() {
    }

    public AdminReply(UUID id, UUID adminId, String text, Instant createdAt) {
        this.id = id;
        this.adminId = adminId;
        this.text = text;
        this.createdAt = createdAt;
    }

    void attachTo(Comment comment) {
        this.comment = comment;
    }

    public UUID getId() { return id; }
    public Comment getComment() { return comment; }
    public UUID getAdminId() { return adminId; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
