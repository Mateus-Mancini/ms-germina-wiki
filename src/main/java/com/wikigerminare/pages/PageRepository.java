package com.wikigerminare.pages;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PageRepository
        extends JpaRepository<Page, UUID> {
}