-- V1: initial GerminaWiki schema.
-- Source: the team schema script "db-script" by Camilla (@CamillaMorenoA), task "Criação do Script do Banco e Modelagem",
-- reproduced verbatim below. Never edit this file once applied; add V2, V3, ... instead.

-- ============================================================
-- KNOWLEDGE BASE / DOCUMENTATION PLATFORM
-- PostgreSQL Database Schema
-- ============================================================

-- ============================================================
-- EXTENSIONS
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ============================================================
-- ENUM TYPES
-- ============================================================

CREATE TYPE user_role AS ENUM (
    'admin',
    'member'
);

CREATE TYPE comment_status AS ENUM (
    'OPEN',
    'RESOLVED'
);


-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(150) NOT NULL,

    email VARCHAR(255) NOT NULL UNIQUE,

    password_hash TEXT NOT NULL,

    avatar_url TEXT,

    bio TEXT,

    role user_role NOT NULL DEFAULT 'member',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- FOLDERS
-- ============================================================

CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(150) NOT NULL,

    parent_folder_id UUID,

    created_by UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_folder_parent
        FOREIGN KEY (parent_folder_id)
        REFERENCES folders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_folder_creator
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT unique_folder_name_per_parent
        UNIQUE (parent_folder_id, name)
);


-- ============================================================
-- PAGES
-- ============================================================

CREATE TABLE pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    title VARCHAR(255) NOT NULL,

    slug VARCHAR(300) NOT NULL UNIQUE,

    content TEXT NOT NULL DEFAULT '',

    -- Optimistic locking
    version INTEGER NOT NULL DEFAULT 1,

    folder_id UUID,

    created_by UUID NOT NULL,

    updated_by UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_page_folder
        FOREIGN KEY (folder_id)
        REFERENCES folders(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_page_creator
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_page_updater
        FOREIGN KEY (updated_by)
        REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT page_version_positive
        CHECK (version > 0)
);


-- ============================================================
-- PAGE IMAGES
-- ============================================================

CREATE TABLE page_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    page_id UUID NOT NULL,

    file_name VARCHAR(255) NOT NULL,

    file_url TEXT NOT NULL,

    mime_type VARCHAR(100) NOT NULL,

    file_size BIGINT,

    uploaded_by UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_image_page
        FOREIGN KEY (page_id)
        REFERENCES pages(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_image_uploader
        FOREIGN KEY (uploaded_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT positive_file_size
        CHECK (file_size IS NULL OR file_size >= 0)
);


-- ============================================================
-- COMMENTS
-- ============================================================
-- Comments are anchored to a stable block ID inside the
-- Markdown content.
--
-- Example:
-- <!--b:550e8400-e29b-41d4-a716-446655440000-->
-- This is the paragraph being commented.
--
-- block_id stores:
-- 550e8400-e29b-41d4-a716-446655440000


CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    page_id UUID NOT NULL,

    user_id UUID NOT NULL,

    parent_comment_id UUID,

    block_id UUID NOT NULL,

    content TEXT NOT NULL,

    status comment_status NOT NULL DEFAULT 'OPEN',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comment_page
        FOREIGN KEY (page_id)
        REFERENCES pages(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_comment_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_comment_parent
        FOREIGN KEY (parent_comment_id)
        REFERENCES comments(id)
        ON DELETE CASCADE
);


-- ============================================================
-- TAGS
-- ============================================================

CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- PAGE TAGS
-- ============================================================
-- Many-to-many relationship between pages and tags.

CREATE TABLE page_tags (
    page_id UUID NOT NULL,

    tag_id UUID NOT NULL,

    PRIMARY KEY (page_id, tag_id),

    CONSTRAINT fk_page_tags_page
        FOREIGN KEY (page_id)
        REFERENCES pages(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_tags_tag
        FOREIGN KEY (tag_id)
        REFERENCES tags(id)
        ON DELETE CASCADE
);


-- ============================================================
-- PAGE LINKS
-- ============================================================
-- Represents internal Wikilinks between pages.
--
-- Example:
-- [[Authentication]]
--
-- A link can exist before the target page is created.
--
-- target_page_id = NULL
-- target_page_title = 'Authentication'
--
-- Once the page exists, target_page_id can be populated.


CREATE TABLE page_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    source_page_id UUID NOT NULL,

    target_page_id UUID,

    target_page_title VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_link_source
        FOREIGN KEY (source_page_id)
        REFERENCES pages(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_link_target
        FOREIGN KEY (target_page_id)
        REFERENCES pages(id)
        ON DELETE SET NULL,

    CONSTRAINT unique_page_link
        UNIQUE (source_page_id, target_page_title)
);


-- ============================================================
-- INDEXES
-- ============================================================


-- USERS
CREATE INDEX idx_users_role
    ON users(role);


-- FOLDERS
CREATE INDEX idx_folders_parent
    ON folders(parent_folder_id);

CREATE INDEX idx_folders_created_by
    ON folders(created_by);


-- PAGES
CREATE INDEX idx_pages_folder
    ON pages(folder_id);

CREATE INDEX idx_pages_created_by
    ON pages(created_by);

CREATE INDEX idx_pages_updated_at
    ON pages(updated_at DESC);


-- PAGE IMAGES
CREATE INDEX idx_page_images_page
    ON page_images(page_id);

CREATE INDEX idx_page_images_uploaded_by
    ON page_images(uploaded_by);


-- COMMENTS
CREATE INDEX idx_comments_page
    ON comments(page_id);

CREATE INDEX idx_comments_user
    ON comments(user_id);

CREATE INDEX idx_comments_parent
    ON comments(parent_comment_id);

CREATE INDEX idx_comments_block
    ON comments(block_id);

CREATE INDEX idx_comments_status
    ON comments(status);


-- TAGS
CREATE INDEX idx_tags_name
    ON tags(name);


-- PAGE TAGS
CREATE INDEX idx_page_tags_tag
    ON page_tags(tag_id);


-- PAGE LINKS
CREATE INDEX idx_page_links_source
    ON page_links(source_page_id);

CREATE INDEX idx_page_links_target
    ON page_links(target_page_id);


-- ============================================================
-- FULL-TEXT SEARCH
-- ============================================================
-- Searches both page title and Markdown content.

CREATE INDEX idx_pages_full_text_search
ON pages
USING GIN (
    to_tsvector(
        'english',
        COALESCE(title, '') || ' ' || COALESCE(content, '')
    )
);


-- ============================================================
-- UPDATED_AT FUNCTION
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ============================================================
-- UPDATED_AT TRIGGERS
-- ============================================================

CREATE TRIGGER users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER folders_updated_at
BEFORE UPDATE ON folders
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER pages_updated_at
BEFORE UPDATE ON pages
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();


CREATE TRIGGER comments_updated_at
BEFORE UPDATE ON comments
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();
