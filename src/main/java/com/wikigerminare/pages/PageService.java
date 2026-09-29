package com.wikigerminare.pages;

import com.wikigerminare.folders.FolderNotFoundException;
import com.wikigerminare.folders.FolderRepository;
import com.wikigerminare.pages.dto.CreatePageRequest;
import com.wikigerminare.pages.dto.PageResponse;
import com.wikigerminare.pages.dto.UpdatePageRequest;
import com.wikigerminare.pages.wikilinks.WikiLinkService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PageService {

    private final PageRepository pageRepository;
    private final FolderRepository folderRepository;
    private final WikiLinkService wikiLinkService;

    public PageService(
            PageRepository pageRepository,
            FolderRepository folderRepository,
            WikiLinkService wikiLinkService
    ) {
        this.pageRepository = pageRepository;
        this.folderRepository = folderRepository;
        this.wikiLinkService = wikiLinkService;
    }

    @Transactional
    public PageResponse create(
            CreatePageRequest request,
            UUID createdBy
    ) {

        folderRepository.findById(request.folderId())
                .orElseThrow(() ->
                        new FolderNotFoundException(
                                request.folderId()));

        Instant now = Instant.now();

        Page page = new Page();

        page.setId(UUID.randomUUID());
        page.setTitle(request.title());
        page.setSlug(request.slug());
        page.setContent(request.content());
        page.setVersion(1);
        page.setFolderId(request.folderId());
        page.setCreatedBy(createdBy);
        page.setUpdatedBy(null);
        page.setCreatedAt(now);
        page.setUpdatedAt(now);

        try {

            Page savedPage = pageRepository.save(page);

            /*
             * Depois que a página foi persistida, seus WikiLinks
             * podem ser resolvidos contra as páginas existentes.
             *
             * O conteúdo Markdown original permanece inalterado.
             */
            wikiLinkService.reconcile(
                    savedPage.getId(),
                    savedPage.getContent()
            );

            /*
             * A página recém-criada também pode ser o destino
             * de WikiLinks que anteriormente apontavam para um
             * slug inexistente.
             */
            wikiLinkService.resolvePendingLinks(
                    savedPage.getSlug(),
                    savedPage.getId()
            );

            return toResponse(savedPage);

        } catch (DataIntegrityViolationException exception) {

            if (isUniqueViolation(exception)) {

                throw new PageConflictException(
                        "A page with this slug already exists"
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse getById(UUID id) {

        Page page = pageRepository.findById(id)
                .orElseThrow(() ->
                        new PageNotFoundException(id));

        return toResponse(page);
    }

    @Transactional(readOnly = true)
    public List<PageResponse> list() {

        return pageRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PageResponse update(
            UUID id,
            UpdatePageRequest request,
            String ifMatch,
            UUID updatedBy
    ) {

        if (ifMatch == null || ifMatch.isBlank()) {

            throw new PagePreconditionRequiredException(
                    "If-Match header is required"
            );
        }

        Page page = pageRepository.findById(id)
                .orElseThrow(() ->
                        new PageNotFoundException(id));

        int expectedVersion = parseEtag(
                ifMatch,
                id
        );

        if (expectedVersion != page.getVersion()) {

            throw new PagePreconditionFailedException(
                    id,
                    page.getVersion()
            );
        }

        if (!request.isTitleProvided()
                && !request.isContentProvided()) {

            throw new PageValidationException(
                    "At least one field must be provided for update"
            );
        }

        if (request.isTitleProvided()) {

            if (request.getTitle() == null
                    || request.getTitle().isBlank()) {

                throw new PageValidationException(
                        "title must not be blank"
                );
            }

            page.setTitle(request.getTitle());
        }

        if (request.isContentProvided()) {

            if (request.getContent() == null) {

                throw new PageValidationException(
                        "content must not be null"
                );
            }

            page.setContent(request.getContent());
        }

        page.setUpdatedBy(updatedBy);
        page.setUpdatedAt(Instant.now());

        try {

            Page savedPage = pageRepository.saveAndFlush(page);

            return toResponse(savedPage);

        } catch (ObjectOptimisticLockingFailureException exception) {

            throw new PagePreconditionFailedException(
                    id,
                    findCurrentVersion(id)
            );

        } catch (DataIntegrityViolationException exception) {

            if (isUniqueViolation(exception)) {

                throw new PageConflictException(
                        "A page with this slug already exists"
                );
            }

            throw exception;
        }
    }

    @Transactional
    public void delete(UUID id) {

        Page page = pageRepository.findById(id)
                .orElseThrow(() ->
                        new PageNotFoundException(id));

        try {

            pageRepository.delete(page);
            pageRepository.flush();

        } catch (DataIntegrityViolationException exception) {

            throw new PageConflictException(
                    "Page cannot be deleted because it is referenced"
            );
        }
    }

    @Transactional(readOnly = true)
    public int findCurrentVersion(UUID id) {

        return pageRepository.findById(id)
                .map(Page::getVersion)
                .orElseThrow(() ->
                        new PageNotFoundException(id));
    }

    public String createEtag(PageResponse response) {

        return "\"" +
                response.id() +
                "-v" +
                response.version() +
                "\"";
    }

    private int parseEtag(
            String ifMatch,
            UUID pageId
    ) {

        String expectedPrefix = "\""
                + pageId
                + "-v";

        if (!ifMatch.startsWith(expectedPrefix)
                || !ifMatch.endsWith("\"")) {

            throw new PageBadRequestException(
                    "Invalid If-Match value"
            );
        }

        String versionText = ifMatch.substring(
                expectedPrefix.length(),
                ifMatch.length() - 1
        );

        try {

            return Integer.parseInt(versionText);

        } catch (NumberFormatException exception) {

            throw new PageBadRequestException(
                    "Invalid If-Match value"
            );
        }
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

    private boolean isUniqueViolation(
            Throwable exception
    ) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof java.sql.SQLException sqlException) {

                return "23505".equals(
                        sqlException.getSQLState()
                );
            }

            current = current.getCause();
        }

        return false;
    }
}