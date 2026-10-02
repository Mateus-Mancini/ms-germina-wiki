package com.wikigerminare.comments;

import com.wikigerminare.config.ApiExceptionHandler;
import com.wikigerminare.config.DatabaseContentAnchorValidator;
import com.wikigerminare.controller.CommentController;
import com.wikigerminare.entity.comment.Comment;
import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.repository.comment.CommentRepository;
import com.wikigerminare.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression coverage: real repository, service transactions and anchor validator; H2 only. */
@DataJpaTest(properties = {
    "spring.sql.init.schema-locations=classpath:h2-comment-types.sql",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
    "spring.jpa.open-in-view=false", "spring.jpa.show-sql=false"
})
@Import({CommentService.class, DatabaseContentAnchorValidator.class, CommentsIntegrationTest.IdentityConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentsIntegrationTest {
    static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static AuthenticatedUser identity;
    @Autowired CommentService service;
    @Autowired CommentRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthenticatedUserProvider provider;
    MockMvc mvc;
    UUID pageId;
    UUID blockId;

    @TestConfiguration
    static class IdentityConfig {
        @Bean AuthenticatedUserProvider identityProvider() { return () -> identity; }
    }

    @BeforeEach void setup() {
        identity = new AuthenticatedUser(AUTHOR, false);
        pageId = UUID.randomUUID();
        blockId = UUID.randomUUID();
        jdbc.update("INSERT INTO pages (id,title,slug,content,version,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                pageId, "Audit", pageId.toString(), "<!--b:" + blockId + "-->paragraph", 1, AUTHOR,
                java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
        mvc = MockMvcBuilders.standaloneSetup(new CommentController(service, provider))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    UUID seed() { return repository.save(new Comment(UUID.randomUUID(), pageId, AUTHOR, blockId, "question", Instant.now())).getId(); }
    UUID seedReply(UUID parentId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO comments (id,page_id,user_id,parent_comment_id,block_id,content,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id,pageId,ADMIN,parentId,blockId,"answer","OPEN",java.sql.Timestamp.from(Instant.now()),java.sql.Timestamp.from(Instant.now()));
        return id;
    }
    String createBody(String text) {
        return "{\"pageId\":\"" + pageId + "\",\"anchor\":{\"blockId\":\"" + blockId + "\"},\"text\":\"" + text + "\"}";
    }

    @Test void validCreationReturns201AndTrimsText() throws Exception {
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("  question  ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("question"));
    }
    @Test void readPersistedCommentReturns200() throws Exception {
        mvc.perform(get("/api/comments/{id}", seed())).andExpect(status().isOk());
    }
    @Test void listPersistedCommentReturns200() throws Exception {
        seed();
        mvc.perform(get("/api/comments").param("pageId", pageId.toString())).andExpect(status().isOk());
    }
    @Test void editPersistedCommentReturns200() throws Exception {
        UUID id = seed();
        var result = mvc.perform(patch("/api/comments/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"edited\"}")).andReturn();
        assertEquals("edited", repository.findById(id).orElseThrow().getContent(), "Check whether an error response still committed the write");
        assertEquals(200, result.getResponse().getStatus());
    }
    @Test void authorDeleteIsIdempotentAndHidesRoot() throws Exception {
        UUID id = seed();
        mvc.perform(delete("/api/comments/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/comments/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/comments/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/comments").param("pageId", pageId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }
    @Test void anotherMemberCannotEditOrDelete() throws Exception {
        UUID id = seed();
        identity = new AuthenticatedUser(UUID.randomUUID(), false);
        mvc.perform(patch("/api/comments/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"edited\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/{id}", id)).andExpect(status().isForbidden());
        assertEquals("question", repository.findById(id).orElseThrow().getContent());
    }
    @Test void regularMemberCannotReply() throws Exception {
        mvc.perform(post("/api/comments/{id}/admin-replies", seed()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"answer\"}")).andExpect(status().isForbidden());
    }
    @Test void adminCanReplyAndModerate() throws Exception {
        UUID id = seed();
        identity = new AuthenticatedUser(ADMIN, true);
        mvc.perform(post("/api/comments/{id}/admin-replies", id).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"answer\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/comments/{id}", id)).andExpect(status().isNoContent());
    }
    @Test void invalidAnchorDoesNotPersist() throws Exception {
        long count = repository.count();
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("question").replace(blockId.toString(), UUID.randomUUID().toString())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANCHOR_INVALID"));
        assertEquals(count, repository.count());
    }
    @Test void blankAndOversizedTextsAreRejectedAndBoundaryIsAccepted() throws Exception {
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("   "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("x".repeat(2001)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("x".repeat(2000)))).andExpect(status().isCreated());
    }
    @Test void invalidPaginationLimitsReturn400() throws Exception {
        for (String size : new String[]{"0", "101"}) mvc.perform(get("/api/comments").param("pageId", pageId.toString()).param("size", size)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/comments").param("pageId", pageId.toString()).param("page", "-1")).andExpect(status().isBadRequest());
    }
    @Test void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/comments/not-a-uuid")).andExpect(status().isBadRequest());
    }
    @Test void missingPageIdReturns400() throws Exception {
        mvc.perform(get("/api/comments")).andExpect(status().isBadRequest());
    }
    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest());
    }
    @Test void documentedCreationBodyIsAccepted() throws Exception {
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("question")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pageId").value(pageId.toString()))
                .andExpect(jsonPath("$.userId").value(AUTHOR.toString()))
                .andExpect(jsonPath("$.anchor.blockId").value(blockId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }
    @Test void replyingToReplyIsRejected() throws Exception {
        UUID rootId = seed();
        identity = new AuthenticatedUser(ADMIN, true);
        UUID replyId = seedReply(rootId);
        mvc.perform(post("/api/comments/{id}/admin-replies", replyId).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"nested\"}")).andExpect(status().is4xxClientError());
    }
    @Test void adminReplyCannotBeEdited() throws Exception {
        UUID rootId = seed();
        identity = new AuthenticatedUser(ADMIN, true);
        UUID replyId = seedReply(rootId);
        var result = mvc.perform(patch("/api/comments/{id}", replyId).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"changed\"}")).andReturn();
        assertAll(
                () -> assertTrue(result.getResponse().getStatus() >= 400 && result.getResponse().getStatus() < 500, "Reply edits should be refused"),
                () -> assertEquals("answer", repository.findById(replyId).orElseThrow().getContent(), "Administrative replies must remain immutable"));
    }
    @Test void removedParentMakesChildInactive() throws Exception {
        UUID rootId = seed();
        identity = new AuthenticatedUser(ADMIN, true);
        UUID replyId = seedReply(rootId);
        service.remove(rootId);
        assertThrows(com.wikigerminare.service.CommentException.class, () -> service.get(replyId));
    }
    @Test void adminCanModerateAnotherAuthorsRoot() throws Exception {
        UUID rootId = seed();
        identity = new AuthenticatedUser(ADMIN, true);
        mvc.perform(delete("/api/comments/{id}", rootId)).andExpect(status().isNoContent());
    }
    @Test void serviceReturnsMaterializedResponseAfterItsTransaction() {
        var comment = service.get(seed());
        assertDoesNotThrow(() -> comment.adminReplies());
    }
    @Test void adminReplyCanBeCreatedWithinRealTransaction() {
        UUID rootId = seed();
        identity = new AuthenticatedUser(ADMIN, true);
        assertDoesNotThrow(() -> service.reply(rootId, new com.wikigerminare.dto.comment.CreateAdminReplyRequest("answer")));
    }

    @Test void repliesAreReturnedAfterCommitAndPaginationKeepsRoots() throws Exception {
        UUID rootId = seed();
        seed();
        identity = new AuthenticatedUser(ADMIN, true);
        var first = service.reply(rootId, new com.wikigerminare.dto.comment.CreateAdminReplyRequest("first"));
        var second = service.reply(rootId, new com.wikigerminare.dto.comment.CreateAdminReplyRequest("second"));
        var response = service.get(rootId);
        assertEquals(java.util.List.of(first.id(), second.id()), response.adminReplies().stream().map(r -> r.id()).toList());
        var page = service.list(pageId, blockId, 0, 1);
        assertEquals(2, page.totalItems());
        assertEquals(1, page.items().size());
        assertEquals(2, page.totalPages());
        mvc.perform(get("/api/comments/{id}", rootId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.adminReplies.length()").value(2));
        service.remove(rootId);
        mvc.perform(get("/api/comments/{id}", first.id())).andExpect(status().isNotFound());
    }

    @Test void malformedQueryAndRequestUuidReturn400() throws Exception {
        mvc.perform(get("/api/comments").param("pageId", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/comments").param("pageId", pageId.toString()).param("size", "bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON)
                .content(createBody("question").replace(pageId.toString(), "bad")))
                .andExpect(status().isBadRequest());
    }
}
