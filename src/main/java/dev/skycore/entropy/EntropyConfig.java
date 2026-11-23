package dev.skycore.entropy;

import dev.skycore.api.entropy.EntropyContract;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server configuration contract. Reading these values alone never starts entropy hazards. */
public final class EntropyConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NATURAL_DIMENSIONS;
    public static final ModConfigSpec.IntValue EXPANDED_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue MECHANICAL_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue FUEL_TICKS_PER_ITEM;
    public static final ModConfigSpec.IntValue MAX_STORED_FUEL_TICKS;
    public static final ModConfigSpec.IntValue EXPLOSION_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue EXPLOSION_STRENGTH;
    public static final ModConfigSpec.IntValue MAX_EXPLOSIONS_PER_SERVER_TICK;
    public static final ModConfigSpec.IntValue MAX_CANDIDATE_CHECKS_PER_SERVER_TICK;
    public static final ModConfigSpec.IntValue MAX_ENTITY_CHECKS_PER_SERVER_TICK;
    public static final ModConfigSpec.IntValue MAX_PLANT_CHECKS_PER_SERVER_TICK;
    public static final ModConfigSpec.IntValue PLAYER_WARNING_TICKS;
    public static final ModConfigSpec.IntValue PLAYER_DAMAGE_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue PLAYER_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("entropy");
        NATURAL_DIMENSIONS = builder.comment("Opt in only after backup, QA and per-team shelter initialization. Empty disables natural entropy.")
                .defineListAllowEmpty("naturalDimensions", List.<String>of(),
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        EXPANDED_RADIUS_CHUNKS = builder.defineInRange("expandedRadiusChunks", EntropyContract.EXPANDED_RADIUS_CHUNKS, 0, 8);
        MECHANICAL_RADIUS_CHUNKS = builder.defineInRange("mechanicalRadiusChunks", EntropyContract.MECHANICAL_RADIUS_CHUNKS, 0, 8);
        FUEL_TICKS_PER_ITEM = builder.defineInRange("fuelTicksPerItem", EntropyContract.FUEL_TICKS_PER_ITEM, 1, Integer.MAX_VALUE);
        MAX_STORED_FUEL_TICKS = builder.defineInRange("maxStoredFuelTicks", EntropyContract.MAX_STORED_FUEL_TICKS, 1, Integer.MAX_VALUE);
        EXPLOSION_INTERVAL_TICKS = builder.comment("One combined natural/mechanical opportunity per dimension, no catch-up bursts.")
                .defineInRange("explosionIntervalTicks", EntropyContract.EXPLOSION_INTERVAL_TICKS, 20, Integer.MAX_VALUE);
        EXPLOSION_STRENGTH = builder.defineInRange("explosionStrength", EntropyContract.EXPLOSION_STRENGTH, 0, 32);
        MAX_EXPLOSIONS_PER_SERVER_TICK = builder.defineInRange("maxExplosionsPerServerTick", 1, 1, 16);
        MAX_CANDIDATE_CHECKS_PER_SERVER_TICK = builder.defineInRange("maxCandidateChecksPerServerTick", 64, 1, 4096);
        MAX_ENTITY_CHECKS_PER_SERVER_TICK = builder.defineInRange("maxEntityChecksPerServerTick", 128, 1, 65536);
        MAX_PLANT_CHECKS_PER_SERVER_TICK = builder.defineInRange("maxPlantChecksPerServerTick", 256, 1, 65536);
        PLAYER_WARNING_TICKS = builder.defineInRange("playerWarningTicks", EntropyContract.PLAYER_WARNING_TICKS, 0, Integer.MAX_VALUE);
        PLAYER_DAMAGE_INTERVAL_TICKS = builder.defineInRange("playerDamageIntervalTicks", EntropyContract.PLAYER_DAMAGE_INTERVAL_TICKS, 1, Integer.MAX_VALUE);
        PLAYER_DAMAGE = builder.defineInRange("playerDamage", EntropyContract.PLAYER_DAMAGE, 0, 2048);
        builder.pop();
        SPEC = builder.build();
    }

    private EntropyConfig() {}
}
