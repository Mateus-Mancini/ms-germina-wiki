CREATE TABLE comments (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL,
    author_id UUID NOT NULL,
    text TEXT NOT NULL,
    anchor_type VARCHAR(100) NOT NULL,
    anchor_value TEXT NOT NULL,
    content_revision VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT comments_status_check CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT comments_text_check CHECK (length(btrim(text)) > 0)
);

CREATE TABLE admin_replies (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL REFERENCES comments (id),
    admin_id UUID NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT admin_replies_text_check CHECK (length(btrim(text)) > 0)
);

CREATE INDEX comments_content_status_created_idx
    ON comments (content_id, status, created_at DESC, id DESC);

CREATE INDEX comments_content_anchor_status_created_idx
    ON comments (content_id, anchor_type, anchor_value, status, created_at DESC, id DESC);

CREATE INDEX admin_replies_comment_created_idx
    ON admin_replies (comment_id, created_at ASC, id ASC);
