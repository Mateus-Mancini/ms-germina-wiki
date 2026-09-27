package com.wikigerminare.folders;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    long HIERARCHY_LOCK_KEY = 827364L;

    @Modifying
    @Transactional
    @Query(value = "SELECT pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    void acquireHierarchyLock(long lockKey);
}