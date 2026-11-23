package dev.skycore.logic;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IngredientAllocatorTest {
    @Test void overlappingIngredientsUseAlternativeSlot() {
        assertArrayEquals(new int[]{1, 1}, IngredientAllocator.allocate(new int[]{1, 1}, new int[]{1, 1},
                new boolean[][]{{true, true}, {true, false}}).orElseThrow());
    }
    @Test void splitsARequirementAcrossStacks() {
        assertArrayEquals(new int[]{2, 3}, IngredientAllocator.allocate(new int[]{2, 3}, new int[]{5},
                new boolean[][]{{true, true}}).orElseThrow());
    }
    @Test void oneItemCannotSatisfyTwoRequirements() {
        assertTrue(IngredientAllocator.allocate(new int[]{1}, new int[]{1, 1}, new boolean[][]{{true}, {true}}).isEmpty());
    }
    @Test void agreesWithIndependentExhaustiveUnitAssignment() {
        Random random = new Random(91427);
        for (int trial = 0; trial < 500; trial++) {
            int[] available = {random.nextInt(4), random.nextInt(4), random.nextInt(4)};
            int[] required = {1 + random.nextInt(3), 1 + random.nextInt(3)};
            boolean[][] matches = new boolean[2][3];
            for (boolean[] row : matches) for (int s = 0; s < 3; s++) row[s] = random.nextBoolean();
            boolean expected = assign(available.clone(), required.clone(), matches, 0);
            var result = IngredientAllocator.allocate(available, required, matches);
            assertEquals(expected, result.isPresent(), "trial " + trial);
            result.ifPresent(consumed -> {
                int sum = 0;
                for (int s = 0; s < available.length; s++) { assertTrue(consumed[s] >= 0 && consumed[s] <= available[s]); sum += consumed[s]; }
                assertEquals(required[0] + required[1], sum);
            });
        }
    }
    private static boolean assign(int[] available, int[] required, boolean[][] matches, int demand) {
        if (demand == required.length) return true;
        if (required[demand] == 0) return assign(available, required, matches, demand + 1);
        for (int slot = 0; slot < available.length; slot++) {
            if (available[slot] > 0 && matches[demand][slot]) {
                available[slot]--; required[demand]--;
                if (assign(available, required, matches, demand)) return true;
                available[slot]++; required[demand]++;
            }
        }
        return false;
    }
}
