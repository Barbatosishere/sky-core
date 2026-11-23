package dev.skycore.api.event;

import net.neoforged.bus.api.Event;

/** Read-only notification; progress is already committed when this event is posted. */
public final class TeamMilestoneGrantedEvent extends Event {
    private final String teamId;
    private final String milestoneId;
    private final int unlockedChapter;

    public TeamMilestoneGrantedEvent(String teamId, String milestoneId, int unlockedChapter) {
        this.teamId = teamId;
        this.milestoneId = milestoneId;
        this.unlockedChapter = unlockedChapter;
    }
    public String getTeamId() { return teamId; }
    public String getMilestoneId() { return milestoneId; }
    public int getUnlockedChapter() { return unlockedChapter; }
}
