package com.wikigerminare.search;

import com.wikigerminare.folders.FolderNotFoundException;
import com.wikigerminare.folders.FolderRepository;
import com.wikigerminare.folders.Folder;
import com.wikigerminare.pages.Page;
import com.wikigerminare.pages.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchRepository searchRepository;

    @Mock
    private FolderRepository folderRepository;

    private SearchService searchService;
    private UUID pageId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(searchRepository, folderRepository);
        pageId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void shouldRejectNullQuery() {
        assertThrows(
                SearchValidationException.class,
                () -> searchService.search(null, null, false));
        verifyNoInteractions(searchRepository, folderRepository);
    }

    @Test
    void shouldRejectEmptyQuery() {
        assertThrows(
                SearchValidationException.class,
                () -> searchService.search("", null, false));
        verifyNoInteractions(searchRepository, folderRepository);
    }

    @Test
    void shouldRejectWhitespaceOnlyQuery() {
        assertThrows(
                SearchValidationException.class,
                () -> searchService.search(" \t ", null, false));
        verifyNoInteractions(searchRepository, folderRepository);
    }

    @Test
    void shouldMapGlobalResultsToPageResponse() {
        Page page = page(null);
        when(searchRepository.searchGlobally("alpha"))
                .thenReturn(List.of(page));

        List<PageResponse> results = searchService.search("alpha", null, false);

        assertEquals(1, results.size());
        assertEquals(pageId, results.getFirst().id());
        assertEquals("Alpha title", results.getFirst().title());
        assertEquals("alpha-page-" + pageId, results.getFirst().slug());
        assertEquals("# alpha content", results.getFirst().content());
        assertEquals(null, results.getFirst().folderId());
        assertEquals(userId, results.getFirst().createdBy());
        verify(searchRepository).searchGlobally("alpha");
        verifyNoInteractions(folderRepository);
    }

    @Test
    void shouldValidateProvidedFolderBeforeSearching() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThrows(
                FolderNotFoundException.class,
                () -> searchService.search("alpha", folderId, true));

        verify(searchRepository, never()).searchInFolderAndDescendants("alpha", folderId);
        verify(searchRepository, never()).searchInFolder("alpha", folderId);
    }

    @Test
    void shouldPassExistingFolderAndDescendantOptionToRepository() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.findById(folderId)).thenReturn(Optional.of(new Folder()));
        when(searchRepository.searchInFolderAndDescendants("alpha", folderId)).thenReturn(List.of());

        List<PageResponse> results = searchService.search("alpha", folderId, true);

        assertEquals(List.of(), results);
        verify(searchRepository).searchInFolderAndDescendants("alpha", folderId);
    }

    @Test
    void shouldIgnoreDescendantOptionWhenFolderIsNotProvided() {
        when(searchRepository.searchGlobally("alpha")).thenReturn(List.of());

        List<PageResponse> results = searchService.search("alpha", null, true);

        assertEquals(List.of(), results);
        verify(folderRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(searchRepository).searchGlobally("alpha");
    }

    private Page page(UUID folderId) {
        Page page = new Page();
        page.setId(pageId);
        page.setTitle("Alpha title");
        page.setSlug("alpha-page-" + pageId);
        page.setContent("# alpha content");
        page.setVersion(1);
        page.setFolderId(folderId);
        page.setCreatedBy(userId);
        page.setUpdatedBy(null);
        page.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        page.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return page;
    }
}
