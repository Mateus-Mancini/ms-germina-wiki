package com.wikigerminare.service;

public final class CommentTextPolicy {
    public static final int MAX_TEXT_LENGTH = 2000;

    private CommentTextPolicy() {
    }

    public static String normalize(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Comment text is required");
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Comment text must not be blank");
        }
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Comment text exceeds the maximum length");
        }
        return normalized;
    }
}
