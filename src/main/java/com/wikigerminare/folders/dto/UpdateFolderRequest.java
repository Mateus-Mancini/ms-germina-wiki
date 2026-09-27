package com.wikigerminare.folders.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UpdateFolderRequest {

    @Size(max = 150, message = "name must have at most 150 characters")
    private String name;

    private boolean nameProvided;

    private UUID parentFolderId;

    private boolean parentFolderIdProvided;

    public UpdateFolderRequest() {
    }

    public String getName() {
        return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public boolean isNameProvided() {
        return nameProvided;
    }

    public UUID getParentFolderId() {
        return parentFolderId;
    }

    @JsonSetter(value = "parentFolderId", nulls = Nulls.SET)
    public void setParentFolderId(UUID parentFolderId) {
        this.parentFolderId = parentFolderId;
        this.parentFolderIdProvided = true;
    }

    public boolean isParentFolderIdProvided() {
        return parentFolderIdProvided;
    }
}