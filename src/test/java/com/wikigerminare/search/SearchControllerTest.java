package com.wikigerminare.search;

import com.wikigerminare.folders.FolderNotFoundException;
import com.wikigerminare.pages.PageExceptionHandler;
import com.wikigerminare.pages.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import({SearchExceptionHandler.class, PageExceptionHandler.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @Test
    void shouldReturnPageResultsForQuery() throws Exception {
        UUID pageId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        PageResponse page = new PageResponse(
                pageId,
                "Searchable title",
                "searchable-title-" + pageId,
                "Raw Markdown content",
                1,
                null,
                creatorId,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(searchService.search("alpha", null, false))
                .thenReturn(List.of(page));

        mockMvc.perform(get("/api/search").param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pageId.toString()))
                .andExpect(jsonPath("$[0].title").value("Searchable title"))
                .andExpect(jsonPath("$[0].slug").value("searchable-title-" + pageId))
                .andExpect(jsonPath("$[0].content").value("Raw Markdown content"))
                .andExpect(jsonPath("$[0].folderId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[0].version").value(1));

        verify(searchService).search("alpha", null, false);
    }

    @Test
    void shouldReturnEmptyArrayWhenThereAreNoMatches() throws Exception {
        when(searchService.search("absent", null, false))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/search").param("q", "absent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnBadRequestWhenQueryIsMissing() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(searchService);
    }

    @Test
    void shouldReturnBadRequestWhenQueryIsBlank() throws Exception {
        when(searchService.search("   ", null, false))
                .thenThrow(new SearchValidationException("q must not be blank"));

        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("q must not be blank"));
    }

    @Test
    void shouldReturnBadRequestForMalformedFolderId() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "alpha")
                        .param("folderId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(searchService);
    }

    @Test
    void shouldReturnBadRequestForInvalidIncludeSubfolders() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "alpha")
                        .param("includeSubfolders", "sometimes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(searchService);
    }

    @Test
    void shouldDefaultIncludeSubfoldersToFalse() throws Exception {
        when(searchService.search("alpha", null, false))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/search").param("q", "alpha"))
                .andExpect(status().isOk());

        verify(searchService).search("alpha", null, false);
    }

    @Test
    void shouldDefaultIncludeSubfoldersToFalseForFolderSearch() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(searchService.search("alpha", folderId, false))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/search")
                        .param("q", "alpha")
                        .param("folderId", folderId.toString()))
                .andExpect(status().isOk());

        verify(searchService).search("alpha", folderId, false);
    }

    @Test
    void shouldPassTrueForIncludeSubfolders() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(searchService.search("alpha", folderId, true))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/search")
                        .param("q", "alpha")
                        .param("folderId", folderId.toString())
                        .param("includeSubfolders", "true"))
                .andExpect(status().isOk());

        verify(searchService).search("alpha", folderId, true);
    }

    @Test
    void shouldReturnNotFoundWhenFolderDoesNotExist() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(searchService.search("alpha", folderId, false))
                .thenThrow(new FolderNotFoundException(folderId));

        mockMvc.perform(get("/api/search")
                        .param("q", "alpha")
                        .param("folderId", folderId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        verify(searchService).search(eq("alpha"), eq(folderId), eq(false));
    }
}
