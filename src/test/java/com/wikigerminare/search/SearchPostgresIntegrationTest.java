package com.wikigerminare.search;

import com.wikigerminare.TestcontainersConfiguration;
import com.wikigerminare.pages.dto.PageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SearchPostgresIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void matchesPageTitleAndContent() {
        String token = uniqueToken();
        UUID creatorId = createUser();
        PageResponse titleMatch = insertPage(
                UUID.randomUUID(), "title-hit-" + token, "title-hit-" + token,
                "unrelated body", null, creatorId);
        PageResponse contentMatch = insertPage(
                UUID.randomUUID(), "content-hit-" + token, "unrelated title",
                "body contains " + token, null, creatorId);

        List<PageResponse> results = searchService.search(token, null, false);

        assertThat(results).extracting(PageResponse::id)
                .containsExactlyInAnyOrder(titleMatch.id(), contentMatch.id());
    }

    @Test
    void ranksMoreFrequentTermHigherAndBreaksTiesByUuid() {
        String token = uniqueToken();
        UUID creatorId = createUser();
        UUID moreRelevantId = new UUID(0, 1);
        UUID tiedIdOne = new UUID(0, 2);
        UUID tiedIdTwo = new UUID(0, 3);
        PageResponse moreRelevant = insertPage(
                moreRelevantId, "rank-high-" + token, "rank-high-" + token,
                token + " " + token + " " + token, null, creatorId);
        PageResponse tiedOne = insertPage(
                tiedIdOne, "rank-tie-one-" + token, "rank-tie-one-" + token,
                token, null, creatorId);
        PageResponse tiedTwo = insertPage(
                tiedIdTwo, "rank-tie-two-" + token, "rank-tie-two-" + token,
                token, null, creatorId);

        List<PageResponse> results = searchService.search(token, null, false);

        assertThat(results).extracting(PageResponse::id)
                .containsExactly(moreRelevant.id(), tiedOne.id(), tiedTwo.id());
    }

    @Test
    void returnsEmptyListWhenNoPageMatches() {
        assertThat(searchService.search(uniqueToken(), null, false)).isEmpty();
    }

    @Test
    void filtersDirectFolderAndAllNestedDescendants() {
        String token = uniqueToken();
        UUID creatorId = createUser();
        UUID rootId = createFolder("root-" + token, null, creatorId);
        UUID childId = createFolder("child-" + token, rootId, creatorId);
        UUID grandchildId = createFolder("grandchild-" + token, childId, creatorId);
        UUID unrelatedId = createFolder("unrelated-" + token, null, creatorId);

        PageResponse rootPage = insertPage(
                UUID.randomUUID(), "root-page-" + token, "root title", token,
                rootId, creatorId);
        PageResponse childPage = insertPage(
                UUID.randomUUID(), "child-page-" + token, "child title", token,
                childId, creatorId);
        PageResponse grandchildPage = insertPage(
                UUID.randomUUID(), "grandchild-page-" + token, "grandchild title", token,
                grandchildId, creatorId);
        insertPage(UUID.randomUUID(), "unrelated-page-" + token,
                "unrelated title", token, unrelatedId, creatorId);
        insertPage(UUID.randomUUID(), "unfiled-page-" + token,
                "unfiled title", token, null, creatorId);

        List<PageResponse> direct = searchService.search(token, rootId, false);
        List<PageResponse> recursive = searchService.search(token, rootId, true);

        assertThat(direct).extracting(PageResponse::id).containsExactly(rootPage.id());
        assertThat(recursive).extracting(PageResponse::id)
                .containsExactlyInAnyOrder(rootPage.id(), childPage.id(), grandchildPage.id());
    }

    @Test
    void includesUnfiledPagesInGlobalSearchEvenWhenDescendantOptionIsTrue() {
        String token = uniqueToken();
        UUID creatorId = createUser();
        PageResponse unfiled = insertPage(
                UUID.randomUUID(), "unfiled-" + token, "unfiled title", token,
                null, creatorId);

        List<PageResponse> results = searchService.search(token, null, true);

        assertThat(results).extracting(PageResponse::id).containsExactly(unfiled.id());
    }

    private String uniqueToken() {
        return "searchtoken" + UUID.randomUUID().toString().replace("-", "");
    }

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, name, email, password_hash)
                VALUES (?, ?, ?, ?)
                """, userId, "Search test user", userId + "@test.local", "test-password");
        return userId;
    }

    private UUID createFolder(String name, UUID parentFolderId, UUID creatorId) {
        UUID folderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO folders (id, name, parent_folder_id, created_by)
                VALUES (?, ?, ?, ?)
                """, folderId, name, parentFolderId, creatorId);
        return folderId;
    }

    private PageResponse insertPage(
            UUID pageId,
            String slug,
            String title,
            String content,
            UUID folderId,
            UUID creatorId
    ) {
        jdbc.update("""
                INSERT INTO pages (id, title, slug, content, folder_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, pageId, title, slug, content, folderId, creatorId);
        return new PageResponse(
                pageId, title, slug, content, 1, folderId, creatorId, null,
                java.time.Instant.now(), java.time.Instant.now());
    }
}
