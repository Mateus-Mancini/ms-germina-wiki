package com.wikigerminare.pages.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePageRequest(

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must have at most 255 characters")
        String title,

        @NotBlank(message = "slug is required")
        @Size(max = 300, message = "slug must have at most 300 characters")
        String slug,

        @NotNull(message = "content is required")
        String content,

        @NotNull(message = "folderId is required")
        UUID folderId

) {
}
