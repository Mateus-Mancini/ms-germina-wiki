package com.wikigerminare.folders;

import java.util.UUID;

public class FolderNotFoundException extends RuntimeException {

    public FolderNotFoundException(UUID id) {
        super("Folder not found: " + id);
    }
}