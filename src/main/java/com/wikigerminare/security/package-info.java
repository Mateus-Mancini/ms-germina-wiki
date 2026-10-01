/**
 * Project-wide, reusable administrator-authorization guard.
 *
 * <p>This package holds the shared "admin only" mechanism (see {@link com.wikigerminare.security.AdminOnly})
 * that any backend feature package may depend on to mark one of its own operations as
 * administrator-only, without implementing its own permission-checking logic.
 *
 * <p>This package has no dependency on any feature package ({@code pages}, {@code controller},
 * {@code users}, {@code search}, {@code folders}, ...); feature packages depend on it, never the
 * other way around.
 *
 * <p>Full usage contract: {@code specs/011-rbac-middleware/contracts/admin-only-guard.md}.
 */
package com.wikigerminare.security;
