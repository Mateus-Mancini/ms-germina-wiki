package com.wikigerminare.security;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only probe exercising {@link AdminOnly} end to end (spec 011-rbac-middleware). Not part of
 * the production API; used solely by the guard's own test suite to prove the 401/403/allow
 * contract without touching any real feature (pages-api, comments-api, images, ...).
 */
@RestController
public class AdminOnlyProbeController {

    private final AtomicInteger adminOnlyCallCount = new AtomicInteger();
    private final AtomicInteger unprotectedCallCount = new AtomicInteger();

    @AdminOnly
    @GetMapping(value = "/security-test/admin-only", produces = MediaType.TEXT_PLAIN_VALUE)
    public String adminOnly() {
        adminOnlyCallCount.incrementAndGet();
        return "admin-only-ok";
    }

    @GetMapping(value = "/security-test/unprotected", produces = MediaType.TEXT_PLAIN_VALUE)
    public String unprotected() {
        unprotectedCallCount.incrementAndGet();
        return "unprotected-ok";
    }

    public int adminOnlyCallCount() {
        return adminOnlyCallCount.get();
    }

    public int unprotectedCallCount() {
        return unprotectedCallCount.get();
    }

    public void reset() {
        adminOnlyCallCount.set(0);
        unprotectedCallCount.set(0);
    }
}
