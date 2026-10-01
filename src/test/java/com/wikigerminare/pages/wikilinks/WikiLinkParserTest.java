package com.wikigerminare.pages.wikilinks;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiLinkParserTest {

    private final WikiLinkParser parser = new WikiLinkParser();

    @Test
    void shouldExtractWikiLinkSlugs() {

        String content = """
                # Authentication

                See [[Login]] and [[Authentication]].
                """;

        Set<String> result = parser.extractSlugs(content);

        assertEquals(
                Set.of("Login", "Authentication"),
                result
        );
    }

    @Test
    void shouldReturnDistinctSlugsWhenLinkIsRepeated() {

        String content = """
                [[Login]]
                [[Login]]
                [[Authentication]]
                [[Login]]
                """;

        Set<String> result = parser.extractSlugs(content);

        assertEquals(
                Set.of("Login", "Authentication"),
                result
        );
    }

    @Test
    void shouldPreserveSlugExactlyAsWritten() {

        String content = """
                [[My Page]]
                [[API-Reference]]
                [[Java_21]]
                """;

        Set<String> result = parser.extractSlugs(content);

        assertEquals(
                Set.of("My Page", "API-Reference", "Java_21"),
                result
        );
    }

    @Test
    void shouldIgnoreWikiLinksInsideInlineCode() {

        String content = """
                Normal link: [[Login]]

                This is code: `[[Authentication]]`

                Another link: [[Dashboard]]
                """;

        Set<String> result = parser.extractSlugs(content);

        assertEquals(
                Set.of("Login", "Dashboard"),
                result
        );
    }

    @Test
    void shouldIgnoreWikiLinksInsideFencedCode() {

        String content = """
                Normal link: [[Login]]

                ```text
                [[Authentication]]
                [[Dashboard]]
                ```

                Normal link: [[Profile]]
                """;

        Set<String> result = parser.extractSlugs(content);

        assertEquals(
                Set.of("Login", "Profile"),
                result
        );
    }

    @Test
    void shouldReturnEmptySetForEmptyContent() {

        Set<String> result = parser.extractSlugs("");

        assertEquals(
                Set.of(),
                result
        );
    }

    @Test
    void shouldReturnEmptySetForNullContent() {

        Set<String> result = parser.extractSlugs(null);

        assertEquals(
                Set.of(),
                result
        );
    }

    @Test
    void shouldAllowSelfLinks() {

        String content = """
                This page references itself: [[Authentication]]
                """;

        Set<String> result = parser.extractSlugs(content);

        assertEquals(
                Set.of("Authentication"),
                result
        );
    }
}