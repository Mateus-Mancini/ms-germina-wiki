package com.wikigerminare.pages.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePageRequest {

    private String title;

    private String content;

    public UpdatePageRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isTitleProvided() {
        return title != null;
    }

    public boolean isContentProvided() {
        return content != null;
    }
}