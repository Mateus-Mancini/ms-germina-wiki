package com.wikigerminare.folders;

import com.wikigerminare.folders.dto.CreateFolderRequest;
import com.wikigerminare.folders.dto.FolderResponse;
import com.wikigerminare.folders.dto.FolderTreeNodeResponse;
import com.wikigerminare.folders.dto.UpdateFolderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @Valid @RequestBody CreateFolderRequest request,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID createdBy = UUID.fromString(principal.getName());

        FolderResponse response = folderService.create(request, createdBy);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderResponse> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(folderService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> list() {
        return ResponseEntity.ok(folderService.list());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FolderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFolderRequest request) {
        return ResponseEntity.ok(
                folderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {
        try {
            folderService.delete(id);

            return ResponseEntity.noContent().build();

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/tree")
    public ResponseEntity<List<FolderTreeNodeResponse>> getTree() {
        return ResponseEntity.ok(folderService.getTree());
    }
}