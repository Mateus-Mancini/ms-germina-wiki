package com.wikigerminare.support;

import java.util.UUID;

import com.wikigerminare.integration.AnchorInput;
import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.integration.ValidatedAnchor;

public final class CommentTestFixtures {
    public static final UUID CONTENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID AUTHOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private CommentTestFixtures() {
    }

    public static AuthenticatedUser author() {
        return new AuthenticatedUser(AUTHOR_ID, false);
    }

    public static AuthenticatedUser otherUser() {
        return new AuthenticatedUser(OTHER_USER_ID, false);
    }

    public static AuthenticatedUser admin() {
        return new AuthenticatedUser(ADMIN_ID, true);
    }

    public static AnchorInput anchor(String value) {
        return new AnchorInput("paragraph", value, "1");
    }

    public static ValidatedAnchor validatedAnchor(String value) {
        return new ValidatedAnchor("paragraph", value, "1");
    }

    public static ContentAnchorValidator publishedContentValidator() {
        return new ContentAnchorValidator() {
            @Override
            public void assertPublishedContent(UUID contentId) {
                if (!CONTENT_ID.equals(contentId)) {
                    throw new IllegalArgumentException("Content not found");
                }
            }

            @Override
            public ValidatedAnchor validateAnchor(UUID contentId, AnchorInput anchorInput) {
                assertPublishedContent(contentId);
                if (anchorInput == null || anchorInput.type() == null || anchorInput.value() == null) {
                    throw new IllegalArgumentException("Anchor is invalid");
                }
                return validatedAnchor(anchorInput.value());
            }
        };
    }
}
