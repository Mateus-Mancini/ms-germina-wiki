package com.wikigerminare.pages.wikilinks;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PageLinkRepository extends JpaRepository<PageLink, UUID> {

    List<PageLink> findBySourcePageId(UUID sourcePageId);

    List<PageLink> findByTargetPageId(UUID targetPageId);

    List<PageLink> findByTargetPageIdIsNullAndTargetPageTitle(String targetPageTitle);

    boolean existsBySourcePageIdAndTargetPageId(
            UUID sourcePageId,
            UUID targetPageId
    );

    void deleteBySourcePageIdAndTargetPageId(
            UUID sourcePageId,
            UUID targetPageId
    );
}