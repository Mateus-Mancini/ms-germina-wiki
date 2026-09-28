package com.wikigerminare.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.wikigerminare.config.ApiExceptionHandler;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentException;
import com.wikigerminare.service.CommentService;

class CommentQueryControllerTest {
    private MockMvc mockMvc;
    private CommentService service;

    @BeforeEach
    void setUp() {
        service = mock(CommentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(service, mock(AuthenticatedUserProvider.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsPaginatedCommentsForAnchor() throws Exception {
        UUID contentId = UUID.randomUUID();
        Comment comment = new Comment(UUID.randomUUID(), contentId, UUID.randomUUID(), "text",
                "paragraph", "intro", "1", Instant.now());
        when(service.list(eq(contentId), eq("paragraph"), eq("intro"), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/comments")
                        .param("contentId", contentId.toString())
                        .param("anchorType", "paragraph")
                        .param("anchorValue", "intro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].text").value("text"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void mapsUnavailableContentToConflict() throws Exception {
        UUID contentId = UUID.randomUUID();
        when(service.list(eq(contentId), eq(null), eq(null), anyInt(), anyInt()))
                .thenThrow(new CommentException("CONTENT_UNAVAILABLE", "Content unavailable"));

        mockMvc.perform(get("/api/comments").param("contentId", contentId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_UNAVAILABLE"));
    }
}
