package dev.skycore.logic;

import org.junit.jupiter.api.Test;

import static dev.skycore.logic.WorldFuelCycle.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

final class WorldFuelCycleTest {
    @Test void onlyZeroOutputEnablesTheCorrespondingPhase() {
        assertEquals(DAY, WorldFuelCycle.phase(15, 0));
        assertEquals(DAY, WorldFuelCycle.phase(1, 0));
        assertEquals(NIGHT, WorldFuelCycle.phase(0, 15));
        assertEquals(NIGHT, WorldFuelCycle.phase(0, 1));
        assertEquals(TWILIGHT, WorldFuelCycle.phase(1, 1));
        assertEquals(TWILIGHT, WorldFuelCycle.phase(0, 0));
    }

    @Test void progressDoesNotAdvanceWhilePausedOrExceedDuration() {
        assertEquals(1_200, WorldFuelCycle.COLLECTION_TICKS);
        assertEquals(3_800, WorldFuelCycle.ACTIVATION_TICKS);
        assertEquals(4_800, WorldFuelCycle.REFINEMENT_TICKS);
        assertEquals(3_799, WorldFuelCycle.advance(3_799, 3_800, false));
        assertEquals(3_800, WorldFuelCycle.advance(3_799, 3_800, true));
        assertEquals(3_800, WorldFuelCycle.advance(3_800, 3_800, true));
    }

    @Test void exactlyOneOfTwentyRollsYieldsResidue() {
        assertTrue(WorldFuelCycle.yieldsResidue(0));
        for (int roll = 1; roll < 20; roll++) assertFalse(WorldFuelCycle.yieldsResidue(roll));
    }
}
