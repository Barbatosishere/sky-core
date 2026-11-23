package dev.skycore.progress;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum JourneyMilestone {
    FLIGHT("skycore:ch02/first_flight", 3),
    RAIN("skycore:ch03/rain_restored", 3, FLIGHT),
    SNOW("skycore:ch03/snow_restored", 3, RAIN),
    STORM(Milestone.STORM, 3, RAIN),
    MOSS("skycore:ch03/first_moss", 4, SNOW, STORM),
    ECOSYSTEM("skycore:ch04/ecosystem_created", 5, MOSS),
    COGNITION(Milestone.COGNITION, 5, ECOSYSTEM),
    VILLAGER("skycore:ch05/first_villager", 6, COGNITION),
    FARMER("skycore:ch06/trade_farmer", 6, VILLAGER),
    LIBRARIAN("skycore:ch06/trade_librarian", 6, VILLAGER),
    CLERIC("skycore:ch06/trade_cleric", 6, VILLAGER),
    ARMORER("skycore:ch06/trade_armorer", 6, VILLAGER),
    TOOLSMITH("skycore:ch06/trade_toolsmith", 6, VILLAGER),
    WEAPONSMITH("skycore:ch06/trade_weaponsmith", 6, VILLAGER),
    CIVILIZATION("skycore:ch06/sky_civilization", 6, FARMER, LIBRARIAN, CLERIC, ARMORER, TOOLSMITH, WEAPONSMITH);

    private final String id;
    private final int chapter;
    private final List<JourneyMilestone> prerequisites;

    JourneyMilestone(String id, int chapter, JourneyMilestone... prerequisites) {
        this.id = id;
        this.chapter = chapter;
        this.prerequisites = List.of(prerequisites);
    }

    public String id() { return id; }
    public int chapter() { return chapter; }
    public List<JourneyMilestone> prerequisites() { return prerequisites; }
    public static Optional<JourneyMilestone> find(String id) { return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst(); }
}
