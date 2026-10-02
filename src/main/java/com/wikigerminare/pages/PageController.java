package com.wikigerminare.pages;

import com.wikigerminare.security.AdminOnly;

import com.wikigerminare.pages.dto.CreatePageRequest;
import com.wikigerminare.pages.dto.PageResponse;
import com.wikigerminare.pages.dto.UpdatePageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<PageResponse> create(
            @Valid @RequestBody CreatePageRequest request,
            Principal principal
    ) {

        if (principal == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        UUID createdBy;

        try {
            createdBy = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .build();
        }

        PageResponse response = pageService.create(
                request,
                createdBy
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .eTag(pageService.createEtag(response))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PageResponse> getById(
            @PathVariable UUID id
    ) {

        PageResponse response = pageService.getById(id);

        return ResponseEntity
                .ok()
                .eTag(pageService.createEtag(response))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<PageResponse>> list() {

        return ResponseEntity.ok(
                pageService.list()
        );
    }

    @AdminOnly
    @PatchMapping("/{id}")
    public ResponseEntity<PageResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false)
            String ifMatch,
            @Valid @RequestBody UpdatePageRequest request,
            Principal principal
    ) {

        if (principal == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        UUID updatedBy;

        try {
            updatedBy = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .build();
        }

        PageResponse response = pageService.update(
                id,
                request,
                ifMatch,
                updatedBy
        );

        return ResponseEntity
                .ok()
                .eTag(pageService.createEtag(response))
                .body(response);
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id
    ) {

        pageService.delete(id);

        return ResponseEntity
                .noContent()
                .build();
    }
}