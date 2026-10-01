package com.wikigerminare.integration;

import java.util.UUID;

public record AuthenticatedUser(UUID id, boolean isAdmin) {
}
