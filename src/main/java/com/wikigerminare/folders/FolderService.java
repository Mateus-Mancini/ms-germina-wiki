package com.wikigerminare.folders;

import com.wikigerminare.folders.dto.CreateFolderRequest;
import com.wikigerminare.folders.dto.FolderResponse;
import com.wikigerminare.folders.dto.UpdateFolderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FolderService {

    private final FolderRepository folderRepository;

    public FolderService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional
    public FolderResponse create(CreateFolderRequest request, UUID createdBy) {

        Folder parentFolder = null;

        if (request.parentFolderId() != null) {
            parentFolder = folderRepository.findById(request.parentFolderId())
                    .orElseThrow(() ->
                            new FolderNotFoundException(request.parentFolderId()));
        }

        Instant now = Instant.now();

        Folder folder = new Folder();

        folder.setId(UUID.randomUUID());
        folder.setName(request.name());
        folder.setParentFolder(parentFolder);
        folder.setCreatedBy(createdBy);
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);

        Folder savedFolder = folderRepository.save(folder);

        return toResponse(savedFolder);
    }

    @Transactional(readOnly = true)
    public FolderResponse getById(UUID id) {

        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new FolderNotFoundException(id));

        return toResponse(folder);
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> list() {

        return folderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FolderResponse update(UUID id, UpdateFolderRequest request) {

        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new FolderNotFoundException(id));

        // Nenhum campo foi enviado
        if (!request.isNameProvided()
                && !request.isParentFolderIdProvided()) {

            throw new FolderConflictException(
                    "At least one field must be provided for update"
            );
        }

        /*
         * Atualização do nome.
         *
         * O campo foi enviado, mas veio null:
         * não aceitamos nome nulo.
         */
        if (request.isNameProvided()) {

            if (request.getName() == null
                    || request.getName().isBlank()) {

                throw new FolderConflictException(
                        "name must not be blank"
                );
            }

            folder.setName(request.getName());
        }

        /*
         * Atualização do pai.
         *
         * O lock deve ser adquirido antes de consultar
         * a hierarquia para evitar condições de corrida.
         */
        if (request.isParentFolderIdProvided()) {

            folderRepository.acquireHierarchyLock(
                    FolderRepository.HIERARCHY_LOCK_KEY
            );

            UUID newParentId = request.getParentFolderId();

            /*
             * parentFolderId = null significa:
             * mover a pasta para a raiz.
             */
            if (newParentId == null) {

                folder.setParentFolder(null);

            } else {

                // Uma pasta não pode ser filha dela mesma.
                if (folder.getId().equals(newParentId)) {

                    throw new FolderConflictException(
                            "A folder cannot be its own parent"
                    );
                }

                Folder newParent = folderRepository.findById(newParentId)
                        .orElseThrow(() ->
                                new FolderNotFoundException(newParentId));

                // Verifica se o novo pai está abaixo da pasta atual.
                if (createsCycle(folder, newParent)) {

                    throw new FolderConflictException(
                            "The requested parent would create a cycle"
                    );
                }

                folder.setParentFolder(newParent);
            }
        }

        /*
         * Só chegamos aqui se todas as validações passaram.
         * Portanto a alteração pode atualizar updatedAt.
         */
        folder.setUpdatedAt(Instant.now());

        Folder savedFolder = folderRepository.save(folder);

        return toResponse(savedFolder);
    }

    @Transactional
    public void delete(UUID id) {

        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new FolderNotFoundException(id));

        folderRepository.delete(folder);
    }

    private boolean createsCycle(
            Folder folder,
            Folder proposedParent
    ) {

        UUID folderId = folder.getId();
        UUID currentId = proposedParent.getId();

        List<UUID> visited = new ArrayList<>();

        while (currentId != null) {

            /*
             * Se encontramos um ID que já visitamos,
             * os dados existentes possuem um ciclo.
             */
            if (visited.contains(currentId)) {
                return true;
            }

            visited.add(currentId);

            /*
             * O caminho do novo pai chegou até a pasta
             * que estamos tentando mover.
             */
            if (folderId.equals(currentId)) {
                return true;
            }

            Folder current = folderRepository.findById(currentId)
                    .orElse(null);

            /*
             * Chegamos à raiz.
             */
            if (current == null
                    || current.getParentFolder() == null) {

                return false;
            }

            currentId = current.getParentFolder().getId();
        }

        return false;
    }

    private FolderResponse toResponse(Folder folder) {

        UUID parentFolderId = folder.getParentFolder() != null
                ? folder.getParentFolder().getId()
                : null;

        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                parentFolderId,
                folder.getCreatedBy(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}