CREATE TABLE comments (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL,
    user_id UUID NOT NULL,
    parent_comment_id UUID,
    block_id UUID NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
