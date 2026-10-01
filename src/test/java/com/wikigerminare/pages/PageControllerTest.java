package com.wikigerminare.pages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikigerminare.pages.dto.CreatePageRequest;
import com.wikigerminare.pages.dto.PageResponse;
import com.wikigerminare.pages.dto.UpdatePageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PageController.class)
@Import(PageExceptionHandler.class)
class PageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PageService pageService;

    @Test
    void shouldCreatePage() throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        CreatePageRequest request = new CreatePageRequest(
                "Getting started",
                "getting-started",
                "# Welcome\n\nRaw **Markdown**.",
                folderId
        );

        PageResponse response = new PageResponse(
                pageId,
                "Getting started",
                "getting-started",
                "# Welcome\n\nRaw **Markdown**.",
                1,
                folderId,
                creatorId,
                null,
                Instant.now(),
                Instant.now()
        );

        when(pageService.create(
                any(CreatePageRequest.class),
                eq(creatorId)
        )).thenReturn(response);

        mockMvc.perform(
                post("/api/pages")
                        .principal(() -> creatorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id")
                        .value(pageId.toString()))
                .andExpect(jsonPath("$.title")
                        .value("Getting started"))
                .andExpect(jsonPath("$.content")
                        .value("# Welcome\n\nRaw **Markdown**."))
                .andExpect(jsonPath("$.version")
                        .value(1))
                .andExpect(jsonPath("$.folderId")
                        .value(folderId.toString()));

        verify(pageService).create(
                any(CreatePageRequest.class),
                eq(creatorId)
        );
    }

    @Test
    void shouldReturnUnauthorizedWhenCreatingWithoutPrincipal()
            throws Exception {

        CreatePageRequest request = new CreatePageRequest(
                "Getting started",
                "getting-started",
                "# Welcome",
                UUID.randomUUID()
        );

        mockMvc.perform(
                post("/api/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(pageService);
    }

    @Test
    void shouldReturnBadRequestWhenPrincipalIsNotUuid()
            throws Exception {

        CreatePageRequest request = new CreatePageRequest(
                "Getting started",
                "getting-started",
                "# Welcome",
                UUID.randomUUID()
        );

        mockMvc.perform(
                post("/api/pages")
                        .principal(() -> "usuario-invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pageService);
    }

    @Test
    void shouldRejectInvalidCreateRequest()
            throws Exception {

        CreatePageRequest request = new CreatePageRequest(
                "",
                "",
                "",
                null
        );

        mockMvc.perform(
                post("/api/pages")
                        .principal(() -> UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pageService);
    }

    @Test
    void shouldGetPageByIdWithEtag()
            throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        PageResponse response = new PageResponse(
                pageId,
                "Getting started",
                "getting-started",
                "# Welcome\n\nRaw **Markdown**.",
                1,
                folderId,
                creatorId,
                null,
                Instant.now(),
                Instant.now()
        );

        when(pageService.getById(pageId))
                .thenReturn(response);

        when(pageService.createEtag(response))
                .thenReturn("\"" + pageId + "-v1\"");

        mockMvc.perform(
                get("/api/pages/{id}", pageId)
        )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "ETag",
                        "\"" + pageId + "-v1\""
                ))
                .andExpect(jsonPath("$.id")
                        .value(pageId.toString()))
                .andExpect(jsonPath("$.content")
                        .value("# Welcome\n\nRaw **Markdown**."))
                .andExpect(jsonPath("$.version")
                        .value(1));
    }

    @Test
    void shouldReturnNotFoundWhenPageDoesNotExist()
            throws Exception {

        UUID pageId = UUID.randomUUID();

        when(pageService.getById(pageId))
                .thenThrow(new PageNotFoundException(pageId));

        mockMvc.perform(
                get("/api/pages/{id}", pageId)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldListPages()
            throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        PageResponse response = new PageResponse(
                pageId,
                "Getting started",
                "getting-started",
                "# Welcome",
                1,
                folderId,
                creatorId,
                null,
                Instant.now(),
                Instant.now()
        );

        when(pageService.list())
                .thenReturn(List.of(response));

        mockMvc.perform(
                get("/api/pages")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title")
                        .value("Getting started"))
                .andExpect(jsonPath("$[0].version")
                        .value(1));
    }

    @Test
    void shouldReturnEmptyPageList()
            throws Exception {

        when(pageService.list())
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/pages")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldUpdatePageWithIfMatch()
            throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        String oldEtag = "\"" + pageId + "-v1\"";
        String newEtag = "\"" + pageId + "-v2\"";

        PageResponse response = new PageResponse(
                pageId,
                "Getting started",
                "getting-started",
                "Updated **Markdown**.",
                2,
                folderId,
                creatorId,
                creatorId,
                Instant.now(),
                Instant.now()
        );

        when(pageService.update(
                eq(pageId),
                any(UpdatePageRequest.class),
                eq(oldEtag),
                eq(creatorId)
        )).thenReturn(response);

        when(pageService.createEtag(response))
                .thenReturn(newEtag);

        String json = """
                {
                    "content": "Updated **Markdown**."
                }
                """;

        mockMvc.perform(
                patch("/api/pages/{id}", pageId)
                        .principal(() -> creatorId.toString())
                        .header("If-Match", oldEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", newEtag))
                .andExpect(jsonPath("$.content")
                        .value("Updated **Markdown**."))
                .andExpect(jsonPath("$.version")
                        .value(2));
    }

    @Test
    void shouldReturn428WhenIfMatchIsMissing()
            throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        when(pageService.update(
                eq(pageId),
                any(UpdatePageRequest.class),
                isNull(),
                eq(creatorId)
        )).thenThrow(
                new PagePreconditionRequiredException(
                        "If-Match header is required"
                )
        );

        String json = """
                {
                    "content": "Updated content"
                }
                """;

        mockMvc.perform(
                patch("/api/pages/{id}", pageId)
                        .principal(() -> creatorId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error")
                        .value("If-Match header is required"));
    }

    @Test
    void shouldReturn400WhenIfMatchIsInvalid()
            throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        when(pageService.update(
                eq(pageId),
                any(UpdatePageRequest.class),
                eq("invalid-etag"),
                eq(creatorId)
        )).thenThrow(
                new PageBadRequestException(
                        "Invalid If-Match value"
                )
        );

        String json = """
                {
                    "content": "Updated content"
                }
                """;

        mockMvc.perform(
                patch("/api/pages/{id}", pageId)
                        .principal(() -> creatorId.toString())
                        .header("If-Match", "invalid-etag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Invalid If-Match value"));
    }

    @Test
    void shouldReturn412WhenIfMatchIsStale()
            throws Exception {

        UUID pageId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        when(pageService.update(
                eq(pageId),
                any(UpdatePageRequest.class),
                eq("\"" + pageId + "-v1\""),
                eq(creatorId)
        )).thenThrow(
                new PagePreconditionFailedException(
                        pageId,
                        2
                )
        );

        String json = """
                {
                    "content": "Updated content"
                }
                """;

        mockMvc.perform(
                patch("/api/pages/{id}", pageId)
                        .principal(() -> creatorId.toString())
                        .header(
                                "If-Match",
                                "\"" + pageId + "-v1\""
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldDeletePage()
            throws Exception {

        UUID pageId = UUID.randomUUID();

        doNothing()
                .when(pageService)
                .delete(pageId);

        mockMvc.perform(
                delete("/api/pages/{id}", pageId)
        )
                .andExpect(status().isNoContent());

        verify(pageService).delete(pageId);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownPage()
            throws Exception {

        UUID pageId = UUID.randomUUID();

        doThrow(
                new PageNotFoundException(pageId)
        )
                .when(pageService)
                .delete(pageId);

        mockMvc.perform(
                delete("/api/pages/{id}", pageId)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldReturnConflictWhenDeletingReferencedPage()
            throws Exception {

        UUID pageId = UUID.randomUUID();

        doThrow(
                new PageConflictException(
                        "Page cannot be deleted because it is referenced"
                )
        )
                .when(pageService)
                .delete(pageId);

        mockMvc.perform(
                delete("/api/pages/{id}", pageId)
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value(
                                "Page cannot be deleted because it is referenced"
                        ));
    }
}