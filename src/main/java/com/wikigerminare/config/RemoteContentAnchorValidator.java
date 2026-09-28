package com.wikigerminare.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.wikigerminare.integration.AnchorInput;
import com.wikigerminare.integration.ContentAnchorValidator;
import com.wikigerminare.integration.ValidatedAnchor;
import com.wikigerminare.service.CommentException;

@Component
public class RemoteContentAnchorValidator implements ContentAnchorValidator {
    private final String baseUrl;
    private final RestClient client;

    public RemoteContentAnchorValidator(RestClient.Builder builder,
                                        @Value("${wikigerminare.content.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void assertPublishedContent(UUID contentId) {
        if (baseUrl.isBlank()) {
            throw new CommentException("CONTENT_UNAVAILABLE", "Content service is not configured");
        }
        try {
            PublicationResponse response = client.get()
                    .uri("/contents/{contentId}/publication", contentId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, responseMessage) -> { throw new CommentException("CONTENT_NOT_FOUND", "Content not found"); })
                    .body(PublicationResponse.class);
            if (response == null || !response.published()) {
                throw new CommentException("CONTENT_UNAVAILABLE", "Content is not published");
            }
        } catch (CommentException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CommentException("CONTENT_UNAVAILABLE", "Content service is unavailable");
        }
    }

    @Override
    public ValidatedAnchor validateAnchor(UUID contentId, AnchorInput anchorInput) {
        assertPublishedContent(contentId);
        try {
            AnchorResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/contents/{contentId}/anchors/{type}/{value}")
                            .queryParam("revision", anchorInput.revision())
                            .build(contentId, anchorInput.type(), anchorInput.value()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, responseMessage) -> { throw new CommentException("ANCHOR_INVALID", "Anchor is invalid"); })
                    .body(AnchorResponse.class);
            if (response == null || !response.valid()) {
                throw new CommentException("ANCHOR_INVALID", "Anchor is invalid or stale");
            }
            return new ValidatedAnchor(response.type(), response.value(), response.revision());
        } catch (CommentException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CommentException("CONTENT_UNAVAILABLE", "Content service is unavailable");
        }
    }

    private record PublicationResponse(boolean published) {
    }

    private record AnchorResponse(boolean valid, String type, String value, String revision) {
    }
}
