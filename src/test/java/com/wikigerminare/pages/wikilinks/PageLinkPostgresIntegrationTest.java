package com.wikigerminare.pages.wikilinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wikigerminare.TestcontainersConfiguration;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PageLinkPostgresIntegrationTest {


@Autowired
private JdbcTemplate jdbc;

@Test
void mapsPageLinksTableWithExpectedColumns() {
    Integer columnCount = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'page_links'
              AND column_name IN (
                  'id',
                  'source_page_id',
                  'target_page_id',
                  'target_page_title',
                  'created_at'
              )
            """,
            Integer.class
    );

    assertThat(columnCount).isEqualTo(5);
}

@Test
void enforcesUniqueSourcePageAndTargetTitlePair() {
    UUID sourcePageId = createPage("source-page");

    insertLink(sourcePageId, null, "target-page");

    assertThatThrownBy(() ->
            insertLink(sourcePageId, null, "target-page")
    )
            .rootCause()
            .isInstanceOf(SQLException.class);
}

@Test
void allowsDifferentTargetTitlesForSameSourcePage() {
    UUID sourcePageId = createPage("source-page");

    insertLink(sourcePageId, null, "target-one");
    insertLink(sourcePageId, null, "target-two");

    Integer count = jdbc.queryForObject(
            """
            SELECT count(*)
            FROM page_links
            WHERE source_page_id = ?
            """,
            Integer.class,
            sourcePageId
    );

    assertThat(count).isEqualTo(2);
}

private UUID createPage(String slugPrefix) {
    UUID userId = createUser();

    UUID folderId = UUID.randomUUID();

    String uniqueSlug = slugPrefix + "-" + UUID.randomUUID();

    jdbc.update(
            """
            INSERT INTO folders (
                id,
                name,
                created_by
            )
            VALUES (?, ?, ?)
            """,
            folderId,
            "Folder " + uniqueSlug,
            userId
    );

    UUID pageId = UUID.randomUUID();

    jdbc.update(
            """
            INSERT INTO pages (
                id,
                title,
                slug,
                content,
                folder_id,
                created_by
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            pageId,
            "Page " + uniqueSlug,
            uniqueSlug,
            "",
            folderId,
            userId
    );

    return pageId;
}

private UUID createUser() {
    UUID userId = UUID.randomUUID();

    jdbc.update(
            """
            INSERT INTO users (
                id,
                name,
                email,
                password_hash
            )
            VALUES (?, ?, ?, ?)
            """,
            userId,
            "Test User",
            userId + "@test.local",
            "test-password"
    );

    return userId;
}

private void insertLink(
        UUID sourcePageId,
        UUID targetPageId,
        String targetTitle
) {
    jdbc.update(
            """
            INSERT INTO page_links (
                id,
                source_page_id,
                target_page_id,
                target_page_title
            )
            VALUES (?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            sourcePageId,
            targetPageId,
            targetTitle
    );
}
}
