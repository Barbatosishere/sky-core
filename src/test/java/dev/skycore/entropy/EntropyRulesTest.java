package dev.skycore.entropy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntropyRulesTest {
    @Test void baseProtectionHasExactPositiveAndNegativeBoundaries() {
        assertTrue(EntropyRules.covers(0, 0, 15, 15, 0));
        assertFalse(EntropyRules.covers(0, 0, -1, 0, 0));
        assertFalse(EntropyRules.covers(15, 15, 16, 15, 0));
        assertTrue(EntropyRules.covers(-1, -1, -16, -16, 0));
        assertFalse(EntropyRules.covers(-1, -1, -17, -1, 0));
        assertFalse(EntropyRules.covers(-1, -1, 0, -1, 0));
    }
    @Test void expansionIsCenteredOnChunkAndIncludesAllNineChunks() {
        for (int x = -40; x <= 56; x++) for (int z = -40; z <= 56; z++)
            assertEquals(x >= -16 && x < 32 && z >= -16 && z < 32, EntropyRules.covers(15, 1, x, z, 1), x + "," + z);
    }
    @Test void capacityCannotOverflowAndLoweringItNeverAcceptsPartialFuel() {
        assertTrue(EntropyRules.accepts(75600, 1200, 76800));
        assertFalse(EntropyRules.accepts(75601, 1200, 76800));
        assertFalse(EntropyRules.accepts(76800, 1200, 1200));
        assertFalse(EntropyRules.accepts(Long.MAX_VALUE, 1200, Long.MAX_VALUE));
        assertFalse(EntropyRules.accepts(0, 1200, 1199));
    }
    @Test void offlineClockAndExhaustionAreExact() {
        long stored = 1200;
        for (int i = 0; i < 10000; i++) stored = EntropyRules.fuelTick(stored, false);
        assertEquals(1200, stored);
        for (int i = 0; i < 1199; i++) stored = EntropyRules.fuelTick(stored, true);
        assertEquals(1, stored);
        assertEquals(0, EntropyRules.fuelTick(stored, true));
        assertEquals(0, EntropyRules.fuelTick(0, true));
    }
    @Test void warningDamageStartsAt100ThenEvery20Ticks() {
        for (int tick = 0; tick < 100; tick++) assertFalse(EntropyRules.damageDue(tick, 100, 20));
        assertTrue(EntropyRules.damageDue(100, 100, 20));
        assertFalse(EntropyRules.damageDue(119, 100, 20));
        assertTrue(EntropyRules.damageDue(120, 100, 20));
        assertTrue(EntropyRules.damageDue(0, 0, 20));
    }
}
