package com.wikigerminare.pages.wikilinks;

import com.wikigerminare.pages.wikilinks.dto.LinkedPageSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pages")
public class WikiLinkController {

    private final WikiLinkService wikiLinkService;

    public WikiLinkController(
            WikiLinkService wikiLinkService
    ) {
        this.wikiLinkService = wikiLinkService;
    }

    /**
     * Retorna as páginas apontadas pelos WikiLinks
     * de uma página de origem.
     */
    @GetMapping("/{pageId}/wikilinks")
    public ResponseEntity<List<LinkedPageSummary>> getOutgoingLinks(
            @PathVariable UUID pageId
    ) {

        return ResponseEntity.ok(
                wikiLinkService.getOutgoingLinks(pageId)
        );
    }

    /**
     * Retorna as páginas que possuem WikiLinks
     * apontando para a página informada.
     */
    @GetMapping("/{pageId}/backlinks")
    public ResponseEntity<List<LinkedPageSummary>> getBacklinks(
            @PathVariable UUID pageId
    ) {

        return ResponseEntity.ok(
                wikiLinkService.getBacklinks(pageId)
        );
    }
}