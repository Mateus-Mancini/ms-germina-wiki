package com.wikigerminare.pages;

import com.wikigerminare.folders.FolderNotFoundException;
import com.wikigerminare.folders.FolderRepository;
import com.wikigerminare.pages.dto.CreatePageRequest;
import com.wikigerminare.pages.dto.PageResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PageService {

    private final PageRepository pageRepository;
    private final FolderRepository folderRepository;

    public PageService(
            PageRepository pageRepository,
            FolderRepository folderRepository
    ) {
        this.pageRepository = pageRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional
    public PageResponse create(
            CreatePageRequest request,
            UUID createdBy
    ) {

        /*
         * A criação de uma página exige que a pasta exista.
         * Não armazenamos a entidade Folder dentro de Page.
         * Apenas guardamos o UUID da pasta em folderId.
         */
        folderRepository.findById(request.folderId())
                .orElseThrow(() ->
                        new FolderNotFoundException(request.folderId()));

        Instant now = Instant.now();

        Page page = new Page();

        page.setTitle(request.title());
        page.setSlug(request.slug());
        page.setContent(request.content());
        page.setFolderId(request.folderId());
        page.setCreatedBy(createdBy);
        page.setCreatedAt(now);
        page.setUpdatedAt(now);

        /*
         * Não definimos version manualmente.
         *
         * O Hibernate/JPA controla o campo @Version.
         */
        try {

            Page savedPage = pageRepository.save(page);

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

    private boolean isUniqueViolation(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof java.sql.SQLException sqlException) {
                return "23505".equals(sqlException.getSQLState());
            }

            current = current.getCause();
        }

        return false;
    }
}