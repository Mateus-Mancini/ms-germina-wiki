package com.wikigerminare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikigerminare.config.ApiExceptionHandler;
import com.wikigerminare.dto.comment.AnchorRequest;
import com.wikigerminare.dto.comment.CreateCommentRequest;
import com.wikigerminare.dto.comment.UpdateCommentRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentException;
import com.wikigerminare.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerTest {
    private MockMvc mockMvc;
    private CommentService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = mock(CommentService.class);
        AuthenticatedUserProvider provider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(service, provider))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void createsCommentAndReturnsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        Comment comment = new Comment(id, UUID.randomUUID(), UUID.randomUUID(), "text",
                "paragraph", "intro", "1", Instant.now());
        when(service.create(any(CreateCommentRequest.class))).thenReturn(comment);

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest(
                                comment.getContentId(), new AnchorRequest("paragraph", "intro", "1"), "text"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/comments/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void mapsValidationErrorToBadRequest() throws Exception {
        when(service.update(any(UUID.class), any(UpdateCommentRequest.class)))
                .thenThrow(new CommentException("VALIDATION_ERROR", "text is required"));

        mockMvc.perform(patch("/api/comments/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void removesCommentWithNoContent() throws Exception {
        mockMvc.perform(delete("/api/comments/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
