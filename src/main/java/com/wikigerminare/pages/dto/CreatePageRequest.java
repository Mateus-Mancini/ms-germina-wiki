package com.wikigerminare.pages.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePageRequest(

        @NotBlank(message = "title must not be blank")
        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,

        @NotBlank(message = "slug must not be blank")
        @Size(max = 300, message = "slug must not exceed 300 characters")
        String slug,

        @NotBlank(message = "content must not be blank")
        String content,

        @NotNull(message = "folderId must not be null")
        UUID folderId

) {
}
