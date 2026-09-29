package com.wikigerminare.pages.wikilinks;

import com.wikigerminare.pages.PageNotFoundException;
import com.wikigerminare.pages.wikilinks.dto.LinkedPageSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WikiLinkController.class)
class WikiLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WikiLinkService wikiLinkService;

    @Test
    void shouldReturnBacklinks() throws Exception {

        UUID targetPageId = UUID.randomUUID();
        UUID sourcePageId = UUID.randomUUID();

        LinkedPageSummary source =
                new LinkedPageSummary(
                        sourcePageId,
                        "Source Page",
                        "source-page"
                );

        when(wikiLinkService.getBacklinks(targetPageId))
                .thenReturn(List.of(source));

        mockMvc.perform(
                        get("/api/pages/{pageId}/backlinks", targetPageId)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/json"
                ))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(sourcePageId.toString()))
                .andExpect(jsonPath("$[0].title")
                        .value("Source Page"))
                .andExpect(jsonPath("$[0].slug")
                        .value("source-page"));
    }

    @Test
    void shouldReturnEmptyArrayWhenThereAreNoBacklinks()
            throws Exception {

        UUID targetPageId = UUID.randomUUID();

        when(wikiLinkService.getBacklinks(targetPageId))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/pages/{pageId}/backlinks", targetPageId)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/json"
                ))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnBadRequestForMalformedUuid()
            throws Exception {

        mockMvc.perform(
                        get("/api/pages/not-a-uuid/backlinks")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenTargetPageDoesNotExist()
            throws Exception {

        UUID targetPageId = UUID.randomUUID();

        when(wikiLinkService.getBacklinks(targetPageId))
                .thenThrow(
                        new PageNotFoundException(targetPageId)
                );

        mockMvc.perform(
                        get("/api/pages/{pageId}/backlinks", targetPageId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnOneSummaryPerSource()
            throws Exception {

        UUID targetPageId = UUID.randomUUID();
        UUID sourcePageId = UUID.randomUUID();

        LinkedPageSummary source =
                new LinkedPageSummary(
                        sourcePageId,
                        "Source Page",
                        "source-page"
                );

        when(wikiLinkService.getBacklinks(targetPageId))
                .thenReturn(List.of(source));

        mockMvc.perform(
                        get("/api/pages/{pageId}/backlinks", targetPageId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(sourcePageId.toString()));
    }
}