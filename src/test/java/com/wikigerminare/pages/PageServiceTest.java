package com.wikigerminare.pages;

import com.wikigerminare.folders.FolderNotFoundException;
import com.wikigerminare.folders.FolderRepository;
import com.wikigerminare.pages.dto.CreatePageRequest;
import com.wikigerminare.pages.dto.PageResponse;
import com.wikigerminare.pages.dto.UpdatePageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.wikigerminare.folders.Folder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PageServiceTest {

    @Mock
    private PageRepository pageRepository;

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private PageService pageService;

    private UUID pageId;
    private UUID folderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        pageId = UUID.randomUUID();
        folderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void shouldCreatePage() {

        CreatePageRequest request = new CreatePageRequest(
                "Getting started",
                "getting-started",
                "# Welcome\n\nRaw **Markdown**.",
                folderId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(mockFolder()));

        when(pageRepository.save(any(Page.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PageResponse response = pageService.create(
                request,
                userId);

        assertNotNull(response);
        assertEquals("Getting started", response.title());
        assertEquals("getting-started", response.slug());
        assertEquals(
                "# Welcome\n\nRaw **Markdown**.",
                response.content());
        assertEquals(folderId, response.folderId());
        assertEquals(userId, response.createdBy());
        assertEquals(1, response.version());

        verify(folderRepository).findById(folderId);
        verify(pageRepository).save(any(Page.class));
    }

    @Test
    void shouldThrowWhenFolderDoesNotExist() {

        CreatePageRequest request = new CreatePageRequest(
                "Getting started",
                "getting-started",
                "# Welcome",
                folderId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

        assertThrows(
                FolderNotFoundException.class,
                () -> pageService.create(request, userId));

        verify(pageRepository, never())
                .save(any(Page.class));
    }

    @Test
    void shouldThrowConflictWhenSlugAlreadyExists() {

        CreatePageRequest request = new CreatePageRequest(
                "Getting started",
                "getting-started",
                "# Welcome",
                folderId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(mockFolder()));

        SQLException sqlException = new SQLException(
                "duplicate key",
                "23505");

        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "Duplicate slug",
                sqlException);

        when(pageRepository.save(any(Page.class)))
                .thenThrow(exception);

        PageConflictException thrown = assertThrows(
                PageConflictException.class,
                () -> pageService.create(request, userId));

        assertEquals(
                "A page with this slug already exists",
                thrown.getMessage());
    }

    @Test
    void shouldGetPageById() {

        Page page = createPage();

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        PageResponse response = pageService.getById(pageId);

        assertNotNull(response);
        assertEquals(pageId, response.id());
        assertEquals("Getting started", response.title());
        assertEquals("getting-started", response.slug());
        assertEquals("# Welcome", response.content());
        assertEquals(1, response.version());
        assertEquals(folderId, response.folderId());
        assertEquals(userId, response.createdBy());

        verify(pageRepository).findById(pageId);
    }

    @Test
    void shouldThrowWhenPageDoesNotExist() {

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.empty());

        assertThrows(
                PageNotFoundException.class,
                () -> pageService.getById(pageId));
    }

    @Test
    void shouldListPages() {

        Page page1 = createPage();

        Page page2 = createPage();
        page2.setId(UUID.randomUUID());
        page2.setTitle("Another page");
        page2.setSlug("another-page");

        when(pageRepository.findAll())
                .thenReturn(List.of(page1, page2));

        List<PageResponse> response = pageService.list();

        assertEquals(2, response.size());

        assertEquals(
                "Getting started",
                response.get(0).title());

        assertEquals(
                "Another page",
                response.get(1).title());

        verify(pageRepository).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoPages() {

        when(pageRepository.findAll())
                .thenReturn(List.of());

        List<PageResponse> response = pageService.list();

        assertNotNull(response);
        assertTrue(response.isEmpty());

        verify(pageRepository).findAll();
    }

    @Test
    void shouldUpdatePageContentWithValidIfMatch() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent(
                "Updated **Markdown**.");

        String etag = "\"" + pageId + "-v1\"";

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        when(pageRepository.saveAndFlush(page))
                .thenAnswer(invocation -> {
                    page.setVersion(2);
                    return page;
                });

        PageResponse response = pageService.update(
                pageId,
                request,
                etag,
                userId);

        assertEquals(
                "Updated **Markdown**.",
                response.content());

        assertEquals(
                2,
                response.version());

        assertEquals(
                userId,
                response.updatedBy());

        verify(pageRepository)
                .saveAndFlush(page);
    }

    @Test
    void shouldUpdatePageTitleWithValidIfMatch() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        request.setTitle("Updated title");

        String etag = "\"" + pageId + "-v1\"";

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        when(pageRepository.saveAndFlush(page))
                .thenAnswer(invocation -> {
                    page.setVersion(2);
                    return page;
                });

        PageResponse response = pageService.update(
                pageId,
                request,
                etag,
                userId);

        assertEquals(
                "Updated title",
                response.title());

        assertEquals(
                2,
                response.version());

        assertEquals(
                userId,
                response.updatedBy());
    }

    @Test
    void shouldUpdateTitleAndContentTogether() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        request.setTitle("New title");
        request.setContent("New content");

        String etag = "\"" + pageId + "-v1\"";

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        when(pageRepository.saveAndFlush(page))
                .thenAnswer(invocation -> {
                    page.setVersion(2);
                    return page;
                });

        PageResponse response = pageService.update(
                pageId,
                request,
                etag,
                userId);

        assertEquals(
                "New title",
                response.title());

        assertEquals(
                "New content",
                response.content());
    }

    @Test
    void shouldThrow428WhenIfMatchIsMissing() {

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent("Updated content");

        PagePreconditionRequiredException exception = assertThrows(
                PagePreconditionRequiredException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        null,
                        userId));

        assertEquals(
                "If-Match header is required",
                exception.getMessage());

        verifyNoInteractions(pageRepository);
    }

    @Test
    void shouldThrow428WhenIfMatchIsBlank() {

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent("Updated content");

        assertThrows(
                PagePreconditionRequiredException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "   ",
                        userId));

        verifyNoInteractions(pageRepository);
    }

    @Test
    void shouldThrow400WhenIfMatchIsInvalid() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent("Updated content");

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        PageBadRequestException exception = assertThrows(
                PageBadRequestException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "invalid-etag",
                        userId));

        assertEquals(
                "Invalid If-Match value",
                exception.getMessage());

        verify(pageRepository, never())
                .saveAndFlush(any(Page.class));
    }

    @Test
    void shouldThrow412WhenIfMatchIsStale() {

        Page page = createPage();
        page.setVersion(2);

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent("Updated content");

        String oldEtag = "\"" + pageId + "-v1\"";

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        PagePreconditionFailedException exception = assertThrows(
                PagePreconditionFailedException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        oldEtag,
                        userId));

        assertEquals(
                pageId,
                exception.getPageId());

        assertEquals(
                2,
                exception.getCurrentVersion());

        verify(pageRepository, never())
                .saveAndFlush(any(Page.class));
    }

    @Test
    void shouldThrowWhenUpdatingUnknownPage() {

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent("Updated content");

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.empty());

        assertThrows(
                PageNotFoundException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "\"" + pageId + "-v1\"",
                        userId));

        verify(pageRepository, never())
                .saveAndFlush(any(Page.class));
    }

    @Test
    void shouldThrowWhenUpdateHasNoFields() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        PageValidationException exception = assertThrows(
                PageValidationException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "\"" + pageId + "-v1\"",
                        userId));

        assertEquals(
                "At least one field must be provided for update",
                exception.getMessage());

        verify(pageRepository, never())
                .saveAndFlush(any(Page.class));
    }

    @Test
    void shouldThrowWhenUpdatedTitleIsBlank() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        request.setTitle("   ");

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        PageValidationException exception = assertThrows(
                PageValidationException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "\"" + pageId + "-v1\"",
                        userId));

        assertEquals(
                "title must not be blank",
                exception.getMessage());

        verify(pageRepository, never())
                .saveAndFlush(any(Page.class));
    }

    @Test
    void shouldThrowWhenUpdatedContentIsNull() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest() {
            @Override
            public boolean isContentProvided() {
                return true;
            }

            @Override
            public String getContent() {
                return null;
            }
        };

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        PageValidationException exception = assertThrows(
                PageValidationException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "\"" + pageId + "-v1\"",
                        userId));

        assertEquals(
                "content must not be null",
                exception.getMessage());

        verify(pageRepository, never())
                .saveAndFlush(any(Page.class));
    }

    @Test
    void shouldThrow412WhenOptimisticLockFails() {

        Page page = createPage();

        UpdatePageRequest request = new UpdatePageRequest();

        request.setContent("Updated content");

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        when(pageRepository.saveAndFlush(page))
                .thenThrow(
                        new ObjectOptimisticLockingFailureException(
                                Page.class,
                                pageId));

        Page currentPage = createPage();
        currentPage.setVersion(2);

        when(pageRepository.findById(pageId))
                .thenReturn(
                        Optional.of(page),
                        Optional.of(currentPage));

        PagePreconditionFailedException exception = assertThrows(
                PagePreconditionFailedException.class,
                () -> pageService.update(
                        pageId,
                        request,
                        "\"" + pageId + "-v1\"",
                        userId));

        assertEquals(
                pageId,
                exception.getPageId());

        assertEquals(
                2,
                exception.getCurrentVersion());
    }

    @Test
    void shouldDeletePage() {

        Page page = createPage();

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        doNothing()
                .when(pageRepository)
                .delete(page);

        doNothing()
                .when(pageRepository)
                .flush();

        assertDoesNotThrow(
                () -> pageService.delete(pageId));

        verify(pageRepository).findById(pageId);
        verify(pageRepository).delete(page);
        verify(pageRepository).flush();
    }

    @Test
    void shouldThrowWhenDeletingUnknownPage() {

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.empty());

        assertThrows(
                PageNotFoundException.class,
                () -> pageService.delete(pageId));

        verify(pageRepository, never())
                .delete(any(Page.class));
    }

    @Test
    void shouldThrowConflictWhenDeleteFailsBecauseOfReference() {

        Page page = createPage();

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        doThrow(
                new DataIntegrityViolationException(
                        "foreign key violation"))
                .when(pageRepository)
                .flush();

        PageConflictException exception = assertThrows(
                PageConflictException.class,
                () -> pageService.delete(pageId));

        assertEquals(
                "Page cannot be deleted because it is referenced",
                exception.getMessage());
    }

    @Test
    void shouldFindCurrentVersion() {

        Page page = createPage();
        page.setVersion(7);

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.of(page));

        int version = pageService.findCurrentVersion(pageId);

        assertEquals(7, version);
    }

    @Test
    void shouldThrowWhenFindingVersionOfUnknownPage() {

        when(pageRepository.findById(pageId))
                .thenReturn(Optional.empty());

        assertThrows(
                PageNotFoundException.class,
                () -> pageService.findCurrentVersion(pageId));
    }

    @Test
    void shouldCreateEtag() {

        PageResponse response = new PageResponse(
                pageId,
                "Getting started",
                "getting-started",
                "# Welcome",
                3,
                folderId,
                userId,
                null,
                Instant.now(),
                Instant.now());

        String etag = pageService.createEtag(response);

        assertEquals(
                "\"" + pageId + "-v3\"",
                etag);
    }

    private Page createPage() {

        Page page = new Page();

        page.setId(pageId);
        page.setTitle("Getting started");
        page.setSlug("getting-started");
        page.setContent("# Welcome");
        page.setVersion(1);
        page.setFolderId(folderId);
        page.setCreatedBy(userId);
        page.setUpdatedBy(null);
        page.setCreatedAt(Instant.now());
        page.setUpdatedAt(Instant.now());

        return page;
    }

    private Folder mockFolder() {
        return new Folder();
    }
}
