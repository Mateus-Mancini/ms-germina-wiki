package com.wikigerminare.pages.wikilinks;

import com.wikigerminare.pages.Page;
import com.wikigerminare.pages.PageRepository;
import com.wikigerminare.pages.wikilinks.dto.LinkedPageSummary;
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

        pageRepository.findById(sourcePageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source page not found: " + sourcePageId
                        ));

        pageLinkRepository.acquireWikiLinkLock(
                "wikilinks:source:" + sourcePageId
        );

        Set<String> desiredSlugs =
                new LinkedHashSet<>(
                        wikiLinkParser.extractSlugs(markdown)
                );

        Map<String, Page> resolvedTargets =
                new LinkedHashMap<>();

        for (String slug : desiredSlugs) {

            pageRepository.findBySlug(slug)
                    .ifPresent(target ->
                            resolvedTargets.put(slug, target)
                    );
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

    @Transactional
    public void resolvePendingLinks(
            String targetPageSlug,
            UUID targetPageId
    ) {

        pageLinkRepository.acquireWikiLinkLock(
                "wikilinks:target:" + targetPageSlug
        );

        List<PageLink> pendingLinks =
                pageLinkRepository
                        .findByTargetPageIdIsNullAndTargetPageTitle(
                                targetPageSlug
                        );

        for (PageLink pageLink : pendingLinks) {

            if (pageLink.getTargetPageId() != null) {
                continue;
            }

            pageLink.setTargetPageId(targetPageId);

            pageLinkRepository.save(pageLink);
        }
    }

    /**
     * Retorna as páginas apontadas pelos WikiLinks
     * de uma página de origem.
     */
    @Transactional(readOnly = true)
    public List<LinkedPageSummary> getOutgoingLinks(
            UUID sourcePageId
    ) {

        pageRepository.findById(sourcePageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source page not found: " + sourcePageId
                        ));

        List<PageLink> links =
                pageLinkRepository.findBySourcePageId(
                        sourcePageId
                );

        Map<UUID, LinkedPageSummary> distinctTargets =
                new LinkedHashMap<>();

        for (PageLink link : links) {

            UUID targetPageId =
                    link.getTargetPageId();

            if (targetPageId == null) {
                continue;
            }

            pageRepository.findById(targetPageId)
                    .ifPresent(targetPage ->
                            distinctTargets.putIfAbsent(
                                    targetPage.getId(),
                                    new LinkedPageSummary(
                                            targetPage.getId(),
                                            targetPage.getTitle(),
                                            targetPage.getSlug()
                                    )
                            )
                    );
        }

        return List.copyOf(
                distinctTargets.values()
        );
    }

    /**
     * Retorna as páginas que possuem WikiLinks
     * apontando para a página informada.
     *
     * Relações sem target_page_id são ignoradas,
     * pois representam links ainda não resolvidos.
     */
    @Transactional(readOnly = true)
    public List<LinkedPageSummary> getBacklinks(
            UUID targetPageId
    ) {

        pageRepository.findById(targetPageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Target page not found: " + targetPageId
                        ));

        List<PageLink> links =
                pageLinkRepository.findByTargetPageId(
                        targetPageId
                );

        Map<UUID, LinkedPageSummary> distinctSources =
                new LinkedHashMap<>();

        for (PageLink link : links) {

            UUID sourcePageId =
                    link.getSourcePageId();

            if (sourcePageId == null) {
                continue;
            }

            pageRepository.findById(sourcePageId)
                    .ifPresent(sourcePage ->
                            distinctSources.putIfAbsent(
                                    sourcePage.getId(),
                                    new LinkedPageSummary(
                                            sourcePage.getId(),
                                            sourcePage.getTitle(),
                                            sourcePage.getSlug()
                                    )
                            )
                    );
        }

        return List.copyOf(
                distinctSources.values()
        );
    }
}