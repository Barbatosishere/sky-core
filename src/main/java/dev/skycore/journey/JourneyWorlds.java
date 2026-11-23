package dev.skycore.journey;

import dev.skycore.CoreContent;
import dev.skycore.entropy.EntropyRuntime;
import dev.skycore.progress.JourneyMilestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class JourneyWorlds {
    static final int WEATHER_PERIOD = 36_000;
    static final int WET_TICKS = 1_200;

    private JourneyWorlds() {}
    public static ResourceKey<Level> key(int slot, String realm) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("skycore", "team/" + String.format(java.util.Locale.ROOT, "%02d", slot) + "/" + realm));
    }

    public static int slot(ResourceKey<Level> dimension) {
        var id = dimension.location();
        if (!id.getNamespace().equals("skycore") || !id.getPath().matches("team/[0-9]{2}/(sky|nether|end)")) return -1;
        int index = Integer.parseInt(id.getPath().substring(5, 7));
        return index < JourneyWorldData.SLOTS ? index : -1;
    }

    @Nullable public static UUID owner(ServerLevel level) {
        int slot = slot(level.dimension());
        return slot < 0 ? null : JourneyWorldData.get(level.getServer()).owner(slot);
    }

    public static boolean owned(ServerPlayer player, ServerLevel level) {
        UUID owner = owner(level);
        return owner != null && TeamAccess.team(player).filter(owner::equals).isPresent();
    }

    public static int enter(ServerPlayer player, String realm) {
        var transition = destination(player, realm, DimensionTransition.DO_NOTHING);
        if (transition == null) return 0;
        if (player.serverLevel() != transition.newLevel()) return player.changeDimension(transition) == null ? 0 : 1;
        var pos = transition.pos();
        player.teleportTo(transition.newLevel(), pos.x, pos.y, pos.z, 0, 0);
        transition.postDimensionTransition().onTransition(player);
        return 1;
    }

    public static @Nullable String sharedRealm(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.NETHER)) return "nether";
        if (dimension.equals(Level.END)) return "end";
        return null;
    }

    static boolean unlocked(TeamProgressData progress, UUID team, String realm) {
        return switch (realm) {
            case "nether" -> progress.has(team, JourneyMilestone.STORM.id());
            case "end" -> progress.has(team, JourneyMilestone.COGNITION.id());
            default -> true;
        };
    }

    public static @Nullable DimensionTransition portal(Entity entity, ServerLevel source, String realm) {
        if (!(entity instanceof ServerPlayer player)) return null;
        var dimension = source.dimension();
        boolean teamRealm = slot(dimension) >= 0;
        if (teamRealm && !owned(player, source)) return null;
        boolean returning = realm.equals(sharedRealm(dimension)) || teamRealm && dimension.location().getPath().endsWith("/" + realm);
        return destination(player, returning ? "sky" : realm,
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET));
    }

    private static @Nullable DimensionTransition destination(ServerPlayer player, String realm, DimensionTransition.PostDimensionTransition after) {
        var server = player.server;
        UUID team = TeamAccess.team(player).orElse(null);
        if (team == null) return null;
        var data = JourneyWorldData.get(server);
        if (!data.available()) {
            player.displayClientMessage(Component.translatable("message.skycore.journey_ledger_unavailable"), true);
            return null;
        }
        if (!unlocked(TeamProgressData.get(server), team, realm)) {
            player.displayClientMessage(Component.translatable("message.skycore.journey_locked"), true);
            return null;
        }
        var state = data.allocate(team);
        if (state == null) {
            player.displayClientMessage(Component.translatable("message.skycore.journey_full"), false);
            return null;
        }
        ServerLevel target = server.getLevel(key(state.slot, realm));
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.skycore.journey_restart"), true);
            return null;
        }
        int mask = realm.equals("sky") ? 1 : realm.equals("nether") ? 2 : 4;
        if ((state.initialized & mask) == 0) {
            BlockPos center = new BlockPos(8, 100, 8);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, -1, 3))) target.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 4, 3))) target.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (realm.equals("sky")) {
                BlockPos rockPos = center.offset(2, 0, 2);
                target.setBlock(rockPos, CoreContent.ROCK_CORE.get().defaultBlockState(), 3);
                ((dev.skycore.block.OwnedBlockEntity) target.getBlockEntity(rockPos)).bind(team);
                target.getChunk(center);
                var shelter = EntropyRuntime.initializeTeamShelter(player, target, center);
                if (shelter == EntropyRuntime.StarterShelterResult.EXISTING_ELSEWHERE) {
                    player.displayClientMessage(Component.translatable("message.skycore.journey_existing_shelter"), false);
                } else if (shelter != EntropyRuntime.StarterShelterResult.READY) {
                    player.displayClientMessage(Component.translatable("message.skycore.journey_restart"), false);
                    return null;
                }
            }
            state.initialized |= mask;
            data.setDirty();
        }
        if (realm.equals("sky")) {
            after = after.then(entity -> ((ServerPlayer) entity).setRespawnPosition(target.dimension(), new BlockPos(8, 100, 6), 0, true, false));
        }
        return new DimensionTransition(target, new Vec3(8.5, 100, 6.5), Vec3.ZERO, 0, 0, after);
    }

    public static boolean weather(ServerLevel level) {
        if (slot(level.dimension()) < 0) return false;
        UUID owner = owner(level);
        var state = owner == null ? null : JourneyWorldData.get(level.getServer()).state(owner);
        boolean active = state != null && state.enabled && state.starvation < 12_000 && level.dimension().location().getPath().endsWith("/sky");
        var progress = TeamProgressData.get(level.getServer());
        boolean rain = active && state.weatherTick < WET_TICKS && progress.has(owner, JourneyMilestone.RAIN.id());
        boolean thunder = rain && progress.has(owner, JourneyMilestone.STORM.id());
        float oldRain = level.getRainLevel(1), oldThunder = level.getThunderLevel(1);
        level.setRainLevel(rain ? 1 : 0);
        level.setThunderLevel(thunder ? 1 : 0);
        if (oldRain != level.getRainLevel(1) || oldThunder != level.getThunderLevel(1)) {
            for (ServerPlayer player : level.players()) {
                player.connection.send(new ClientboundGameEventPacket(rain ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain ? 1 : 0));
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder ? 1 : 0));
            }
        }
        return true;
    }

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skycore").then(Commands.literal("world")
                .then(Commands.literal("enter").executes(context -> enter(context.getSource().getPlayerOrException(), "sky")))
                .then(Commands.literal("home").executes(context -> enter(context.getSource().getPlayerOrException(), "sky")))
                .then(Commands.literal("nether").executes(context -> enter(context.getSource().getPlayerOrException(), "nether")))
                .then(Commands.literal("end").executes(context -> enter(context.getSource().getPlayerOrException(), "end")))
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    UUID team = TeamAccess.team(player).orElse(null);
                    var state = team == null ? null : JourneyWorldData.get(player.server).state(team);
                    player.displayClientMessage(Component.translatable("message.skycore.journey_status", state == null ? "-" : state.slot,
                            state == null ? 0 : state.fuel, state == null ? 0 : state.starvation), false);
                    return 1;
                }))));
    }
}
