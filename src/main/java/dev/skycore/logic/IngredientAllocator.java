package dev.skycore.logic;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Optional;

/** Integral bipartite flow: overlapping ingredients must never greedily steal another input. */
public final class IngredientAllocator {
    private IngredientAllocator() {}

    public static Optional<int[]> allocate(int[] available, int[] required, boolean[][] matches) {
        int slots = available.length;
        int demands = required.length;
        int sink = slots + demands + 1;
        int[][] capacity = new int[sink + 1][sink + 1];
        int total = 0;
        for (int s = 0; s < slots; s++) {
            if (available[s] < 0) throw new IllegalArgumentException("Negative inventory");
            capacity[0][s + 1] = available[s];
        }
        for (int d = 0; d < demands; d++) {
            if (required[d] <= 0 || matches[d].length != slots) throw new IllegalArgumentException("Invalid demand");
            total = Math.addExact(total, required[d]);
            capacity[slots + d + 1][sink] = required[d];
            for (int s = 0; s < slots; s++) {
                if (matches[d][s]) capacity[s + 1][slots + d + 1] = required[d];
            }
        }
        int flow = 0;
        while (flow < total) {
            int[] parent = new int[sink + 1];
            Arrays.fill(parent, -1);
            parent[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int from = queue.remove();
                for (int to = 1; to <= sink; to++) {
                    if (parent[to] < 0 && capacity[from][to] > 0) {
                        parent[to] = from;
                        queue.add(to);
                    }
                }
            }
            if (parent[sink] < 0) return Optional.empty();
            int amount = total - flow;
            for (int at = sink; at != 0; at = parent[at]) amount = Math.min(amount, capacity[parent[at]][at]);
            for (int at = sink; at != 0; at = parent[at]) {
                capacity[parent[at]][at] -= amount;
                capacity[at][parent[at]] += amount;
            }
            flow += amount;
        }
        int[] consumed = new int[slots];
        for (int s = 0; s < slots; s++) consumed[s] = available[s] - capacity[0][s + 1];
        return Optional.of(consumed);
    }
}
