package dev.skycore.api;

import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import dev.skycore.entropy.EntropyRuntime;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Stable read-only server-thread API used by the pack. Never creates team records. */
public final class SkyCoreApi {
    private SkyCoreApi() {}

    public static boolean hasMilestone(ServerPlayer player, String milestoneId) {
        return TeamAccess.team(player).map(team -> TeamProgressData.get(player.getServer()).has(team, milestoneId)).orElse(false);
    }

    @Nullable
    public static String snapshotJson(ServerPlayer player) {
        return TeamAccess.team(player).map(team -> TeamProgressData.get(player.getServer()).snapshot(team)).orElse(null);
    }

    /** Call from the logical server thread; false if configuration or persisted data is unavailable. */
    public static boolean entropyRuntimeReady(ServerPlayer player) {
        return EntropyRuntime.ready(player.server) && TeamAccess.team(player).isPresent();
    }

    /** Independent schema 1; null means unavailable, never a request to clear tasks. */
    @Nullable
    public static String entropySnapshotJson(ServerPlayer player) { return EntropyRuntime.teamSnapshot(player); }

    /** Does not load chunks. Returns null for an unavailable runtime or an unloaded location. */
    @Nullable
    public static String fieldSnapshotJson(ServerLevel level, BlockPos pos) { return EntropyRuntime.fieldSnapshot(level, pos); }

    /** Permanent evidence from committed production/supply only; never inferred from old milestones. */
    public static boolean hasEntropyEvidence(ServerPlayer player, String evidenceId) {
        return EntropyRuntime.hasEvidence(player, evidenceId);
    }
}
