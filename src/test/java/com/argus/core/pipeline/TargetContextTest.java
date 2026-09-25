package com.argus.core.pipeline;

import com.argus.core.model.Target;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetContextTest {

    private final TargetContext ctx = new TargetContext("example.com",
            List.of("93.184.216.0/24", "10.0.0.0/8"));

    @Test
    void domainAndSubdomainsAreInScope() {
        assertTrue(ctx.inScope("example.com"));
        assertTrue(ctx.inScope("www.example.com"));
        assertTrue(ctx.inScope("a.b.example.com"));
        assertTrue(ctx.inScope("WWW.EXAMPLE.COM"));
    }

    @Test
    void suffixLookalikesAreOutOfScope() {
        assertFalse(ctx.inScope("notexample.com"));
        assertFalse(ctx.inScope("example.com.evil.net"));
        assertFalse(ctx.inScope("example.org"));
        assertFalse(ctx.inScope(""));
        assertFalse(ctx.inScope(null));
    }

    @Test
    void cidrMembership() {
        assertTrue(ctx.inScope("93.184.216.34"));
        assertTrue(ctx.inScope("10.1.2.3"));
        assertFalse(ctx.inScope("93.184.217.34"));
        assertFalse(ctx.inScope("11.1.2.3"));
        assertFalse(ctx.inScope("not-an-ip"));
    }

    @Test
    void boundaryPrefixes() {
        TargetContext slash32 = new TargetContext("x.test", List.of("192.168.1.5/32"));
        assertTrue(slash32.inScope("192.168.1.5"));
        assertFalse(slash32.inScope("192.168.1.6"));

        TargetContext slash8 = new TargetContext("x.test", List.of("192.0.0.0/0"));
        assertTrue(slash8.inScope("8.8.8.8"));
    }

    @Test
    void malformedCidrIsIgnored() {
        TargetContext bad = new TargetContext("x.test", List.of("10.0.0.0", "10.0.0.0/99", "junk/24"));
        assertFalse(bad.inScope("10.0.0.1"));
        assertFalse(bad.inScope("anything"));
    }

    @Test
    void ofTargetCopiesDomain() {
        Target t = new Target(null, "Lab", "Example.com", List.of(), "quick", Instant.now());
        assertTrue(TargetContext.of(t).inScope("sub.example.com"));
    }
}
