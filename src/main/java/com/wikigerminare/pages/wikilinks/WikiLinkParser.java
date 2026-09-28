package com.wikigerminare.pages.wikilinks;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WikiLinkParser {

    private static final Pattern WIKILINK_PATTERN =
            Pattern.compile("\\[\\[([^\\[\\]\\n]+)\\]\\]");

    public Set<String> extractSlugs(String markdown) {

        Set<String> slugs = new LinkedHashSet<>();

        if (markdown == null || markdown.isEmpty()) {
            return slugs;
        }

        String withoutCode = removeCodeBlocks(markdown);

        Matcher matcher = WIKILINK_PATTERN.matcher(withoutCode);

        while (matcher.find()) {

            String slug = matcher.group(1);

            if (slug.contains("|")) {
                continue;
            }

            if (slug.isBlank()) {
                continue;
            }

            slugs.add(slug);
        }

        return slugs;
    }

    private String removeCodeBlocks(String markdown) {

        StringBuilder result = new StringBuilder();

        String[] lines = markdown.split("\\R", -1);

        boolean insideFence = false;

        for (String line : lines) {

            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                insideFence = !insideFence;
                result.append("\n");
                continue;
            }

            if (insideFence) {
                result.append("\n");
            } else {
                result.append(removeInlineCode(line));
                result.append("\n");
            }
        }

        return result.toString();
    }

    private String removeInlineCode(String line) {

        StringBuilder result = new StringBuilder();

        boolean insideInlineCode = false;

        for (int i = 0; i < line.length(); i++) {

            char current = line.charAt(i);

            if (current == '`') {
                insideInlineCode = !insideInlineCode;
                result.append(' ');
                continue;
            }

            if (insideInlineCode) {
                result.append(' ');
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }
}