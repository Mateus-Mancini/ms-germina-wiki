package com.wikigerminare.folders;

import com.wikigerminare.folders.dto.CreateFolderRequest;
import com.wikigerminare.folders.dto.FolderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
            Principal principal
    ) {
        UUID createdBy = UUID.fromString(principal.getName());

        FolderResponse response = folderService.create(request, createdBy);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderResponse> getById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(folderService.getById(id));
    }
}