package com.wikigerminare.comments;

import com.wikigerminare.auth.security.JwtConfiguration;
import com.wikigerminare.config.SecurityConfig;
import com.wikigerminare.config.ApiExceptionHandler;
import com.wikigerminare.controller.CommentController;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, JwtConfiguration.class, ApiExceptionHandler.class, CommentsSecurityTest.WebSecurityConfiguration.class})
class CommentsSecurityTest {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class WebSecurityConfiguration {}
    @Autowired MockMvc mvc;
    @MockitoBean CommentService service;
    @MockitoBean AuthenticatedUserProvider provider;

    @Test void anonymousListRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/comments").param("pageId", UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
    }
    @Test void anonymousReadRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/comments/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
    @Test void anonymousWritesAreDenied() throws Exception {
        mvc.perform(post("/api/comments").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/comments/{id}", UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/comments/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/comments/{id}/admin-replies", UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void invalidBearerIsDenied() throws Exception {
        mvc.perform(get("/api/comments").param("pageId", UUID.randomUUID().toString()).header("Authorization", "Bearer malformed")).andExpect(status().isUnauthorized());
    }
}
