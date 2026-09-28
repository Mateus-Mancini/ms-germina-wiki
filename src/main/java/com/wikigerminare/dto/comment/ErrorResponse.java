package com.wikigerminare.dto.comment;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, Object> details) {
}
