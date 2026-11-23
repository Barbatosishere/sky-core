package dev.skycore.api.entropy;

import java.util.Set;

/** Stable identifiers shared with pack scripts. This class does not enable the runtime. */
public final class EntropyContract {
    public static final int CONTRACT_VERSION = 1;
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int SAVE_SCHEMA_VERSION = 1;
    public static final String ANTI_ENTROPY_FIELD = "skycore:mechanical_anti_entropy_field";
    public static final String ENTROPY_FIELD = "skycore:mechanical_entropy_field";
    public static final String WORLD_FUEL = "skycore:world_fuel";
    public static final String ANTI_ENTROPY_STONE = "skycore:anti_entropy_stone";
    public static final String WORLD_FUEL_RECIPE = "skycore:entropy/world_fuel";
    public static final String ANTI_ENTROPY_FIELD_RECIPE = "skycore:entropy/mechanical_anti_entropy_field";
    public static final String ENTROPY_FIELD_RECIPE = "skycore:entropy/mechanical_entropy_field";
    public static final String WORLD_FUEL_PRODUCED = "skycore:entropy/world_fuel_produced";
    public static final String WORLD_FUEL_SUPPLIED = "skycore:entropy/world_fuel_supplied";
    public static final Set<String> EVIDENCE_IDS = Set.of(WORLD_FUEL_PRODUCED, WORLD_FUEL_SUPPLIED);
    public static final String SAVE_ID = "skycore_entropy";
    public static final int BASE_RADIUS_CHUNKS = 0;
    public static final int EXPANDED_RADIUS_CHUNKS = 1;
    public static final int MECHANICAL_RADIUS_CHUNKS = 0;
    public static final int FUEL_TICKS_PER_ITEM = 1200;
    public static final int MAX_STORED_FUEL_TICKS = 76800;
    public static final int EXPLOSION_INTERVAL_TICKS = 1200;
    public static final double EXPLOSION_STRENGTH = 4;
    public static final int PLAYER_WARNING_TICKS = 100;
    public static final int PLAYER_DAMAGE_INTERVAL_TICKS = 20;
    public static final double PLAYER_DAMAGE = 1;
    public static final int DECAY_DRY_TICKS = 12000;
    public static final int DECAY_GROWTH_STOP_TICKS = 36000;
    public static final int DECAY_FACILITIES_STOP_TICKS = 72000;

    private EntropyContract() {}
}
