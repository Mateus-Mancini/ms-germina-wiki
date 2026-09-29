package com.wikigerminare.pages.wikilinks;

import com.wikigerminare.pages.Page;
import com.wikigerminare.pages.PageRepository;
import com.wikigerminare.pages.wikilinks.dto.LinkedPageSummary;
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

    @BeforeEach
    void setUp() {

        service = new WikiLinkService(
                pageRepository,
                pageLinkRepository,
                wikiLinkParser
        );

        sourceId = UUID.randomUUID();
        targetId = UUID.randomUUID();
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
        Page target = page(targetId, "OldPage");

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

    // ============================================================
    // T014 - Outgoing WikiLinks
    // ============================================================

    @Test
    void shouldReturnOutgoingLinks() {

        Page source = page(sourceId, "Source");
        Page target = page(targetId, "Login");

        PageLink link = new PageLink();
        link.setSourcePageId(sourceId);
        link.setTargetPageId(targetId);
        link.setTargetPageTitle("Login");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(link));

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        List<LinkedPageSummary> result =
                service.getOutgoingLinks(sourceId);

        assertEquals(1, result.size());
        assertEquals(targetId, result.get(0).id());
        assertEquals("Login", result.get(0).title());
        assertEquals("Login", result.get(0).slug());
    }

    @Test
    void shouldReturnEmptyOutgoingLinks() {

        Page source = page(sourceId, "Source");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of());

        List<LinkedPageSummary> result =
                service.getOutgoingLinks(sourceId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotDuplicateOutgoingTarget() {

        Page source = page(sourceId, "Source");
        Page target = page(targetId, "Login");

        PageLink firstLink = new PageLink();
        firstLink.setSourcePageId(sourceId);
        firstLink.setTargetPageId(targetId);
        firstLink.setTargetPageTitle("Login");

        PageLink secondLink = new PageLink();
        secondLink.setSourcePageId(sourceId);
        secondLink.setTargetPageId(targetId);
        secondLink.setTargetPageTitle("Login");

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        when(pageLinkRepository.findBySourcePageId(sourceId))
                .thenReturn(List.of(firstLink, secondLink));

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        List<LinkedPageSummary> result =
                service.getOutgoingLinks(sourceId);

        assertEquals(1, result.size());
        assertEquals(targetId, result.get(0).id());
    }

    // ============================================================
    // T018 - Backlinks
    // ============================================================

    @Test
    void shouldReturnBacklinksFromDistinctSourcePages() {

        UUID sourceOneId = UUID.randomUUID();
        UUID sourceTwoId = UUID.randomUUID();

        Page target = page(targetId, "Target");
        Page sourceOne = page(sourceOneId, "SourceOne");
        Page sourceTwo = page(sourceTwoId, "SourceTwo");

        PageLink linkOne = new PageLink();
        linkOne.setSourcePageId(sourceOneId);
        linkOne.setTargetPageId(targetId);
        linkOne.setTargetPageTitle("Target");

        PageLink linkTwo = new PageLink();
        linkTwo.setSourcePageId(sourceTwoId);
        linkTwo.setTargetPageId(targetId);
        linkTwo.setTargetPageTitle("Target");

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findByTargetPageId(targetId))
                .thenReturn(List.of(linkOne, linkTwo));

        when(pageRepository.findById(sourceOneId))
                .thenReturn(Optional.of(sourceOne));

        when(pageRepository.findById(sourceTwoId))
                .thenReturn(Optional.of(sourceTwo));

        List<LinkedPageSummary> result =
                service.getBacklinks(targetId);

        assertEquals(2, result.size());

        assertEquals(sourceOneId, result.get(0).id());
        assertEquals("SourceOne", result.get(0).title());
        assertEquals("SourceOne", result.get(0).slug());

        assertEquals(sourceTwoId, result.get(1).id());
        assertEquals("SourceTwo", result.get(1).title());
        assertEquals("SourceTwo", result.get(1).slug());
    }

    @Test
    void shouldReturnEmptyBacklinks() {

        Page target = page(targetId, "Target");

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findByTargetPageId(targetId))
                .thenReturn(List.of());

        List<LinkedPageSummary> result =
                service.getBacklinks(targetId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAllowSelfLinkAsBacklink() {

        Page target = page(targetId, "Target");

        PageLink selfLink = new PageLink();
        selfLink.setSourcePageId(targetId);
        selfLink.setTargetPageId(targetId);
        selfLink.setTargetPageTitle("Target");

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findByTargetPageId(targetId))
                .thenReturn(List.of(selfLink));

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        List<LinkedPageSummary> result =
                service.getBacklinks(targetId);

        assertEquals(1, result.size());
        assertEquals(targetId, result.get(0).id());
        assertEquals("Target", result.get(0).title());
        assertEquals("Target", result.get(0).slug());
    }

    @Test
    void shouldIgnoreNullTargetRowsWhenQueryingBacklinks() {

        Page target = page(targetId, "Target");

        UUID sourceOneId = UUID.randomUUID();

        PageLink activeLink = new PageLink();
        activeLink.setSourcePageId(sourceOneId);
        activeLink.setTargetPageId(targetId);
        activeLink.setTargetPageTitle("Target");

        PageLink unresolvedLink = new PageLink();
        unresolvedLink.setSourcePageId(UUID.randomUUID());
        unresolvedLink.setTargetPageId(null);
        unresolvedLink.setTargetPageTitle("Target");

        Page sourceOne = page(sourceOneId, "SourceOne");

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findByTargetPageId(targetId))
                .thenReturn(List.of(activeLink));

        when(pageRepository.findById(sourceOneId))
                .thenReturn(Optional.of(sourceOne));

        List<LinkedPageSummary> result =
                service.getBacklinks(targetId);

        assertEquals(1, result.size());
        assertEquals(sourceOneId, result.get(0).id());
    }

    @Test
    void shouldNotDuplicateBacklinkFromSameSourcePage() {

        Page target = page(targetId, "Target");
        Page source = page(sourceId, "Source");

        PageLink firstLink = new PageLink();
        firstLink.setSourcePageId(sourceId);
        firstLink.setTargetPageId(targetId);
        firstLink.setTargetPageTitle("Target");

        PageLink secondLink = new PageLink();
        secondLink.setSourcePageId(sourceId);
        secondLink.setTargetPageId(targetId);
        secondLink.setTargetPageTitle("Target");

        when(pageRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        when(pageLinkRepository.findByTargetPageId(targetId))
                .thenReturn(List.of(firstLink, secondLink));

        when(pageRepository.findById(sourceId))
                .thenReturn(Optional.of(source));

        List<LinkedPageSummary> result =
                service.getBacklinks(targetId);

        assertEquals(1, result.size());
        assertEquals(sourceId, result.get(0).id());
    }

    private Page page(UUID id, String slug) {

        Page page = new Page();

        page.setId(id);
        page.setSlug(slug);
        page.setTitle(slug);
        page.setContent("");

        return page;
    }
}
