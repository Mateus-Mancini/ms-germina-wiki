package com.wikigerminare.controller;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikigerminare.config.ApiExceptionHandler;
import com.wikigerminare.dto.comment.CreateAdminReplyRequest;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentException;
import com.wikigerminare.service.CommentService;

class AdminCommentControllerTest {
    private MockMvc mockMvc;
    private CommentService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = mock(CommentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(service, mock(AuthenticatedUserProvider.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void createsAdminReply() throws Exception {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment(commentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "question", Instant.now());
        Comment reply = Comment.reply(UUID.randomUUID(), comment, UUID.randomUUID(), "answer", Instant.now());
        when(service.reply(eq(commentId), any(CreateAdminReplyRequest.class))).thenReturn(com.wikigerminare.dto.comment.AdminReplyResponse.from(reply));

        mockMvc.perform(post("/api/comments/{id}/admin-replies", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAdminReplyRequest("answer"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").value(commentId.toString()))
                .andExpect(jsonPath("$.text").value("answer"));
    }

    @Test
    void mapsForbiddenAdminReplyToForbidden() throws Exception {
        UUID commentId = UUID.randomUUID();
        when(service.reply(eq(commentId), any(CreateAdminReplyRequest.class)))
                .thenThrow(new CommentException("FORBIDDEN", "Administrator permission is required"));

        mockMvc.perform(post("/api/comments/{id}/admin-replies", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"answer\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
