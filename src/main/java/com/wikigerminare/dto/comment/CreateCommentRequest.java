package com.wikigerminare.dto.comment;

import java.util.UUID;

public record CreateCommentRequest(UUID pageId, AnchorRequest anchor, String text) {
}
