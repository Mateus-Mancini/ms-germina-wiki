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
import org.springframework.boot.test.context.SpringBootTest;
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

/** Regression coverage: real repository, service transactions and anchor validator; PostgreSQL 18 throwaway container. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@Import({com.wikigerminare.TestcontainersConfiguration.class, CommentsPostgresIntegrationTest.IdentityConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentsPostgresIntegrationTest {
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
        @Bean @org.springframework.context.annotation.Primary AuthenticatedUserProvider identityProvider() { return () -> identity; }
    }

    @BeforeEach void setup() {
        identity = new AuthenticatedUser(AUTHOR, false); jdbc.update("INSERT INTO users (id,name,email,password_hash,role) VALUES (?,?,?,?,?::user_role) ON CONFLICT (id) DO NOTHING", AUTHOR,"Audit Author","author-audit@example.invalid","unused","member"); jdbc.update("INSERT INTO users (id,name,email,password_hash,role) VALUES (?,?,?,?,?::user_role) ON CONFLICT (id) DO NOTHING", ADMIN,"Audit Admin","admin-audit@example.invalid","unused","admin");
        pageId = UUID.randomUUID();
        blockId = UUID.randomUUID();
        jdbc.update("INSERT INTO pages (id,title,slug,content,version,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                pageId, "Audit", pageId.toString(), "<!--b:" + blockId + "-->paragraph", 1, AUTHOR,
                java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
        mvc = MockMvcBuilders.standaloneSetup(new CommentController(service, provider))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    UUID seed() {
        return service.create(new com.wikigerminare.dto.comment.CreateCommentRequest(pageId,
                new com.wikigerminare.dto.comment.AnchorRequest(blockId), "question")).id();
    }
    UUID seedReply(UUID parentId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO comments (id,page_id,user_id,parent_comment_id,block_id,content,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?::comment_status,?,?)",
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
        mvc.perform(patch("/api/comments/{id}", replyId).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"changed\"}")).andExpect(status().is4xxClientError());
        assertEquals("answer", repository.findById(replyId).orElseThrow().getContent());
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

    @Test void listLoadsRepliesWithBoundedQueries() {
        for (int i = 0; i < 21; i++) seed();
        var stats = entityManagerFactory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getStatistics();
        stats.clear();
        var page = service.list(pageId, null, 0, 20);
        assertEquals(20, page.items().size());
        assertEquals(21, page.totalItems());
        // Root select + count + one reply fetch; page existence uses JdbcTemplate.
        assertEquals(3, stats.getPrepareStatementCount());
    }

    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Test @org.junit.jupiter.api.Timeout(18)
    void blockedCreationReturnsStorageErrorBeforeLambdaDeadline() throws Exception {
        // A row lock on the author blocks the FK check of a new comment.
        try (var lock = jdbc.getDataSource().getConnection()) {
            lock.setAutoCommit(false);
            try (var statement = lock.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, AUTHOR);
                statement.executeQuery().close();
            }
            try {
                long start = System.nanoTime();
                mvc.perform(post("/api/comments").contentType(MediaType.APPLICATION_JSON).content(createBody("blocked")))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("COMMENT_STORAGE_UNAVAILABLE"));
                assertTrue(java.time.Duration.ofNanos(System.nanoTime() - start).toSeconds() < 15);
            } finally {
                lock.rollback();
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM comments WHERE page_id = ?", Long.class, pageId));
    }
}
