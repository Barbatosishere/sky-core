package dev.skycore.entropy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.skycore.SkyCore;
import dev.skycore.CoreContent;
import dev.skycore.logic.StackTransactions;
import dev.skycore.api.entropy.EntropyContract;
import dev.skycore.api.event.TeamEntropyEvidenceGrantedEvent;
import dev.skycore.progress.TeamAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Server-thread runtime. All queues contain only loaded objects; all protection comes from the ledger. */
final class ServerEntropyBackend implements EntropyBackend {
    static final ResourceKey<DamageType> EXPOSURE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("skycore", "entropy_exposure"));
    static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("skycore", "entropy_explosion"));
    static final TagKey<Block> PLANTS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("skycore", "entropy_vulnerable_plants"));
    static final TagKey<Block> BLASTPROOF_BLOCKS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("skycore", "entropy_blastproof"));
    record ChunkKey(ServerLevel level, int x, int z) {}
    record Exposure(String dimension, long since) {}
    record Settings(List<? extends String> natural, int expanded, int mechanical, int perItem, int capacity,
                    int explosionInterval, float strength, int explosions, int candidates, int entities, int plants,
                    int warning, int damageInterval, float damage) {
        static Settings read() {
            return new Settings(List.copyOf(EntropyConfig.NATURAL_DIMENSIONS.get()), EntropyConfig.EXPANDED_RADIUS_CHUNKS.get(),
                    EntropyConfig.MECHANICAL_RADIUS_CHUNKS.get(), EntropyConfig.FUEL_TICKS_PER_ITEM.get(), EntropyConfig.MAX_STORED_FUEL_TICKS.get(),
                    EntropyConfig.EXPLOSION_INTERVAL_TICKS.get(), EntropyConfig.EXPLOSION_STRENGTH.get().floatValue(),
                    EntropyConfig.MAX_EXPLOSIONS_PER_SERVER_TICK.get(), EntropyConfig.MAX_CANDIDATE_CHECKS_PER_SERVER_TICK.get(),
                    EntropyConfig.MAX_ENTITY_CHECKS_PER_SERVER_TICK.get(), EntropyConfig.MAX_PLANT_CHECKS_PER_SERVER_TICK.get(),
                    EntropyConfig.PLAYER_WARNING_TICKS.get(), EntropyConfig.PLAYER_DAMAGE_INTERVAL_TICKS.get(), EntropyConfig.PLAYER_DAMAGE.get().floatValue());
        }
        boolean valid() { return capacity >= perItem; }
    }
    final MinecraftServer server;
    final EntropySavedData data;
    private Settings settings;
    private final Map<String, Map<Long, Set<UUID>>> protection = new HashMap<>(), entropy = new HashMap<>();
    private final Map<String, Map<Long, Set<UUID>>> locations = new HashMap<>();
    private final LinkedHashSet<ChunkKey> chunks = new LinkedHashSet<>();
    private final Map<ChunkKey, Integer> plantCursors = new HashMap<>();
    private final LinkedHashSet<Mob> mobs = new LinkedHashSet<>();
    private final Set<ChunkKey> pendingChunks = new LinkedHashSet<>();
    private final Map<UUID, Exposure> exposures = new HashMap<>();
    private final EntropyExplosionSchedule explosionSchedule = new EntropyExplosionSchedule();
    private Set<UUID> online = Set.of();
    long ticks;
    private boolean invalidLinks;
    int lastCandidates, lastEntities, lastPlants, lastExplosions;
    final Map<String, Integer> explosionTotals = new HashMap<>();

    ServerEntropyBackend(MinecraftServer server, EntropySavedData data) {
        this.server = server; this.data = data; this.settings = Settings.read(); rebuild();
        if (!settings.valid()) SkyCore.LOGGER.error("Entropy unavailable: maxStoredFuelTicks < fuelTicksPerItem; accepted fuel retained");
    }
    @Override public boolean available() { return settings.valid() && !invalidLinks; }
    private String dimension(ServerLevel level) { return level.dimension().location().toString(); }
    private static long chunk(int x, int z) { return net.minecraft.world.level.ChunkPos.asLong(Math.floorDiv(x, 16), Math.floorDiv(z, 16)); }
    private static <T> T rotate(LinkedHashSet<T> values) {
        var iterator = values.iterator(); T first = iterator.next(); iterator.remove(); values.add(first); return first;
    }
    private int radius(EntropySavedData.Machine machine) { return machine.inverse ? machine.fuel > 0 ? settings.expanded : 0 : settings.mechanical; }
    private void index(EntropySavedData.Machine machine, boolean add) {
        change(locations, machine.dimension, chunk(machine.pos.getX(), machine.pos.getZ()), machine.id, add);
        if (!machine.inverse && !machine.enabled) return;
        int radius = radius(machine), cx = Math.floorDiv(machine.pos.getX(), 16), cz = Math.floorDiv(machine.pos.getZ(), 16);
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++)
            change(machine.inverse ? protection : entropy, machine.dimension, net.minecraft.world.level.ChunkPos.asLong(x, z), machine.id, add);
    }
    private static void change(Map<String, Map<Long, Set<UUID>>> index, String dimension, long key, UUID id, boolean add) {
        if (add) index.computeIfAbsent(dimension, ignored -> new HashMap<>()).computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
        else {
            Map<Long, Set<UUID>> byChunk = index.get(dimension);
            if (byChunk == null) return;
            Set<UUID> ids = byChunk.get(key); if (ids == null) return;
            ids.remove(id); if (ids.isEmpty()) byChunk.remove(key); if (byChunk.isEmpty()) index.remove(dimension);
        }
    }
    private Set<UUID> at(Map<String, Map<Long, Set<UUID>>> index, ServerLevel level, BlockPos pos) {
        return index.getOrDefault(dimension(level), Map.of()).getOrDefault(chunk(pos.getX(), pos.getZ()), Set.of());
    }
    void rebuild() { protection.clear(); entropy.clear(); locations.clear(); data.machines.values().forEach(machine -> index(machine, true)); }
    boolean protectedAt(ServerLevel level, BlockPos pos) { return !level.isOutsideBuildHeight(pos) && !at(protection, level, pos).isEmpty(); }
    boolean natural(ServerLevel level) { return settings.natural.contains(dimension(level)); }
    boolean mechanical(ServerLevel level, BlockPos pos) {
        return at(entropy, level, pos).stream().map(data.machines::get).anyMatch(machine -> machine != null && TeamAccess.online(server, machine.owner));
    }
    boolean hazardous(ServerLevel level, BlockPos pos) { return (natural(level) || mechanical(level, pos)) && !protectedAt(level, pos); }
    private boolean active(ServerLevel level) { return !level.players().isEmpty(); }

    void loaded(ServerLevel level, int x, int z) {
        ChunkKey key = new ChunkKey(level, x, z); chunks.add(key); pendingChunks.add(key);
    }
    void unloaded(ServerLevel level, int x, int z) {
        ChunkKey key = new ChunkKey(level, x, z); chunks.remove(key); pendingChunks.remove(key); plantCursors.remove(key);
    }
    void joined(Entity entity) { if (entity instanceof Mob mob) mobs.add(mob); }
    void left(Entity entity) { if (entity instanceof Mob mob) mobs.remove(mob); exposures.remove(entity.getUUID()); }

    void reconcile(ChunkKey key) {
        LevelChunk chunk = key.level.getChunkSource().getChunkNow(key.x, key.z); if (chunk == null) return;
        for (var entry : List.copyOf(data.teams.entrySet())) {
            var init = entry.getValue().initialization;
            if (init != null && !init.committed() && init.dimension().equals(dimension(key.level))
                    && init.pos().getX() >> 4 == key.x && init.pos().getZ() >> 4 == key.z) {
                var result = initialize(entry.getKey(), key.level, init.pos());
                if (!result.successful()) SkyCore.LOGGER.error("Pending shelter initialization needs attention at {}: {}", init.pos(), result);
            }
        }
        Set<UUID> ids = locations.getOrDefault(dimension(key.level), Map.of()).getOrDefault(net.minecraft.world.level.ChunkPos.asLong(key.x, key.z), Set.of());
        for (UUID id : List.copyOf(ids)) {
            EntropySavedData.Machine machine = data.machines.get(id);
            if (machine != null && chunk.getBlockEntity(machine.pos) instanceof FieldMachineBlockEntity field && field.invalidLink()) {
                quarantine(field); continue;
            }
            if (machine != null && !matches(machine, chunk.getBlockEntity(machine.pos))) remove(machine);
        }
        for (BlockEntity entity : List.copyOf(chunk.getBlockEntities().values())) if (entity instanceof FieldMachineBlockEntity machine) register(machine);
    }
    private boolean matches(EntropySavedData.Machine machine, BlockEntity entity) {
        return entity instanceof FieldMachineBlockEntity field && machine.id.equals(field.machineId())
                && machine.owner.equals(field.owner()) && machine.inverse == field.inverse();
    }
    boolean register(FieldMachineBlockEntity entity) {
        if (entity.invalidLink()) { quarantine(entity); return false; }
        if (!(entity.getLevel() instanceof ServerLevel level) || entity.owner() == null) return false;
        UUID id = entity.machineId();
        if (id != null) {
            EntropySavedData.Machine old = data.machines.get(id);
            if (old != null) {
                boolean valid = old.dimension.equals(dimension(level)) && old.pos.equals(entity.getBlockPos()) && matches(old, entity);
                if (valid) completePending(old);
                return valid;
            }
            EntropySavedData.Team team = data.teams.get(entity.owner());
            var init = team == null ? null : team.initialization;
            if (init == null || init.committed() || !init.machineId().equals(id) || !init.dimension().equals(dimension(level)) || !init.pos().equals(entity.getBlockPos())) {
                SkyCore.LOGGER.error("Quarantined orphan or copied field machine {} at {} {}", id, dimension(level), entity.getBlockPos()); return false;
            }
        } else { id = UUID.randomUUID(); entity.identify(id); }
        if (at(locations, level, entity.getBlockPos()).stream().map(data.machines::get).anyMatch(machine -> machine.pos.equals(entity.getBlockPos()))) return false;
        var machine = new EntropySavedData.Machine(id, entity.owner(), dimension(level), entity.getBlockPos(), entity.inverse(), entity.inverse(), 0);
        data.team(machine.owner); data.machines.put(id, machine); index(machine, true); completePending(machine); data.setDirty(); return true;
    }
    private void completePending(EntropySavedData.Machine machine) {
        var team = data.teams.get(machine.owner); var init = team == null ? null : team.initialization;
        if (machine.inverse && init != null && !init.committed() && init.machineId().equals(machine.id)
                && init.dimension().equals(machine.dimension) && init.pos().equals(machine.pos)) {
            team.initialization = new EntropySavedData.Initialization(machine.id, machine.dimension, machine.pos, true); data.setDirty();
        }
    }
    private void quarantine(FieldMachineBlockEntity entity) {
        if (!invalidLinks) SkyCore.LOGGER.error("Entropy unavailable: invalid/newer block identity at {}; original NBT and ledger retained", entity.getBlockPos());
        invalidLinks = true;
    }
    private void remove(EntropySavedData.Machine machine) { index(machine, false); data.machines.remove(machine.id); data.setDirty(); }
    void removed(FieldMachineBlockEntity entity) {
        EntropySavedData.Machine machine = data.machines.get(entity.machineId());
        if (machine != null && entity.getLevel() instanceof ServerLevel level && machine.dimension.equals(dimension(level))
                && machine.pos.equals(entity.getBlockPos()) && matches(machine, entity)) remove(machine);
    }
    void interact(FieldMachineBlockEntity entity, ServerPlayer player) {
        if (!entity.mayUse(player)) { message(player, "not_owner"); return; }
        if (!available() || !register(entity)) { message(player, "entropy_not_ready"); return; }
        var machine = data.machines.get(entity.machineId());
        ItemStack held = player.getMainHandItem();
        if (machine.inverse && held.is(Items.FURNACE)) {
            ItemStack result = new ItemStack(CoreContent.WORLD_FUEL_SYNTHESIZER.get());
            if (held.getCount() == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, result);
            } else {
                var planned = StackTransactions.insert(player.getInventory().items, List.of(result));
                if (planned.isEmpty()) { message(player, "synthesizer_inventory_full"); return; }
                for (int slot = 0; slot < planned.get().size(); slot++) player.getInventory().setItem(slot, planned.get().get(slot));
                player.getMainHandItem().shrink(1);
            }
            player.getInventory().setChanged();
            message(player, "synthesizer_obtained");
        } else if (machine.inverse && held.is(EntropyContent.WORLD_FUEL.get())) {
            if (!EntropyRules.accepts(machine.fuel, settings.perItem, settings.capacity)) { message(player, "entropy_fuel_full"); return; }
            // No callbacks between checking and committing the inventory/ledger pair, including in creative mode.
            held.shrink(1); player.getInventory().setChanged();
            index(machine, false); machine.fuel += settings.perItem; index(machine, true); data.setDirty();
            evidence(machine.owner, EntropyContract.WORLD_FUEL_SUPPLIED);
            player.displayClientMessage(Component.translatable("message.skycore.entropy_fuel", machine.fuel), true);
        } else if (!machine.inverse && held.isEmpty() && player.isShiftKeyDown()) {
            index(machine, false); machine.enabled = !machine.enabled; index(machine, true); data.setDirty();
            message(player, machine.enabled ? "entropy_enabled" : "entropy_disabled");
        } else player.displayClientMessage(Component.translatable(machine.inverse ? "message.skycore.entropy_fuel" : "message.skycore.entropy_switch", machine.fuel), true);
    }
    private static void message(ServerPlayer player, String key) { player.displayClientMessage(Component.translatable("message.skycore." + key), true); }
    void evidence(UUID team, String id) {
        if (!EntropyContract.EVIDENCE_IDS.contains(id)) throw new IllegalArgumentException("Unknown entropy evidence");
        if (!data.team(team).evidence.add(id)) return;
        data.setDirty();
        try { NeoForge.EVENT_BUS.post(new TeamEntropyEvidenceGrantedEvent(team.toString(), id)); }
        catch (RuntimeException failure) { SkyCore.LOGGER.error("Entropy evidence listener failed after commit {} {}", team, id, failure); }
    }
    @Override public boolean hasEvidence(UUID team, String id) { return data.teams.containsKey(team) && data.teams.get(team).evidence.contains(id); }

    @Override public InitializationResult initialize(UUID team, ServerLevel level, BlockPos pos) {
        if (!available()) return InitializationResult.NOT_READY;
        var record = data.teams.get(team); var old = record == null ? null : record.initialization;
        if (old != null) {
            if (!old.dimension().equals(dimension(level)) || !old.pos().equals(pos)) return InitializationResult.CONFLICT;
            if (old.committed()) return InitializationResult.ALREADY_INITIALIZED;
        }
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return InitializationResult.INVALID_POSITION;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return InitializationResult.CHUNK_UNLOADED;
        UUID id = old == null ? UUID.randomUUID() : old.machineId();
        if (chunk.getBlockEntity(pos) instanceof FieldMachineBlockEntity entity && id.equals(entity.machineId()) && team.equals(entity.owner())) {
            if (!register(entity)) return InitializationResult.CONFLICT;
        } else {
            if (!chunk.getBlockState(pos).canBeReplaced() || at(locations, level, pos).stream().map(data.machines::get).anyMatch(machine -> machine.pos.equals(pos))) return InitializationResult.POSITION_OCCUPIED;
            data.team(team).initialization = new EntropySavedData.Initialization(id, dimension(level), pos.immutable(), false); data.setDirty();
            try { data.checkpoint(server); }
            catch (java.io.IOException | java.util.concurrent.CompletionException failure) {
                invalidLinks = true;
                SkyCore.LOGGER.error("Entropy unavailable: cannot persist pending initialization before placing a machine", failure);
                return InitializationResult.NOT_READY;
            }
            if (!level.setBlock(pos, EntropyContent.ANTI_ENTROPY_FIELD.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return InitializationResult.POSITION_OCCUPIED;
            if (!(chunk.getBlockEntity(pos) instanceof FieldMachineBlockEntity entity)) return InitializationResult.NOT_READY;
            entity.bind(team); entity.identify(id);
            if (!register(entity)) return InitializationResult.CONFLICT;
        }
        data.team(team).initialization = new EntropySavedData.Initialization(id, dimension(level), pos.immutable(), true); data.setDirty();
        return InitializationResult.INITIALIZED;
    }

    @Override public String teamSnapshot(UUID team) {
        JsonObject root = new JsonObject(); root.addProperty("schema_version", 1); root.addProperty("team_id", team.toString());
        var record = data.teams.get(team); root.addProperty("initialized", record != null && record.initialization != null && record.initialization.committed());
        JsonArray evidence = new JsonArray(); if (record != null) record.evidence.forEach(evidence::add); root.add("evidence", evidence);
        JsonArray machines = new JsonArray();
        data.machines.values().stream().filter(machine -> machine.owner.equals(team)).sorted(Comparator.comparing(machine -> machine.id)).forEach(machine -> {
            JsonObject value = new JsonObject(); value.addProperty("machine_id", machine.id.toString()); value.addProperty("kind", machine.inverse ? "anti_entropy" : "entropy");
            value.addProperty("dimension", machine.dimension); value.addProperty("x", machine.pos.getX()); value.addProperty("y", machine.pos.getY()); value.addProperty("z", machine.pos.getZ());
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(machine.dimension)));
            value.addProperty("loaded", level != null && level.getChunkSource().getChunkNow(machine.pos.getX() >> 4, machine.pos.getZ() >> 4) != null);
            value.addProperty("enabled", machine.enabled); value.addProperty("radius_chunks", radius(machine)); value.addProperty("remaining_fuel_ticks", machine.fuel); machines.add(value);
        });
        root.add("machines", machines); root.addProperty("ecology_implemented", false); return root.toString();
    }
    @Override public String fieldSnapshot(ServerLevel level, BlockPos pos) {
        JsonObject root = new JsonObject(); root.addProperty("schema_version", 1); root.addProperty("dimension", dimension(level));
        root.addProperty("x", pos.getX()); root.addProperty("y", pos.getY()); root.addProperty("z", pos.getZ());
        root.addProperty("natural_entropy", natural(level)); root.addProperty("mechanical_entropy", mechanical(level, pos));
        root.addProperty("protected", protectedAt(level, pos)); root.addProperty("hazardous", hazardous(level, pos));
        JsonArray ids = new JsonArray(); at(protection, level, pos).stream().sorted().forEach(id -> ids.add(id.toString())); root.add("protecting_machine_ids", ids); return root.toString();
    }

    void tick() {
        ticks++;
        Settings updated = Settings.read();
        if (!updated.equals(settings)) {
            settings = updated; rebuild();
            if (!settings.valid()) SkyCore.LOGGER.error("Entropy unavailable: maxStoredFuelTicks < fuelTicksPerItem; accepted fuel retained");
        }
        // Load callbacks can precede FULL. Reconcile only now, using getChunkNow, before any hazards.
        for (ChunkKey key : List.copyOf(pendingChunks)) if (key.level.getChunkSource().getChunkNow(key.x, key.z) != null) { reconcile(key); pendingChunks.remove(key); }
        lastCandidates = lastEntities = lastPlants = lastExplosions = 0;
        if (!available()) return;
        Set<UUID> onlineTeams = new HashSet<>(); server.getPlayerList().getPlayers().forEach(player -> TeamAccess.team(player).ifPresent(onlineTeams::add)); online = onlineTeams;
        for (var machine : data.machines.values()) if (machine.inverse && machine.fuel > 0 && online.contains(machine.owner)) {
            if (machine.fuel == 1) { index(machine, false); machine.fuel = 0; index(machine, true); }
            else machine.fuel--;
            data.setDirty();
        }
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            present.add(player.getUUID());
            if (!exposed(player)) { exposures.remove(player.getUUID()); continue; }
            long elapsed = elapsed(player);
            if (elapsed == 0) player.displayClientMessage(Component.translatable("message.skycore.entropy_warning", (settings.warning + 19L) / 20), true);
            if (EntropyRules.damageDue(elapsed, settings.warning, settings.damageInterval) && settings.damage > 0) player.hurt(source(player.serverLevel(), EXPOSURE), settings.damage);
        }
        exposures.keySet().retainAll(present);
        int entityBudget = Math.min(settings.entities, mobs.size());
        for (int i = 0; i < entityBudget && !mobs.isEmpty(); i++) {
            Mob mob = rotate(mobs); lastEntities++;
            if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel current) || current.getEntity(mob.getId()) != mob) { mobs.remove(mob); continue; }
            if (mob.level() instanceof ServerLevel level && active(level) && level.hasChunkAt(mob.blockPosition()) && hazardous(level, mob.blockPosition())) mob.kill();
        }
        plants(); explosions();
    }
    private boolean exposed(ServerPlayer player) { return !player.isCreative() && !player.isSpectator() && hazardous(player.serverLevel(), player.blockPosition()); }
    private long elapsed(ServerPlayer player) {
        String dimension = dimension(player.serverLevel());
        Exposure previous = exposures.get(player.getUUID());
        if (previous == null || !previous.dimension.equals(dimension)) { previous = new Exposure(dimension, ticks); exposures.put(player.getUUID(), previous); }
        return ticks - previous.since;
    }
    boolean grace(ServerPlayer player) { return !exposed(player) || elapsed(player) < settings.warning; }
    private DamageSource source(ServerLevel level, ResourceKey<DamageType> key) { return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key)); }

    private void plants() {
        if (chunks.isEmpty()) return;
        for (int n = 0; n < settings.plants && !chunks.isEmpty(); n++) {
            ChunkKey key = rotate(chunks); lastPlants++;
            if (!active(key.level)) continue;
            LevelChunk chunk = key.level.getChunkSource().getChunkNow(key.x, key.z); if (chunk == null) continue;
            int cursor = plantCursors.getOrDefault(key, 0), total = key.level.getSectionsCount() * 4096;
            int section = cursor / 4096, local = cursor % 4096;
            var states = chunk.getSection(section);
            if (states.hasOnlyAir() || !states.maybeHas(state -> state.is(PLANTS))) { plantCursors.put(key, ((section + 1) * 4096) % total); continue; }
            BlockPos pos = new BlockPos((key.x << 4) + (local & 15), key.level.getMinBuildHeight() + section * 16 + (local >> 8), (key.z << 4) + ((local >> 4) & 15));
            plantCursors.put(key, (cursor + 1) % total);
            var state = states.getBlockState(local & 15, local >> 8, (local >> 4) & 15);
            if (state.is(PLANTS) && hazardous(key.level, pos))
                key.level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
    private void explosions() {
        Map<String, ServerLevel> levels = new LinkedHashMap<>(); server.getAllLevels().forEach(level -> levels.put(dimension(level), level));
        explosionSchedule.tick(List.copyOf(levels.keySet()), id -> active(levels.get(id)) && (natural(levels.get(id)) || entropy.containsKey(id)),
                settings.explosionInterval, () -> lastExplosions < settings.explosions && lastCandidates < settings.candidates && settings.strength > 0, id -> {
            ServerLevel level = levels.get(id);
            int attempts = Math.min(settings.candidates - lastCandidates, chunks.size());
            for (int i = 0; i < attempts; i++) {
                ChunkKey key = rotate(chunks); lastCandidates++;
                if (key.level != level) continue;
                int x = (key.x << 4) + level.random.nextInt(16), z = (key.z << 4) + level.random.nextInt(16);
                BlockPos pos = new BlockPos(x, level.getMinBuildHeight() + level.random.nextInt(level.getHeight()), z);
                if (!hazardous(level, pos) || !blastAreaLoaded(level, pos, settings.strength)) continue;
                explode(level, pos, settings.strength); lastExplosions++; explosionTotals.merge(id, 1, Integer::sum); break;
            }
        });
    }
    boolean blastAreaLoaded(ServerLevel level, BlockPos pos, float strength) {
        int reach = (int)Math.ceil(strength * 2) + 3;
        for (int x = (pos.getX() - reach) >> 4; x <= (pos.getX() + reach) >> 4; x++)
            for (int z = (pos.getZ() - reach) >> 4; z <= (pos.getZ() + reach) >> 4; z++) if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    void explode(ServerLevel level, BlockPos pos, float strength) {
        if (!blastAreaLoaded(level, pos, strength)) return;
        level.explode(null, source(level, BLAST), new ExplosionDamageCalculator() {
            @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos target, BlockState state, float power) {
                return !state.is(BLASTPROOF_BLOCKS) && super.shouldBlockExplode(explosion, world, target, state, power);
            }

            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return !(entity instanceof ServerPlayer player) || !grace(player);
            }
        }, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, strength, false, Level.ExplosionInteraction.BLOCK);
    }
    void filter(ExplosionEvent.Detonate event) {
        if (!available() || !(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(pos -> protectedAt(level, pos));
        event.getAffectedEntities().removeIf(entity -> protectedAt(level, entity.blockPosition()));
    }
}
