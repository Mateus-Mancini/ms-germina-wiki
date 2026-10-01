package com.wikigerminare.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Marks a Spring-managed bean method (or type) as administrator-only.
 *
 * <p>Reuses the project's existing authentication mechanism and its canonical administrator
 * authority, {@code ROLE_ADMIN} (granted by {@code JwtRoleAuthenticationConverter} to
 * authenticated admins). Applying this annotation introduces no new authentication, role, or
 * user model.
 *
 * <p><strong>Contract</strong> (see {@code specs/011-rbac-middleware/contracts/admin-only-guard.md}):
 * <ul>
 *   <li>No valid authenticated identity (anonymous, missing/invalid token) &rarr; HTTP 401; the
 *       annotated method body never executes.</li>
 *   <li>Authenticated, but without {@code ROLE_ADMIN} (including an authenticated caller with no
 *       granted authorities at all) &rarr; HTTP 403; the annotated method body never executes.
 *       The absence of the administrator authority is never treated as permission.</li>
 *   <li>Authenticated and holding {@code ROLE_ADMIN} &rarr; the method body executes normally.</li>
 * </ul>
 *
 * <p>Usage: annotate a controller or service method (the caller's choice) owned by any backend
 * feature:
 * <pre>{@code
 * @AdminOnly
 * @DeleteMapping("/api/pages/{pageId}")
 * public void delete(@PathVariable UUID pageId) { ... }
 * }</pre>
 *
 * <p>This annotation only governs the administrator-only allow/deny decision; it does not
 * perform or replace any feature-specific business authorization (e.g., ownership checks), and it
 * requires {@code @EnableMethodSecurity} to be active (already enabled on
 * {@code com.wikigerminare.config.SecurityConfig}) and the method to be invoked through its
 * Spring proxy (not via a direct {@code this.method(...)} call from inside the same class).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {
}
