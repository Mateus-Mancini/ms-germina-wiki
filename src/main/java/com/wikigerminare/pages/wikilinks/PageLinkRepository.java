package com.wikigerminare.pages.wikilinks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PageLinkRepository extends JpaRepository<PageLink, UUID> {

    List<PageLink> findBySourcePageId(UUID sourcePageId);

    List<PageLink> findByTargetPageId(UUID targetPageId);

    List<PageLink> findByTargetPageIdIsNullAndTargetPageTitle(
            String targetPageTitle
    );

    boolean existsBySourcePageIdAndTargetPageId(
            UUID sourcePageId,
            UUID targetPageId
    );

    void deleteBySourcePageIdAndTargetPageId(
            UUID sourcePageId,
            UUID targetPageId
    );

    @Query(
            value = """
                    SELECT pg_advisory_xact_lock(
                        hashtext(:lockKey)
                    )
                    """,
            nativeQuery = true
    )
    void acquireWikiLinkLock(
            @Param("lockKey") String lockKey
    );
}