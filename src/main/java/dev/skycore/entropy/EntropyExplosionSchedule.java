package dev.skycore.entropy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Per-dimension effective clocks, with fairness advanced only when a due dimension gets an attempt. */
final class EntropyExplosionSchedule {
    private final Map<String, Integer> clocks = new HashMap<>();
    private int cursor;

    void tick(List<String> dimensions, Predicate<String> active, int interval,
              BooleanSupplier budgetAvailable, Consumer<String> attempt) {
        if (dimensions.isEmpty()) return;
        int start = Math.floorMod(cursor, dimensions.size());
        for (int n = 0; n < dimensions.size(); n++) {
            int index = (start + n) % dimensions.size();
            String id = dimensions.get(index);
            if (!active.test(id)) continue;
            int elapsed = clocks.getOrDefault(id, 0) + 1;
            if (elapsed < interval) { clocks.put(id, elapsed); continue; }
            // Expire this opportunity even when another dimension used the shared budget. Never catch up.
            clocks.put(id, 0);
            if (!budgetAvailable.getAsBoolean()) continue;
            // Idle ticks and budget-denied dimensions cannot undo fairness or phase-lock it to the interval.
            cursor = (index + 1) % dimensions.size();
            attempt.accept(id);
        }
    }
}
