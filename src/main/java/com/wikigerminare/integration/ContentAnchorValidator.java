package com.wikigerminare.integration;

import java.util.UUID;

public interface ContentAnchorValidator {
    void assertPublishedContent(UUID contentId);

    ValidatedAnchor validateAnchor(UUID contentId, AnchorInput anchorInput);
}
