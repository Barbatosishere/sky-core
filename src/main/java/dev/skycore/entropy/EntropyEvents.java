package dev.skycore.entropy;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Collect early load notifications without querying unfinished chunks. All mutations are serialized on the server. */
final class EntropyEvents {
    private static final Map<ServerLevel, Set<ChunkPos>> loaded = new IdentityHashMap<>();
    private static final Set<Entity> earlyEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    static void register() {
        NeoForge.EVENT_BUS.addListener(EntropyEvents::started);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::stopped);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::tick);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::load);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::unload);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::join);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::leave);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::detonate);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::breaking);
        NeoForge.EVENT_BUS.addListener(EntropyEvents::rightClick);
    }
    private static void execute(MinecraftServer server, Runnable action) { if (server.isSameThread()) action.run(); else server.execute(action); }
    private static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer(); EntropyRuntime.start(server);
        var backend = EntropyRuntime.raw(server); if (backend == null) return;
        loaded.forEach((level, chunks) -> { if (level.getServer() == server) chunks.forEach(pos -> backend.loaded(level, pos.x, pos.z)); });
        earlyEntities.forEach(backend::joined); earlyEntities.clear();
    }
    private static void stopped(ServerStoppedEvent event) {
        EntropyRuntime.clear(event.getServer()); loaded.keySet().removeIf(level -> level.getServer() == event.getServer());
        earlyEntities.removeIf(entity -> entity.level() instanceof ServerLevel level && level.getServer() == event.getServer());
    }
    private static void tick(ServerTickEvent.Post event) { var backend = EntropyRuntime.raw(event.getServer()); if (backend != null) backend.tick(); }
    private static void load(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            execute(level.getServer(), () -> {
                loaded.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos);
                var backend = EntropyRuntime.raw(level.getServer()); if (backend != null) backend.loaded(level, pos.x, pos.z);
            });
        }
    }
    private static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos pos = event.getChunk().getPos();
            execute(level.getServer(), () -> {
                var chunks = loaded.get(level); if (chunks != null) chunks.remove(pos);
                var backend = EntropyRuntime.raw(level.getServer()); if (backend != null) backend.unloaded(level, pos.x, pos.z);
            });
        }
    }
    private static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) execute(level.getServer(), () -> {
            var backend = EntropyRuntime.raw(level.getServer()); if (backend != null) backend.joined(event.getEntity()); else earlyEntities.add(event.getEntity());
        });
    }
    private static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) execute(level.getServer(), () -> {
            earlyEntities.remove(event.getEntity()); var backend = EntropyRuntime.raw(level.getServer()); if (backend != null) backend.left(event.getEntity());
        });
    }
    private static void detonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) { var backend = EntropyRuntime.raw(level.getServer()); if (backend != null) backend.filter(event); }
    }
    private static void breaking(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().isCreative() && event.getLevel().getBlockEntity(event.getPos()) instanceof FieldMachineBlockEntity entity && entity.inverse()) event.setCanceled(true);
    }
    private static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity().getMainHandItem().isEmpty()
                && event.getEntity().isShiftKeyDown() && !event.getUseBlock().isFalse()
                && event.getLevel().getBlockState(event.getPos()).is(EntropyContent.ENTROPY_FIELD.get())) {
            // Vanilla otherwise skips the block when sneaking with any offhand item. Dispatch normally once,
            // on both logical sides; ownership and the mutation remain in the server block/backend path.
            event.setUseBlock(TriState.TRUE);
            event.setUseItem(TriState.FALSE);
        }
    }
    private EntropyEvents() {}
}
