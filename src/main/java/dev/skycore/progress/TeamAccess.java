package dev.skycore.progress;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TeamAccess {
    private TeamAccess() {}

    public static Optional<UUID> team(ServerPlayer player) {
        var server = player.getServer();
        if (server == null || !server.isSameThread() || !FTBTeamsAPI.api().isManagerLoaded()) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).filter(Team::isValid).map(Team::getId);
    }

    public static boolean online(MinecraftServer server, UUID team) {
        return team != null && server.getPlayerList().getPlayers().stream().anyMatch(p -> team(p).filter(team::equals).isPresent());
    }
}
