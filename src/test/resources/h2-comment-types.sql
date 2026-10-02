-- Match PostgreSQL type names without H2's generated enum-domain constraint,
-- whose expression retains the closed Hibernate schema-generation session.
CREATE DOMAIN IF NOT EXISTS comment_status AS VARCHAR(8);
CREATE DOMAIN IF NOT EXISTS user_role AS VARCHAR(16);
