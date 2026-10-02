-- Comments attach to block anchors (<!--b:uuid-->) inside page content, and the comments API only
-- accepts an anchor that exists in the page. Pages written outside the web editor (seeds, early
-- contributions) have none, so nobody could comment on them. Give each such page one anchor at the
-- top, which makes the whole page one commentable block until the editor splits it on the next save.
-- The version bump makes an editor still holding the old text get a conflict instead of overwriting.
UPDATE pages
SET content = '<!--b:' || gen_random_uuid() || '-->' || E'\n' || content,
    version = version + 1
WHERE content !~ '<!--b:[0-9a-fA-F-]{36}-->';
