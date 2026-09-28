package com.wikigerminare.folders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    long HIERARCHY_LOCK_KEY = 827364L;

    @Query(
        value = "SELECT CAST(pg_advisory_xact_lock(:lockKey) AS text)",
        nativeQuery = true
    )
    String acquireHierarchyLock(long lockKey);

    @Query("""
        select
            f.id as id,
            f.name as name,
            f.parentFolder.id as parentFolderId,
            f.createdBy as createdBy,
            f.createdAt as createdAt,
            f.updatedAt as updatedAt
        from Folder f
    """)
    List<FolderTreeProjection> findAllForTree();
}
