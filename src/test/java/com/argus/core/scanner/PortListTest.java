package com.argus.core.scanner;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortListTest {

    @Test
    void quickIsTop100AndFullIsTop1000() {
        List<Integer> quick = PortList.forProfile("quick");
        assertEquals(100, quick.size());
        assertTrue(quick.contains(80));
        assertTrue(quick.contains(443));

        List<Integer> full = PortList.forProfile("full");
        assertEquals(1000, full.size());
        assertTrue(full.containsAll(quick), "full extends quick");
    }

    @Test
    void unknownProfilesFallBackToQuick() {
        List<Integer> quick = PortList.forProfile("quick");
        assertEquals(quick, PortList.forProfile("nonsense"));
        assertEquals(quick, PortList.forProfile(null));
    }

    /** Round trip through target/classes — assumes no hand-made custom.txt there. */
    @Test
    void customRoundTripThenFallbackWhenDeleted() throws Exception {
        List<Integer> custom = List.of(8081, 22, 443);
        try {
            PortList.saveCustom(custom);
            assertEquals(custom, PortList.forProfile("custom"));
            assertEquals("8081\n22\n443", PortList.customText());
        } finally {
            Files.deleteIfExists(PortList.customPath());
        }
        assertEquals(PortList.forProfile("quick"), PortList.forProfile("custom"),
                "missing custom.txt falls back to quick");
    }
}
