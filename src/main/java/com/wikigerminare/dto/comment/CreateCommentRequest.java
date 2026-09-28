package com.wikigerminare.dto.comment;

import java.util.UUID;

public record CreateCommentRequest(UUID contentId, AnchorRequest anchor, String text) {
}
