package com.wikigerminare.search;

import com.wikigerminare.pages.Page;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SearchRepository {

    private static final String FULL_TEXT_VECTOR = "to_tsvector('english', "
            + "COALESCE(p.title, '') || ' ' || COALESCE(p.content, ''))";
    private static final String FULL_TEXT_QUERY = "plainto_tsquery('english', :q)";

    private static final String GLOBAL_SEARCH_SQL = """
            SELECT p.*
            FROM pages p
            WHERE %s @@ %s
            ORDER BY ts_rank(%s, %s) DESC, p.id ASC
            """.formatted(
            FULL_TEXT_VECTOR,
            FULL_TEXT_QUERY,
            FULL_TEXT_VECTOR,
            FULL_TEXT_QUERY);

    private static final String DIRECT_FOLDER_SEARCH_SQL = """
            SELECT p.*
            FROM pages p
            WHERE %s @@ %s
              AND p.folder_id = :folderId
            ORDER BY ts_rank(%s, %s) DESC, p.id ASC
            """.formatted(
            FULL_TEXT_VECTOR,
            FULL_TEXT_QUERY,
            FULL_TEXT_VECTOR,
            FULL_TEXT_QUERY);

    private static final String DESCENDANT_FOLDER_SEARCH_SQL = """
            WITH RECURSIVE folder_scope(id) AS (
                SELECT CAST(:folderId AS UUID)
                UNION
                SELECT child.id
                FROM folders child
                JOIN folder_scope parent ON child.parent_folder_id = parent.id
            )
            SELECT p.*
            FROM pages p
            JOIN folder_scope scope ON scope.id = p.folder_id
            WHERE %s @@ %s
            ORDER BY ts_rank(%s, %s) DESC, p.id ASC
            """.formatted(
            FULL_TEXT_VECTOR,
            FULL_TEXT_QUERY,
            FULL_TEXT_VECTOR,
            FULL_TEXT_QUERY);

    @PersistenceContext
    private EntityManager entityManager;

    public List<Page> searchGlobally(String queryText) {
        return execute(GLOBAL_SEARCH_SQL, queryText, null);
    }

    public List<Page> searchInFolder(String queryText, UUID folderId) {
        return execute(DIRECT_FOLDER_SEARCH_SQL, queryText, folderId);
    }

    public List<Page> searchInFolderAndDescendants(String queryText, UUID folderId) {
        return execute(DESCENDANT_FOLDER_SEARCH_SQL, queryText, folderId);
    }

    @SuppressWarnings("unchecked")
    private List<Page> execute(String sql, String queryText, UUID folderId) {
        Query query = entityManager.createNativeQuery(sql, Page.class)
                .setParameter("q", queryText);
        if (folderId != null) {
            query.setParameter("folderId", folderId);
        }
        return query.getResultList();
    }
}
