package com.wikigerminare.pages.wikilinks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "page_links")
public class PageLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_page_id", nullable = false)
    private UUID sourcePageId;

    @Column(name = "target_page_id")
    private UUID targetPageId;

    @Column(name = "target_page_title", nullable = false, length = 255)
    private String targetPageTitle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PageLink() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourcePageId() {
        return sourcePageId;
    }

    public void setSourcePageId(UUID sourcePageId) {
        this.sourcePageId = sourcePageId;
    }

    public UUID getTargetPageId() {
        return targetPageId;
    }

    public void setTargetPageId(UUID targetPageId) {
        this.targetPageId = targetPageId;
    }

    public String getTargetPageTitle() {
        return targetPageTitle;
    }

    public void setTargetPageTitle(String targetPageTitle) {
        this.targetPageTitle = targetPageTitle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}