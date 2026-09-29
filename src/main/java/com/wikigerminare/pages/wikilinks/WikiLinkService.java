package com.wikigerminare.pages.wikilinks;

import com.wikigerminare.pages.Page;
import com.wikigerminare.pages.PageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WikiLinkService {

    private final PageRepository pageRepository;
    private final PageLinkRepository pageLinkRepository;
    private final WikiLinkParser wikiLinkParser;

    public WikiLinkService(
            PageRepository pageRepository,
            PageLinkRepository pageLinkRepository,
            WikiLinkParser wikiLinkParser
    ) {
        this.pageRepository = pageRepository;
        this.pageLinkRepository = pageLinkRepository;
        this.wikiLinkParser = wikiLinkParser;
    }

    @Transactional
    public void reconcile(UUID sourcePageId, String markdown) {

        Page sourcePage = pageRepository.findById(sourcePageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source page not found: " + sourcePageId
                        ));

        Set<String> desiredSlugs =
                new LinkedHashSet<>(
                        wikiLinkParser.extractSlugs(markdown)
                );

        Map<String, Page> resolvedTargets =
                new LinkedHashMap<>();

        for (String slug : desiredSlugs) {

            pageRepository.findBySlug(slug)
                    .ifPresent(target ->
                            resolvedTargets.put(slug, target));
        }

        Set<String> desiredTitles =
                new LinkedHashSet<>(resolvedTargets.keySet());

        List<PageLink> existingLinks =
                pageLinkRepository.findBySourcePageId(sourcePageId);

        for (PageLink existing : existingLinks) {

            if (!desiredTitles.contains(
                    existing.getTargetPageTitle()
            )) {
                pageLinkRepository.delete(existing);
            }
        }

        for (Map.Entry<String, Page> entry :
                resolvedTargets.entrySet()) {

            String slug = entry.getKey();
            Page targetPage = entry.getValue();

            boolean alreadyExists =
                    pageLinkRepository
                            .existsBySourcePageIdAndTargetPageId(
                                    sourcePageId,
                                    targetPage.getId()
                            );

            if (alreadyExists) {
                continue;
            }

            PageLink pageLink = new PageLink();

            pageLink.setSourcePageId(sourcePageId);
            pageLink.setTargetPageId(targetPage.getId());
            pageLink.setTargetPageTitle(slug);
            pageLink.setCreatedAt(Instant.now());

            pageLinkRepository.save(pageLink);
        }
    }
}