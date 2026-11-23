package dev.skycore.logic;

public final class WorldFuelCycle {
    public static final int COLLECTION_TICKS = 1_200;
    public static final int ACTIVATION_TICKS = 3_800;
    public static final int REFINEMENT_TICKS = 4_800;
    public static final int WASHING_TICKS = 30;

    public enum Phase { DAY, NIGHT, TWILIGHT }

    private WorldFuelCycle() {}

    public static Phase phase(int normalSignal, int invertedSignal) {
        if (normalSignal == 0 && invertedSignal > 0) return Phase.NIGHT;
        if (invertedSignal == 0 && normalSignal > 0) return Phase.DAY;
        return Phase.TWILIGHT;
    }

    public static int advance(int progress, int duration, boolean enabled) {
        return enabled && progress < duration ? progress + 1 : progress;
    }

    public static boolean yieldsResidue(int roll) { return roll == 0; }
}
