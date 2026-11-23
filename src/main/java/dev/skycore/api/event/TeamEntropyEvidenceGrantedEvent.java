package dev.skycore.api.event;

import net.neoforged.bus.api.Event;

/** Non-cancellable notification after real production or fuel acceptance has committed. */
public final class TeamEntropyEvidenceGrantedEvent extends Event {
    private final String teamId;
    private final String evidenceId;

    public TeamEntropyEvidenceGrantedEvent(String teamId, String evidenceId) {
        this.teamId = teamId;
        this.evidenceId = evidenceId;
    }

    public String getTeamId() { return teamId; }
    public String getEvidenceId() { return evidenceId; }
}
