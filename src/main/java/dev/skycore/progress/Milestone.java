package dev.skycore.progress;

import java.util.Arrays;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum Milestone {
    CORE_EXTRACTED("core_extracted"),
    WORKBENCH_PLACED("workbench_placed"),
    MINERALS_REFINED("minerals_refined"),
    CONDENSATE_OBTAINED("condensate_obtained"),
    LIFE_EMBRYO_CREATED("life_embryo_created"),
    FIRST_TREE("first_tree");

    public static final String STORM = "skycore:ch03/storm_restored";
    public static final String COGNITION = "skycore:ch05/cognition_substrate";
    public static final Codec<String> ID_CODEC = Codec.STRING.validate(id -> known(id)
            ? DataResult.success(id) : DataResult.error(() -> "Unknown Sky Core milestone: " + id));
    private final String id;

    Milestone(String name) { this.id = "skycore:ch01/" + name; }
    public String id() { return id; }
    public Optional<Milestone> previous() { return ordinal() == 0 ? Optional.empty() : Optional.of(values()[ordinal() - 1]); }
    public static Optional<Milestone> find(String id) { return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst(); }
    public static boolean known(String id) { return JourneyMilestone.find(id).isPresent() || find(id).isPresent(); }

    public static Optional<Milestone> forRecipe(String id) {
        return switch (id) {
            case "skycore:ch01/minerals" -> Optional.of(MINERALS_REFINED);
            case "skycore:ch01/condensate" -> Optional.of(CONDENSATE_OBTAINED);
            case "skycore:ch01/life_embryo" -> Optional.of(LIFE_EMBRYO_CREATED);
            case "skycore:ch01/first_tree" -> Optional.of(FIRST_TREE);
            default -> Optional.empty();
        };
    }
}
