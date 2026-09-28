package com.wikigerminare.pages.wikilinks;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WikiLinkParserTest {

    private final WikiLinkParser parser = new WikiLinkParser();

    @Test
    void shouldExtractWikiLinkSlug() {

        String markdown = """
                # Introdução

                Veja também [[outra-pagina]].
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of("outra-pagina"),
                result
        );
    }

    @Test
    void shouldExtractMultipleWikiLinks() {

        String markdown = """
                [[pagina-a]]
                [[pagina-b]]
                [[pagina-c]]
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of(
                        "pagina-a",
                        "pagina-b",
                        "pagina-c"
                ),
                result
        );
    }

    @Test
    void shouldRemoveDuplicatedWikiLinks() {

        String markdown = """
                [[pagina-a]]
                [[pagina-a]]
                [[pagina-a]]
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of("pagina-a"),
                result
        );
    }

    @Test
    void shouldIgnoreWikiLinksInsideInlineCode() {

        String markdown = """
                Texto normal [[pagina-real]].

                Código inline: `[[pagina-codigo]]`
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of("pagina-real"),
                result
        );
    }

    @Test
    void shouldIgnoreWikiLinksInsideFencedCode() {

        String markdown = """
                Texto normal [[pagina-real]].

                ```markdown
                [[pagina-codigo]]
                ```

                Outro texto [[outra-pagina]]
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of(
                        "pagina-real",
                        "outra-pagina"
                ),
                result
        );
    }

    @Test
    void shouldAllowSelfLink() {

        String markdown = """
                Esta página aponta para ela mesma: [[minha-pagina]]
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of("minha-pagina"),
                result
        );
    }

    @Test
    void shouldIgnoreEmptyWikiLink() {

        String markdown = """
                Texto [[]] texto.
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldIgnoreMalformedWikiLink() {

        String markdown = """
                [[pagina-aberta
                pagina-fechada]]
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPreserveExactSlug() {

        String markdown = """
                [[Minha-Pagina]]
                """;

        Set<String> result = parser.extractSlugs(markdown);

        assertEquals(
                Set.of("Minha-Pagina"),
                result
        );
    }

    @Test
    void shouldReturnEmptySetForEmptyContent() {

        Set<String> result = parser.extractSlugs("");

        assertTrue(result.isEmpty());
    }
}