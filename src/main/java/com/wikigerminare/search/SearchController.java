package com.wikigerminare.search;

import com.wikigerminare.pages.dto.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<List<PageResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(defaultValue = "false") boolean includeSubfolders
    ) {
        return ResponseEntity.ok(
                searchService.search(q, folderId, includeSubfolders));
    }
}
