package com.wikigerminare.security.otherfeature;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wikigerminare.security.AdminOnly;

/**
 * Simulates a second, independent backend feature area (standing in for a real future adopter
 * such as pages-api or comments-api) applying {@link AdminOnly} with zero custom
 * permission-checking code, to prove the guard behaves identically wherever it is applied (spec
 * 011-rbac-middleware, User Story 4). Test-only; not part of the production API.
 */
@RestController
public class AdminOnlyOtherFeatureProbeController {

    private final AtomicInteger callCount = new AtomicInteger();

    @AdminOnly
    @GetMapping(value = "/security-test/other-feature/admin-only", produces = MediaType.TEXT_PLAIN_VALUE)
    public String adminOnly() {
        callCount.incrementAndGet();
        return "other-feature-admin-only-ok";
    }

    public int callCount() {
        return callCount.get();
    }

    public void reset() {
        callCount.set(0);
    }
}
