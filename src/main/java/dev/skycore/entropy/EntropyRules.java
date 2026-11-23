package dev.skycore.entropy;

/** Pure rules shared by the runtime and deterministic behavior tests. */
public final class EntropyRules {
    public static boolean covers(int machineX, int machineZ, int x, int z, int radius) {
        return Math.abs((long) Math.floorDiv(machineX, 16) - Math.floorDiv(x, 16)) <= radius
                && Math.abs((long) Math.floorDiv(machineZ, 16) - Math.floorDiv(z, 16)) <= radius;
    }

    public static boolean accepts(long stored, long perItem, long capacity) {
        return stored >= 0 && perItem > 0 && capacity >= perItem && stored <= capacity - perItem;
    }

    public static long fuelTick(long stored, boolean ownerOnline) {
        return ownerOnline && stored > 0 ? stored - 1 : stored;
    }

    public static boolean damageDue(long elapsed, int warning, int interval) {
        return elapsed >= warning && (elapsed - warning) % interval == 0;
    }

    private EntropyRules() {}
}
