package com.wikigerminare.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wikigerminare.dto.comment.AdminReplyResponse;
import com.wikigerminare.dto.comment.CommentPageResponse;
import com.wikigerminare.dto.comment.CommentResponse;
import com.wikigerminare.dto.comment.CreateAdminReplyRequest;
import com.wikigerminare.dto.comment.CreateCommentRequest;
import com.wikigerminare.dto.comment.UpdateCommentRequest;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;
    private final AuthenticatedUserProvider userProvider;

    public CommentController(CommentService commentService, AuthenticatedUserProvider userProvider) {
        this.commentService = commentService;
        this.userProvider = userProvider;
    }

    @GetMapping
    @Operation(summary = "Lista comentários ativos por conteúdo e anchor")
    public CommentPageResponse list(@RequestParam UUID pageId,
                                    @RequestParam(required = false) UUID blockId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return commentService.list(pageId, blockId, page, size);
    }

    @PostMapping
    @Operation(summary = "Cria um comentário ancorado")
    public ResponseEntity<CommentResponse> create(@RequestBody CreateCommentRequest request) {
        var comment = commentService.create(request);
        return ResponseEntity.created(URI.create("/api/comments/" + comment.id()))
                .body(comment);
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Consulta um comentário ativo")
    public CommentResponse get(@PathVariable UUID commentId) {
        return commentService.get(commentId);
    }

    @PatchMapping("/{commentId}")
    @Operation(summary = "Edita o comentário do próprio autor")
    public CommentResponse update(@PathVariable UUID commentId, @RequestBody UpdateCommentRequest request) {
        return commentService.update(commentId, request);
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Remove logicamente um comentário")
    public ResponseEntity<Void> remove(@PathVariable UUID commentId) {
        commentService.remove(commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/admin-replies")
    @Operation(summary = "Cria uma resposta administrativa")
    public ResponseEntity<AdminReplyResponse> reply(@PathVariable UUID commentId,
                                                     @RequestBody CreateAdminReplyRequest request) {
        var reply = commentService.reply(commentId, request);
        return ResponseEntity.created(URI.create("/api/comments/" + commentId + "/admin-replies/" + reply.id()))
                .body(reply);
    }
}
