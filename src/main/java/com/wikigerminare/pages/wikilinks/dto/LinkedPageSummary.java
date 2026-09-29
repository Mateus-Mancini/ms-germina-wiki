package com.wikigerminare.pages.wikilinks.dto;

import java.util.UUID;

public record LinkedPageSummary(
        UUID id,
        String title,
        String slug
) {
}
