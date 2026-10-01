package com.wikigerminare.integration;

import java.util.UUID;

public interface ContentAnchorValidator {
    void assertPublishedContent(UUID pageId);

    ValidatedAnchor validateAnchor(UUID pageId, AnchorInput anchorInput);
}
