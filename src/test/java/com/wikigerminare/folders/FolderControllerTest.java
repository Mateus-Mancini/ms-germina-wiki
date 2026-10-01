package com.wikigerminare.folders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikigerminare.folders.dto.CreateFolderRequest;
import com.wikigerminare.folders.dto.FolderResponse;
import com.wikigerminare.folders.dto.FolderTreeNodeResponse;
import com.wikigerminare.folders.dto.UpdateFolderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@WebMvcTest(FolderController.class)
class FolderControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private FolderService folderService;

        @TestConfiguration
        static class TestConfig {

                @Bean
                ObjectMapper objectMapper() {
                        return new ObjectMapper();
                }
        }

        @Test
        void shouldCreateRootFolder() throws Exception {

                UUID folderId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();

                CreateFolderRequest request = new CreateFolderRequest("Minha pasta", null);

                FolderResponse response = new FolderResponse(
                                folderId,
                                "Minha pasta",
                                null,
                                creatorId,
                                Instant.now(),
                                Instant.now());

                when(folderService.create(any(CreateFolderRequest.class), eq(creatorId)))
                                .thenReturn(response);

                mockMvc.perform(
                                post("/api/folders")
                                                .principal(() -> creatorId.toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(folderId.toString()))
                                .andExpect(jsonPath("$.name").value("Minha pasta"))
                                .andExpect(jsonPath("$.parentFolderId").doesNotExist())
                                .andExpect(jsonPath("$.createdBy").value(creatorId.toString()));

                verify(folderService).create(
                                any(CreateFolderRequest.class),
                                eq(creatorId));
        }

        @Test
        void shouldReturnUnauthorizedWhenCreatingWithoutPrincipal()
                        throws Exception {

                CreateFolderRequest request = new CreateFolderRequest("Minha pasta", null);

                mockMvc.perform(
                                post("/api/folders")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());

                verifyNoInteractions(folderService);
        }

        @Test
        void shouldRejectInvalidCreateRequest() throws Exception {

                CreateFolderRequest request = new CreateFolderRequest("", null);

                mockMvc.perform(
                                post("/api/folders")
                                                .principal(() -> UUID.randomUUID().toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(folderService);
        }

        @Test
        void shouldGetFolderById() throws Exception {

                UUID folderId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();

                FolderResponse response = new FolderResponse(
                                folderId,
                                "Minha pasta",
                                null,
                                creatorId,
                                Instant.now(),
                                Instant.now());

                when(folderService.getById(folderId))
                                .thenReturn(response);

                mockMvc.perform(
                                get("/api/folders/{id}", folderId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(folderId.toString()))
                                .andExpect(jsonPath("$.name").value("Minha pasta"))
                                .andExpect(jsonPath("$.createdBy").value(creatorId.toString()));
        }

        @Test
        void shouldReturnNotFoundWhenFolderDoesNotExist()
                        throws Exception {

                UUID folderId = UUID.randomUUID();

                when(folderService.getById(folderId))
                                .thenThrow(new FolderNotFoundException(folderId));

                mockMvc.perform(
                                get("/api/folders/{id}", folderId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldListFolders() throws Exception {

                UUID folderId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();

                FolderResponse response = new FolderResponse(
                                folderId,
                                "Minha pasta",
                                null,
                                creatorId,
                                Instant.now(),
                                Instant.now());

                when(folderService.list())
                                .thenReturn(List.of(response));

                mockMvc.perform(
                                get("/api/folders"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].id").value(folderId.toString()))
                                .andExpect(jsonPath("$[0].name").value("Minha pasta"));
        }

        @Test
        void shouldUpdateFolderName() throws Exception {

                UUID folderId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();

                FolderResponse response = new FolderResponse(
                                folderId,
                                "Nome atualizado",
                                null,
                                creatorId,
                                Instant.now(),
                                Instant.now());

                when(folderService.update(
                                eq(folderId),
                                any(UpdateFolderRequest.class))).thenReturn(response);

                String json = """
                                {
                                    "name": "Nome atualizado"
                                }
                                """;

                mockMvc.perform(
                                patch("/api/folders/{id}", folderId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(json))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(folderId.toString()))
                                .andExpect(jsonPath("$.name").value("Nome atualizado"));
        }

        @Test
        void shouldRejectBlankNameOnUpdate() throws Exception {

                UUID folderId = UUID.randomUUID();

                String json = """
                                {
                                    "name": ""
                                }
                                """;

                mockMvc.perform(
                                patch("/api/folders/{id}", folderId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(json))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(folderService);
        }

        @Test
        void shouldRejectEmptyUpdate() throws Exception {

                UUID folderId = UUID.randomUUID();

                String json = """
                                {}
                                """;

                /*
                 * O UpdateFolderRequest permite que o objeto seja criado,
                 * mas a regra "pelo menos um campo" pertence ao Service.
                 *
                 * Por isso o mock lança FolderValidationException.
                 */
                when(folderService.update(
                                eq(folderId),
                                any(UpdateFolderRequest.class))).thenThrow(
                                                new FolderValidationException(
                                                                "At least one field must be provided for update"));

                mockMvc.perform(
                                patch("/api/folders/{id}", folderId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(json))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectSelfParenting() throws Exception {

                UUID folderId = UUID.randomUUID();

                when(folderService.update(
                                eq(folderId),
                                any(UpdateFolderRequest.class))).thenThrow(
                                                new FolderConflictException(
                                                                "A folder cannot be its own parent"));

                String json = """
                                {
                                    "parentFolderId": "%s"
                                }
                                """.formatted(folderId);

                mockMvc.perform(
                                patch("/api/folders/{id}", folderId)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(json))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error")
                                                .value("A folder cannot be its own parent"));
        }

        @Test
        void shouldDeleteFolder() throws Exception {

                UUID folderId = UUID.randomUUID();

                doNothing()
                                .when(folderService)
                                .delete(folderId);

                mockMvc.perform(
                                delete("/api/folders/{id}", folderId))
                                .andExpect(status().isNoContent());

                verify(folderService).delete(folderId);
        }

        @Test
        void shouldReturnConflictWhenDeletingReferencedFolder()
                        throws Exception {

                UUID folderId = UUID.randomUUID();

                doThrow(
                                new org.springframework.dao.DataIntegrityViolationException(
                                                "foreign key violation"))
                                .when(folderService)
                                .delete(folderId);

                mockMvc.perform(
                                delete("/api/folders/{id}", folderId))
                                .andExpect(status().isConflict());
        }

        @Test
        void shouldReturnFolderTree() throws Exception {

                UUID rootId = UUID.randomUUID();
                UUID childId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();

                FolderTreeNodeResponse child = new FolderTreeNodeResponse(
                                childId,
                                "Filha",
                                rootId,
                                creatorId,
                                Instant.now(),
                                Instant.now(),
                                new java.util.ArrayList<>());

                FolderTreeNodeResponse root = new FolderTreeNodeResponse(
                                rootId,
                                "Raiz",
                                null,
                                creatorId,
                                Instant.now(),
                                Instant.now(),
                                new java.util.ArrayList<>());

                root.children().add(child);

                when(folderService.getTree())
                                .thenReturn(List.of(root));

                mockMvc.perform(
                                get("/api/folders/tree"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].name").value("Raiz"))
                                .andExpect(jsonPath("$[0].children").isArray())
                                .andExpect(jsonPath("$[0].children.length()").value(1))
                                .andExpect(jsonPath("$[0].children[0].name")
                                                .value("Filha"));
        }

        @Test
        void shouldReturnEmptyTree() throws Exception {

                when(folderService.getTree())
                                .thenReturn(List.of());

                mockMvc.perform(
                                get("/api/folders/tree"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(0));
        }
}
