package dev.skycore.journey;

import dev.skycore.progress.JourneyMilestone;

public enum JourneyAction {
    RAIN("cloud_core", JourneyMilestone.RAIN),
    SNOW("frost_core", JourneyMilestone.SNOW),
    STORM("storm_core", JourneyMilestone.STORM),
    MOSS("moss_culture", JourneyMilestone.MOSS),
    ECOSYSTEM("life_medium", JourneyMilestone.ECOSYSTEM),
    COGNITION("cognition_core", JourneyMilestone.COGNITION),
    VILLAGER("villager_embryo", JourneyMilestone.VILLAGER),
    ARCHIVE("civilization_codex", JourneyMilestone.CIVILIZATION);

    private final String item;
    private final JourneyMilestone milestone;
    JourneyAction(String item, JourneyMilestone milestone) { this.item = item; this.milestone = milestone; }
    public String item() { return item; }
    public JourneyMilestone milestone() { return milestone; }
}
