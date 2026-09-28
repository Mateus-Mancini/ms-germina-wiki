package com.wikigerminare.pages;

import com.wikigerminare.pages.dto.CreatePageRequest;
import com.wikigerminare.pages.dto.PageResponse;
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

    @PostMapping
    public ResponseEntity<PageResponse> create(
            @Valid @RequestBody CreatePageRequest request,
            Principal principal
    ) {
        UUID createdBy = UUID.fromString(principal.getName());

        PageResponse response = pageService.create(
                request,
                createdBy
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PageResponse> getById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                pageService.getById(id)
        );
    }

    @GetMapping
    public ResponseEntity<List<PageResponse>> list() {
        return ResponseEntity.ok(
                pageService.list()
        );
    }
}