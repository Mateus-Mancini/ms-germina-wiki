package com.wikigerminare.pages;

import java.util.UUID;

public class PagePreconditionFailedException
        extends RuntimeException {

    private final UUID pageId;
    private final int currentVersion;

    public PagePreconditionFailedException(
            UUID pageId,
            int currentVersion
    ) {
        super("The page was modified by another request");
        this.pageId = pageId;
        this.currentVersion = currentVersion;
    }

    public UUID getPageId() {
        return pageId;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}