package com.wikigerminare.folders;

import com.wikigerminare.folders.dto.CreateFolderRequest;
import com.wikigerminare.folders.dto.FolderResponse;
import com.wikigerminare.folders.dto.FolderTreeNodeResponse;
import com.wikigerminare.folders.dto.UpdateFolderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    private FolderService folderService;

    private UUID creatorId;
    private UUID folderId;
    private UUID parentId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(folderRepository);

        creatorId = UUID.randomUUID();
        folderId = UUID.randomUUID();
        parentId = UUID.randomUUID();
        childId = UUID.randomUUID();
    }

    // =========================================================
    // US1 - CREATE
    // =========================================================

    @Test
    void shouldCreateRootFolder() {

        CreateFolderRequest request =
                new CreateFolderRequest("Pasta raiz", null);

        when(folderRepository.save(any(Folder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FolderResponse response =
                folderService.create(request, creatorId);

        assertNotNull(response);
        assertEquals("Pasta raiz", response.name());
        assertNull(response.parentFolderId());
        assertEquals(creatorId, response.createdBy());

        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void shouldCreateChildFolder() {

        Folder parent = createFolder(
                parentId,
                "Pasta pai",
                null,
                creatorId
        );

        CreateFolderRequest request =
                new CreateFolderRequest("Pasta filha", parentId);

        when(folderRepository.findById(parentId))
                .thenReturn(Optional.of(parent));

        when(folderRepository.save(any(Folder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FolderResponse response =
                folderService.create(request, creatorId);

        assertNotNull(response);
        assertEquals("Pasta filha", response.name());
        assertEquals(parentId, response.parentFolderId());
        assertEquals(creatorId, response.createdBy());

        verify(folderRepository).findById(parentId);
        verify(folderRepository).save(any(Folder.class));
    }

    @Test
    void shouldRejectMissingParentWhenCreatingFolder() {

        UUID missingParentId = UUID.randomUUID();

        CreateFolderRequest request =
                new CreateFolderRequest(
                        "Pasta filha",
                        missingParentId
                );

        when(folderRepository.findById(missingParentId))
                .thenReturn(Optional.empty());

        assertThrows(
                FolderNotFoundException.class,
                () -> folderService.create(request, creatorId)
        );

        verify(folderRepository, never()).save(any(Folder.class));
    }

    @Test
    void shouldReturnNotFoundWhenGettingAbsentFolder() {

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

        assertThrows(
                FolderNotFoundException.class,
                () -> folderService.getById(folderId)
        );
    }

    // =========================================================
    // US2 - LIST
    // =========================================================

    @Test
    void shouldListFolders() {

        Folder first = createFolder(
                UUID.randomUUID(),
                "Pasta 1",
                null,
                creatorId
        );

        Folder second = createFolder(
                UUID.randomUUID(),
                "Pasta 2",
                null,
                creatorId
        );

        when(folderRepository.findAll())
                .thenReturn(List.of(first, second));

        List<FolderResponse> result =
                folderService.list();

        assertEquals(2, result.size());
        assertEquals("Pasta 1", result.get(0).name());
        assertEquals("Pasta 2", result.get(1).name());

        verify(folderRepository).findAll();
    }

    // =========================================================
    // US2 - UPDATE NAME
    // =========================================================

    @Test
    void shouldUpdateOnlyName() {

        Folder folder = createFolder(
                folderId,
                "Nome antigo",
                null,
                creatorId
        );

        UpdateFolderRequest request = new UpdateFolderRequest();
        request.setName("Nome novo");

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        when(folderRepository.save(any(Folder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FolderResponse response =
                folderService.update(folderId, request);

        assertEquals("Nome novo", response.name());
        assertNull(response.parentFolderId());

        verify(folderRepository).save(folder);
    }

    // =========================================================
    // US2 - UPDATE PARENT
    // =========================================================

    @Test
    void shouldUpdateParent() {

        Folder folder = createFolder(
                folderId,
                "Pasta",
                null,
                creatorId
        );

        Folder newParent = createFolder(
                parentId,
                "Novo pai",
                null,
                creatorId
        );

        UpdateFolderRequest request = new UpdateFolderRequest();
        request.setParentFolderId(parentId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        when(folderRepository.findById(parentId))
                .thenReturn(Optional.of(newParent));

        when(folderRepository.save(any(Folder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FolderResponse response =
                folderService.update(folderId, request);

        assertEquals(parentId, response.parentFolderId());

        verify(folderRepository)
                .acquireHierarchyLock(FolderRepository.HIERARCHY_LOCK_KEY);

        verify(folderRepository).save(folder);
    }

    // =========================================================
    // US2 - EXPLICIT NULL
    // =========================================================

    @Test
    void shouldMoveFolderToRootWhenParentIsExplicitlyNull() {

        Folder parent = createFolder(
                parentId,
                "Pai",
                null,
                creatorId
        );

        Folder folder = createFolder(
                folderId,
                "Filha",
                parent,
                creatorId
        );

        UpdateFolderRequest request = new UpdateFolderRequest();
        request.setParentFolderId(null);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        when(folderRepository.save(any(Folder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FolderResponse response =
                folderService.update(folderId, request);

        assertNull(response.parentFolderId());

        verify(folderRepository).save(folder);
    }

    // =========================================================
    // US2 - EMPTY UPDATE
    // =========================================================

    @Test
    void shouldRejectEmptyUpdate() {

        Folder folder = createFolder(
                folderId,
                "Pasta",
                null,
                creatorId
        );

        UpdateFolderRequest request =
                new UpdateFolderRequest();

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        assertThrows(
                FolderValidationException.class,
                () -> folderService.update(folderId, request)
        );

        verify(folderRepository, never())
                .save(any(Folder.class));
    }

    // =========================================================
    // US2 - BLANK NAME
    // =========================================================

    @Test
    void shouldRejectBlankName() {

        Folder folder = createFolder(
                folderId,
                "Pasta",
                null,
                creatorId
        );

        UpdateFolderRequest request =
                new UpdateFolderRequest();

        request.setName("   ");

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        assertThrows(
                FolderValidationException.class,
                () -> folderService.update(folderId, request)
        );

        verify(folderRepository, never())
                .save(any(Folder.class));
    }

    // =========================================================
    // US2 - MISSING PARENT
    // =========================================================

    @Test
    void shouldRejectMissingParentWhenUpdating() {

        Folder folder = createFolder(
                folderId,
                "Pasta",
                null,
                creatorId
        );

        UUID missingParentId = UUID.randomUUID();

        UpdateFolderRequest request =
                new UpdateFolderRequest();

        request.setParentFolderId(missingParentId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        when(folderRepository.findById(missingParentId))
                .thenReturn(Optional.empty());

        assertThrows(
                FolderNotFoundException.class,
                () -> folderService.update(folderId, request)
        );

        verify(folderRepository, never())
                .save(any(Folder.class));
    }

    // =========================================================
    // US2 - SELF PARENT
    // =========================================================

    @Test
    void shouldRejectSelfParenting() {

        Folder folder = createFolder(
                folderId,
                "Pasta",
                null,
                creatorId
        );

        UpdateFolderRequest request =
                new UpdateFolderRequest();

        request.setParentFolderId(folderId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        assertThrows(
                FolderConflictException.class,
                () -> folderService.update(folderId, request)
        );

        verify(folderRepository, never())
                .save(any(Folder.class));
    }

    // =========================================================
    // US2 - CYCLE
    // =========================================================

    @Test
    void shouldRejectAncestorCycle() {

        Folder folderA = createFolder(
                folderId,
                "A",
                null,
                creatorId
        );

        Folder folderB = createFolder(
                parentId,
                "B",
                folderA,
                creatorId
        );

        UpdateFolderRequest request =
                new UpdateFolderRequest();

        request.setParentFolderId(parentId);

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folderA));

        when(folderRepository.findById(parentId))
                .thenReturn(Optional.of(folderB));

        assertThrows(
                FolderConflictException.class,
                () -> folderService.update(folderId, request)
        );

        verify(folderRepository, never())
                .save(any(Folder.class));
    }

    // =========================================================
    // US2 - DELETE
    // =========================================================

    @Test
    void shouldDeleteFolder() {

        Folder folder = createFolder(
                folderId,
                "Pasta",
                null,
                creatorId
        );

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.of(folder));

        folderService.delete(folderId);

        verify(folderRepository).delete(folder);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingAbsentFolder() {

        when(folderRepository.findById(folderId))
                .thenReturn(Optional.empty());

        assertThrows(
                FolderNotFoundException.class,
                () -> folderService.delete(folderId)
        );

        verify(folderRepository, never())
                .delete(any(Folder.class));
    }

    // =========================================================
    // US3 - TREE
    // =========================================================

    @Test
    void shouldBuildFolderTree() {

        FolderTreeProjection root =
                mockProjection(
                        parentId,
                        "Raiz",
                        null
                );

        FolderTreeProjection child =
                mockProjection(
                        childId,
                        "Filha",
                        parentId
                );

        when(folderRepository.findAllForTree())
                .thenReturn(List.of(root, child));

        List<FolderTreeNodeResponse> result =
                folderService.getTree();

        assertEquals(1, result.size());

        FolderTreeNodeResponse rootNode =
                result.get(0);

        assertEquals(parentId, rootNode.id());
        assertEquals("Raiz", rootNode.name());

        assertEquals(1, rootNode.children().size());

        FolderTreeNodeResponse childNode =
                rootNode.children().get(0);

        assertEquals(childId, childNode.id());
        assertEquals("Filha", childNode.name());
        assertTrue(childNode.children().isEmpty());

        verify(folderRepository).findAllForTree();
    }

    @Test
    void shouldReturnEmptyTreeWhenThereAreNoFolders() {

        when(folderRepository.findAllForTree())
                .thenReturn(List.of());

        List<FolderTreeNodeResponse> result =
                folderService.getTree();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSupportMultipleRoots() {

        UUID root1Id = UUID.randomUUID();
        UUID root2Id = UUID.randomUUID();

        FolderTreeProjection root1 =
                mockProjection(
                        root1Id,
                        "Raiz 1",
                        null
                );

        FolderTreeProjection root2 =
                mockProjection(
                        root2Id,
                        "Raiz 2",
                        null
                );

        when(folderRepository.findAllForTree())
                .thenReturn(List.of(root1, root2));

        List<FolderTreeNodeResponse> result =
                folderService.getTree();

        assertEquals(2, result.size());
        assertEquals("Raiz 1", result.get(0).name());
        assertEquals("Raiz 2", result.get(1).name());
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Folder createFolder(
            UUID id,
            String name,
            Folder parent,
            UUID createdBy
    ) {

        Folder folder = new Folder();

        folder.setId(id);
        folder.setName(name);
        folder.setParentFolder(parent);
        folder.setCreatedBy(createdBy);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());

        return folder;
    }

    private FolderTreeProjection mockProjection(
            UUID id,
            String name,
            UUID parentId
    ) {

        FolderTreeProjection projection =
                mock(FolderTreeProjection.class);

        when(projection.getId())
                .thenReturn(id);

        when(projection.getName())
                .thenReturn(name);

        when(projection.getParentFolderId())
                .thenReturn(parentId);

        when(projection.getCreatedBy())
                .thenReturn(creatorId);

        when(projection.getCreatedAt())
                .thenReturn(Instant.now());

        when(projection.getUpdatedAt())
                .thenReturn(Instant.now());

        return projection;
    }
}