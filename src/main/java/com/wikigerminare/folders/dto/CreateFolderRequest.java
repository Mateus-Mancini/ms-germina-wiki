package com.wikigerminare.folders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFolderRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must have at most 150 characters")
        String name,

        UUID parentFolderId
) {
}