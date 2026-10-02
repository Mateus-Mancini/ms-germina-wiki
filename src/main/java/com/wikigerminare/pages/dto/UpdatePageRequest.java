package com.wikigerminare.pages.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.UUID;

public class UpdatePageRequest {

    private String title;

    private String content;

    private UUID folderId;

    private boolean folderIdProvided;

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

    public UUID getFolderId() {
        return folderId;
    }

    /** Moving a page to another folder (drag and drop in the binder); null is rejected. */
    @JsonSetter(value = "folderId", nulls = Nulls.SET)
    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
        this.folderIdProvided = true;
    }

    public boolean isFolderIdProvided() {
        return folderIdProvided;
    }
}
