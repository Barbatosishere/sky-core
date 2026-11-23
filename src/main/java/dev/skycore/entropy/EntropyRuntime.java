package dev.skycore.entropy;

import dev.skycore.api.entropy.EntropyContract;
import dev.skycore.progress.TeamAccess;
import dev.skycore.SkyCore;
import java.util.UUID;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Read-only public facade and core-internal commit hooks. Never interprets unavailable as safe. */
public final class EntropyRuntime {
    @Nullable private static MinecraftServer boundServer;
    @Nullable private static EntropyBackend backend;

    static void install(MinecraftServer server, EntropyBackend implementation) {
        requireServerThread(server);
        if (backend != null) throw new IllegalStateException("Entropy backend already installed");
        boundServer = server;
        backend = java.util.Objects.requireNonNull(implementation);
    }

    static void clear(MinecraftServer server) {
        if (boundServer == server) { backend = null; boundServer = null; }
    }

    @Nullable private static EntropyBackend backend(MinecraftServer server) {
        requireServerThread(server);
        return boundServer == server && backend != null && backend.available() ? backend : null;
    }

    public static boolean ready(MinecraftServer server) { return backend(server) != null; }

    static ServerEntropyBackend raw(MinecraftServer server) {
        requireServerThread(server);
        return boundServer == server && backend instanceof ServerEntropyBackend implementation ? implementation : null;
    }
    static void start(MinecraftServer server) {
        requireServerThread(server);
        try { install(server, new ServerEntropyBackend(server, EntropySavedData.open(server))); }
        catch (Exception failure) { SkyCore.LOGGER.error("Entropy backend unavailable; source data retained and hazards disabled", failure); }
    }
    public static void register(FieldMachineBlockEntity entity) {
        if (entity.getLevel() instanceof ServerLevel level) {
            ServerEntropyBackend implementation = raw(level.getServer()); if (implementation != null) implementation.register(entity);
        }
    }
    public static void remove(FieldMachineBlockEntity entity) {
        if (entity.getLevel() instanceof ServerLevel level) {
            ServerEntropyBackend implementation = raw(level.getServer()); if (implementation != null) implementation.removed(entity);
        }
    }
    static void interact(FieldMachineBlockEntity entity, ServerPlayer player) {
        ServerEntropyBackend implementation = raw(player.server);
        if (implementation == null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.skycore.entropy_not_ready"), true);
        else implementation.interact(entity, player);
    }
    public static boolean productionReady(MinecraftServer server, String recipeId, List<ItemStack> results) {
        if (!recipeId.equals(EntropyContract.WORLD_FUEL_RECIPE)) return true;
        return ready(server) && results.stream().anyMatch(stack -> !stack.isEmpty() && stack.is(EntropyContent.WORLD_FUEL.get()))
                && results.stream().anyMatch(stack -> !stack.isEmpty() && stack.is(EntropyContent.ANTI_ENTROPY_STONE.get().asItem()));
    }
    public static void productionCommitted(MinecraftServer server, UUID team, String recipeId, List<ItemStack> results) {
        if (recipeId.equals(EntropyContract.WORLD_FUEL_RECIPE) && productionReady(server, recipeId, results))
            raw(server).evidence(team, EntropyContract.WORLD_FUEL_PRODUCED);
    }

    public static void worldFuelRefined(MinecraftServer server, UUID team) {
        if (ready(server)) raw(server).evidence(team, EntropyContract.WORLD_FUEL_PRODUCED);
    }

    @Nullable public static String teamSnapshot(ServerPlayer player) {
        EntropyBackend current = backend(player.server);
        return current == null ? null : TeamAccess.team(player).map(current::teamSnapshot).orElse(null);
    }

    @Nullable public static String fieldSnapshot(ServerLevel level, BlockPos pos) {
        EntropyBackend current = backend(level.getServer());
        return current == null || !level.hasChunkAt(pos) ? null : current.fieldSnapshot(level, pos.immutable());
    }

    public static boolean hasEvidence(ServerPlayer player, String evidenceId) {
        EntropyBackend current = backend(player.server);
        return current != null && evidenceId != null && EntropyContract.EVIDENCE_IDS.contains(evidenceId)
                && TeamAccess.team(player).map(team -> current.hasEvidence(team, evidenceId)).orElse(false);
    }

    static EntropyBackend.InitializationResult initialize(ServerPlayer player, ServerLevel level, BlockPos pos) {
        EntropyBackend current = backend(level.getServer());
        if (current == null || player.server != level.getServer()) return EntropyBackend.InitializationResult.NOT_READY;
        return TeamAccess.team(player).map(team -> current.initialize(team, level, pos.immutable()))
                .orElse(EntropyBackend.InitializationResult.NOT_READY);
    }

    public enum StarterShelterResult { READY, EXISTING_ELSEWHERE, UNAVAILABLE }
    public static StarterShelterResult initializeTeamShelter(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var result = initialize(player, level, pos);
        return result.successful() ? StarterShelterResult.READY
                : result == EntropyBackend.InitializationResult.CONFLICT ? StarterShelterResult.EXISTING_ELSEWHERE : StarterShelterResult.UNAVAILABLE;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Sky Core entropy state requires the server thread");
    }

    private EntropyRuntime() {}
}
