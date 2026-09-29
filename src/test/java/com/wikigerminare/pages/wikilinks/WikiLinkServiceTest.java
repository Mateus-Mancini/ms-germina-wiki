package com.wikigerminare.pages.wikilinks;

import com.wikigerminare.pages.Page;
import com.wikigerminare.pages.PageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WikiLinkServiceTest {

    @Mock
    private PageRepository pageRepository;

    @Mock
    private PageLinkRepository pageLinkRepository;

    @Mock
    private WikiLinkParser wikiLinkParser;

    private WikiLinkService service;

    private UUID sourceId;
    private UUID targetId;
    private UUID secondTargetId;

    @BeforeEach
    void setUp() {

        service = new WikiLinkService(
                pageRepository,
                pageLinkRepository,
                wikiLinkParser
        );

        sourceId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        secondTargetId = UUID.randomUUID();
    }

    @Test
    void shouldCreateResolvedLink() {

        Page source = page(sourceId, "Source");
        Page target = page(targetId, "Login");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(wikiLinkParser.extractSlugs("# Content"))
                .thenReturn(Set.of("Login"));

        when(pageRepository.findBySlug("Login"))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of());

        when(pageLinkRepository.existsBySourcePageIdAndTargetPageId(
                sourceId,
                targetId
        )).thenReturn(false);

        service.reconcile(sourceId, "# Content");

        ArgumentCaptor<PageLink> captor =
                ArgumentCaptor.forClass(PageLink.class);

        verify(pageLinkRepository).save(captor.capture());

        PageLink created = captor.getValue();

        assertEquals(sourceId, created.getSourcePageId());
        assertEquals(targetId, created.getTargetPageId());
        assertEquals("Login", created.getTargetPageTitle());
        assertTrue(created.getCreatedAt() != null);
    }

    @Test
    void shouldIgnoreUnresolvedSlug() {

        Page source = page(sourceId, "Source");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(wikiLinkParser.extractSlugs("# Content"))
                .thenReturn(Set.of("DoesNotExist"));

        when(pageRepository.findBySlug("DoesNotExist"))
                .thenReturn(Optional.empty());

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of());

        service.reconcile(sourceId, "# Content");

        verify(pageLinkRepository, never()).save(any());

        verify(
                pageLinkRepository,
                never()
        ).existsBySourcePageIdAndTargetPageId(
                any(),
                any()
        );
    }

    @Test
    void shouldAllowSelfLink() {

        Page source = page(sourceId, "Authentication");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(wikiLinkParser.extractSlugs("# Authentication"))
                .thenReturn(Set.of("Authentication"));

        when(pageRepository.findBySlug("Authentication"))
                .thenReturn(Optional.of(source));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of());

        when(pageLinkRepository.existsBySourcePageIdAndTargetPageId(
                sourceId,
                sourceId
        )).thenReturn(false);

        service.reconcile(sourceId, "# Authentication");

        ArgumentCaptor<PageLink> captor =
                ArgumentCaptor.forClass(PageLink.class);

        verify(pageLinkRepository).save(captor.capture());

        PageLink created = captor.getValue();

        assertEquals(sourceId, created.getSourcePageId());
        assertEquals(sourceId, created.getTargetPageId());
    }

    @Test
    void shouldNotDuplicateExistingLink() {

        Page source = page(sourceId, "Source");
        Page target = page(targetId, "Login");

        PageLink existing = new PageLink();

        existing.setSourcePageId(sourceId);
        existing.setTargetPageId(targetId);
        existing.setTargetPageTitle("Login");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(wikiLinkParser.extractSlugs("# Content"))
                .thenReturn(Set.of("Login"));

        when(pageRepository.findBySlug("Login"))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(existing));

        when(pageLinkRepository.existsBySourcePageIdAndTargetPageId(
                sourceId,
                targetId
        )).thenReturn(true);

        service.reconcile(sourceId, "# Content");

        verify(
                pageLinkRepository,
                never()
        ).save(any());

        verify(
                pageLinkRepository,
                never()
        ).delete(any());
    }

    @Test
    void shouldRemoveObsoleteLink() {

        Page source = page(sourceId, "Source");

        PageLink existing = new PageLink();

        existing.setSourcePageId(sourceId);
        existing.setTargetPageId(targetId);
        existing.setTargetPageTitle("OldPage");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(wikiLinkParser.extractSlugs("# Content"))
                .thenReturn(Set.of());

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(existing));

        service.reconcile(sourceId, "# Content");

        verify(pageLinkRepository).delete(existing);
    }

    /*
     * ============================================================
     * T014 - US2
     * Outgoing WikiLinks
     * ============================================================
     */

    @Test
    void shouldReturnOutgoingLinksForSourcePage() {

        Page target = page(targetId, "Login");

        PageLink link = pageLink(
                sourceId,
                targetId,
                "Login"
        );

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(
                        page(sourceId, "Source")
                ));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(link));

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        var result = service.getOutgoingLinks(sourceId);

        assertEquals(1, result.size());

        assertEquals(
                targetId,
                result.get(0).id()
        );

        assertEquals(
                "Login",
                result.get(0).title()
        );

        assertEquals(
                "Login",
                result.get(0).slug()
        );
    }

    @Test
    void shouldReturnEmptyListWhenSourceHasNoOutgoingLinks() {

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(
                        page(sourceId, "Source")
                ));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of());

        var result = service.getOutgoingLinks(sourceId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnSelfLinkAsOutgoingLink() {

        Page source = page(sourceId, "Authentication");

        PageLink selfLink = pageLink(
                sourceId,
                sourceId,
                "Authentication"
        );

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(selfLink));

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        var result = service.getOutgoingLinks(sourceId);

        assertEquals(1, result.size());

        assertEquals(
                sourceId,
                result.get(0).id()
        );

        assertEquals(
                "Authentication",
                result.get(0).title()
        );

        assertEquals(
                "Authentication",
                result.get(0).slug()
        );
    }

    @Test
    void shouldReturnEachOutgoingTargetOnlyOnce() {

        Page target = page(targetId, "Login");

        PageLink firstLink = pageLink(
                sourceId,
                targetId,
                "Login"
        );

        PageLink duplicateLink = pageLink(
                sourceId,
                targetId,
                "Login"
        );

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(
                        page(sourceId, "Source")
                ));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(
                        firstLink,
                        duplicateLink
                ));

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        var result = service.getOutgoingLinks(sourceId);

        assertEquals(1, result.size());

        assertEquals(
                targetId,
                result.get(0).id()
        );
    }

    @Test
    void shouldReturnMultipleDistinctOutgoingTargets() {

        Page firstTarget = page(targetId, "Login");
        Page secondTarget = page(secondTargetId, "Home");

        PageLink firstLink = pageLink(
                sourceId,
                targetId,
                "Login"
        );

        PageLink secondLink = pageLink(
                sourceId,
                secondTargetId,
                "Home"
        );

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(
                        page(sourceId, "Source")
                ));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(
                        firstLink,
                        secondLink
                ));

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(firstTarget));

        when(pageRepository.findById(secondTargetId))
                .thenReturn(Optional.of(secondTarget));

        var result = service.getOutgoingLinks(sourceId);

        assertEquals(2, result.size());

        assertEquals(
                targetId,
                result.get(0).id()
        );

        assertEquals(
                secondTargetId,
                result.get(1).id()
        );
    }

    /*
     * ============================================================
     * Helpers
     * ============================================================
     */

    private Page page(UUID id, String slug) {

        Page page = new Page();

        page.setId(id);
        page.setSlug(slug);
        page.setTitle(slug);
        page.setContent("");

        return page;
    }

    private PageLink pageLink(
            UUID sourceId,
            UUID targetId,
            String targetTitle
    ) {

        PageLink link = new PageLink();

        link.setSourcePageId(sourceId);
        link.setTargetPageId(targetId);
        link.setTargetPageTitle(targetTitle);

        return link;
    }
}
