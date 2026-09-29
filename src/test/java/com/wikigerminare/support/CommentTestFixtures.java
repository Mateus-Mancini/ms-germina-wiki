package com.wikigerminare.support;

import com.wikigerminare.integration.AnchorInput;
import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.integration.ValidatedAnchor;

import java.util.UUID;

public final class CommentTestFixtures {
    public static final UUID PAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID BLOCK_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID AUTHOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private CommentTestFixtures() {
    }

    public static AuthenticatedUser author() { return new AuthenticatedUser(AUTHOR_ID, false); }
    public static AuthenticatedUser otherUser() { return new AuthenticatedUser(OTHER_USER_ID, false); }
    public static AuthenticatedUser admin() { return new AuthenticatedUser(ADMIN_ID, true); }
    public static AnchorInput anchor() { return new AnchorInput(BLOCK_ID); }
    public static ValidatedAnchor validatedAnchor() { return new ValidatedAnchor(BLOCK_ID); }

    public static ContentAnchorValidator publishedContentValidator() {
        return new ContentAnchorValidator() {
            @Override
            public void assertPublishedContent(UUID pageId) {
                if (!PAGE_ID.equals(pageId)) {
                    throw new IllegalArgumentException("Page not found");
                }
            }

            @Override
            public ValidatedAnchor validateAnchor(UUID pageId, AnchorInput anchorInput) {
                assertPublishedContent(pageId);
                if (anchorInput == null || !BLOCK_ID.equals(anchorInput.blockId())) {
                    throw new IllegalArgumentException("Block is invalid");
                }
                return validatedAnchor();
            }
        };
    }
}
