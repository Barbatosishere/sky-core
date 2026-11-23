package dev.skycore.entropy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntropyExplosionScheduleTest {
    private static final List<String> DIMENSIONS = List.of("overworld", "the_end", "the_nether");

    @Test void default1200PeriodWithThreeDimensionsAndSynchronousExpiryServicesBoth() {
        var schedule = new EntropyExplosionSchedule();
        Map<String, Integer> counts = new HashMap<>();
        for (int tick = 1; tick <= 7200; tick++) {
            int[] spent = {0};
            schedule.tick(DIMENSIONS, id -> !id.equals("the_end"), 1200, () -> spent[0] < 1, id -> {
                spent[0]++; counts.merge(id, 1, Integer::sum);
            });
            assertEquals(tick % 1200 == 0 ? 1 : 0, spent[0], "Only synchronous expiry can use the global budget");
        }
        assertEquals(Map.of("overworld", 3, "the_nether", 3), counts);
    }

    @Test void deniedOpportunityIsDroppedWithoutCatchupOrCursorReversal() {
        var schedule = new EntropyExplosionSchedule();
        var served = new java.util.ArrayList<String>();
        for (int tick = 1; tick <= 2400; tick++) {
            int[] spent = {0};
            schedule.tick(DIMENSIONS, id -> !id.equals("the_end"), 1200, () -> spent[0] == 0, id -> {
                spent[0]++; served.add(id);
            });
            assertEquals(tick / 1200, served.size(), "A denied dimension cannot burst on the next tick");
        }
        assertEquals(List.of("overworld", "the_nether"), served);
    }

    @Test void unsuccessfulSearchUsingAllCandidatesAlsoYieldsTheNextRound() {
        var schedule = new EntropyExplosionSchedule();
        var attempted = new java.util.ArrayList<String>();
        for (int tick = 1; tick <= 2400; tick++) {
            int[] candidates = {0};
            schedule.tick(DIMENSIONS, id -> !id.equals("the_end"), 1200, () -> candidates[0] < 64, id -> {
                candidates[0] = 64; attempted.add(id); // No successful explosion; the shared search budget is spent.
            });
        }
        assertEquals(List.of("overworld", "the_nether"), attempted);
    }
}
