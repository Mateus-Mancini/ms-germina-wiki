package com.wikigerminare.search;

import com.wikigerminare.folders.FolderNotFoundException;
import com.wikigerminare.folders.FolderRepository;
import com.wikigerminare.pages.Page;
import com.wikigerminare.pages.dto.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private final SearchRepository searchRepository;
    private final FolderRepository folderRepository;

    public SearchService(
            SearchRepository searchRepository,
            FolderRepository folderRepository
    ) {
        this.searchRepository = searchRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public List<PageResponse> search(String queryText, UUID folderId, boolean includeSubfolders) {
        if (queryText == null || queryText.isBlank()) {
            throw new SearchValidationException("q must not be blank");
        }

        List<Page> pages;
        if (folderId == null) {
            pages = searchRepository.searchGlobally(queryText);
        } else {
            folderRepository.findById(folderId)
                    .orElseThrow(() -> new FolderNotFoundException(folderId));

            pages = includeSubfolders
                    ? searchRepository.searchInFolderAndDescendants(queryText, folderId)
                    : searchRepository.searchInFolder(queryText, folderId);
        }

        return pages.stream()
                .map(this::toResponse)
                .toList();
    }

    private PageResponse toResponse(Page page) {
        return new PageResponse(
                page.getId(),
                page.getTitle(),
                page.getSlug(),
                page.getContent(),
                page.getVersion(),
                page.getFolderId(),
                page.getCreatedBy(),
                page.getUpdatedBy(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }
}
