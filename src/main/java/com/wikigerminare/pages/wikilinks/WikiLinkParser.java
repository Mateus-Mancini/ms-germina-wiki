package com.wikigerminare.pages.wikilinks;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class WikiLinkParser {

    private static final Pattern WIKI_LINK_PATTERN =
            Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");

    public Set<String> extractSlugs(String content) {

        Set<String> slugs = new LinkedHashSet<>();

        if (content == null || content.isEmpty()) {
            return slugs;
        }

        boolean fencedCode = false;

        String[] lines = content.split("\\R", -1);

        for (String line : lines) {

            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                fencedCode = !fencedCode;
                continue;
            }

            if (fencedCode) {
                continue;
            }

            String withoutInlineCode = removeInlineCode(line);

            Matcher matcher = WIKI_LINK_PATTERN.matcher(withoutInlineCode);

            while (matcher.find()) {
                slugs.add(matcher.group(1));
            }
        }

        return slugs;
    }

    private String removeInlineCode(String line) {

        StringBuilder result = new StringBuilder();

        boolean insideCode = false;

        for (int i = 0; i < line.length(); i++) {

            char current = line.charAt(i);

            if (current == '`') {
                insideCode = !insideCode;
                continue;
            }

            if (!insideCode) {
                result.append(current);
            }
        }

        return result.toString();
    }
}