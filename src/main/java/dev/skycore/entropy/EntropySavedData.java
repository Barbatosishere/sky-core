package dev.skycore.entropy;

import dev.skycore.api.entropy.EntropyContract;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Canonical ledger. Never uses computeIfAbsent: vanilla swallows deserialization failures. */
public final class EntropySavedData extends SavedData {
    public static final class Machine {
        public final UUID id, owner;
        public final String dimension;
        public final BlockPos pos;
        public final boolean inverse;
        public boolean enabled;
        public long fuel;
        public Machine(UUID id, UUID owner, String dimension, BlockPos pos, boolean inverse, boolean enabled, long fuel) {
            this.id = id; this.owner = owner; this.dimension = dimension; this.pos = pos.immutable();
            this.inverse = inverse; this.enabled = inverse || enabled; this.fuel = fuel;
        }
    }
    public static final class Team {
        public final Set<String> evidence = new TreeSet<>();
        public Initialization initialization;
    }
    public record Initialization(UUID machineId, String dimension, BlockPos pos, boolean committed) {}
    final Map<UUID, Machine> machines = new LinkedHashMap<>();
    final Map<UUID, Team> teams = new LinkedHashMap<>();

    public static EntropySavedData open(MinecraftServer server) throws IOException {
        var path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(EntropyContract.SAVE_ID + ".dat");
        EntropySavedData result;
        if (Files.exists(path)) {
            // readTagFromDisk supports both vanilla compressed and uncompressed envelopes, and propagates IO errors.
            CompoundTag envelope = server.overworld().getDataStorage().readTagFromDisk(EntropyContract.SAVE_ID, null,
                    net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            require(envelope.contains("data", Tag.TAG_COMPOUND), "Missing SavedData envelope");
            result = load(envelope.getCompound("data"), server.registryAccess());
        } else result = new EntropySavedData();
        server.overworld().getDataStorage().set(EntropyContract.SAVE_ID, result);
        return result;
    }

    Team team(UUID id) { return teams.computeIfAbsent(id, ignored -> new Team()); }

    /** Once per initialization: the pending identity must reach disk before placing its block. */
    void checkpoint(MinecraftServer server) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Entropy checkpoint requires the server thread");
        var path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(EntropyContract.SAVE_ID + ".dat");
        Files.createDirectories(path.getParent());
        // NeoForge queues SavedData writes. Drain older snapshots before committing a newer pending marker.
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        CompoundTag envelope = new CompoundTag(); envelope.put("data", save(new CompoundTag(), server.registryAccess()));
        NbtUtils.addCurrentDataVersion(envelope);
        net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(envelope, path);
        setDirty(false);
    }

    public static EntropySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EntropySavedData data = new EntropySavedData();
        if (tag.isEmpty()) return data;
        require(tag.contains("SchemaVersion", Tag.TAG_INT) && tag.getInt("SchemaVersion") == 1, "Unsupported or missing entropy schema");
        require(tag.contains("Teams", Tag.TAG_LIST) && tag.contains("Machines", Tag.TAG_LIST), "Missing entropy lists");
        ListTag teams = tag.getList("Teams", Tag.TAG_COMPOUND);
        require(teams.size() == ((ListTag)tag.get("Teams")).size(), "Invalid team list type");
        for (Tag raw : teams) {
            CompoundTag entry = (CompoundTag) raw;
            UUID id = uuid(entry, "Id");
            require(!data.teams.containsKey(id), "Duplicate team " + id);
            Team team = data.team(id);
            require(entry.contains("Evidence", Tag.TAG_LIST), "Missing evidence");
            ListTag evidence = entry.getList("Evidence", Tag.TAG_STRING);
            require(evidence.size() == ((ListTag)entry.get("Evidence")).size(), "Invalid evidence list");
            for (Tag value : evidence) {
                require(EntropyContract.EVIDENCE_IDS.contains(value.getAsString()), "Unknown evidence " + value);
                require(team.evidence.add(value.getAsString()), "Duplicate evidence");
            }
            if (entry.contains("Initialization")) {
                require(entry.contains("Initialization", Tag.TAG_COMPOUND), "Invalid initialization");
                CompoundTag init = entry.getCompound("Initialization");
                String state = init.getString("State");
                require(state.equals("pending") || state.equals("committed"), "Invalid initialization state");
                team.initialization = new Initialization(uuid(init, "MachineId"), dimension(init), position(init), state.equals("committed"));
            }
        }
        Set<String> positions = new HashSet<>();
        ListTag machines = tag.getList("Machines", Tag.TAG_COMPOUND);
        require(machines.size() == ((ListTag)tag.get("Machines")).size(), "Invalid machine list type");
        for (Tag raw : machines) {
            CompoundTag entry = (CompoundTag) raw;
            String kind = entry.getString("Kind");
            require(kind.equals("anti_entropy") || kind.equals("entropy"), "Invalid machine kind");
            require(entry.contains("Enabled", Tag.TAG_BYTE) && entry.contains("RemainingFuelTicks", Tag.TAG_LONG), "Missing machine state");
            long fuel = entry.getLong("RemainingFuelTicks");
            require(fuel >= 0 && (kind.equals("anti_entropy") || fuel == 0), "Invalid fuel");
            require(!kind.equals("anti_entropy") || entry.getBoolean("Enabled"), "Disabled anti-entropy machine");
            Machine machine = new Machine(uuid(entry, "Id"), uuid(entry, "OwnerTeam"), dimension(entry), position(entry),
                    kind.equals("anti_entropy"), entry.getBoolean("Enabled"), fuel);
            require(data.teams.containsKey(machine.owner), "Missing owning team");
            require(data.machines.putIfAbsent(machine.id, machine) == null, "Duplicate machine UUID");
            require(positions.add(machine.dimension + "/" + machine.pos.asLong()), "Conflicting machine position");
        }
        Set<UUID> initialIds = new HashSet<>();
        data.teams.forEach((teamId, team) -> {
            if (team.initialization == null) return;
            Initialization init = team.initialization;
            require(initialIds.add(init.machineId), "Duplicate initialization machine");
            Machine machine = data.machines.get(init.machineId);
            require(machine == null || machine.inverse && machine.owner.equals(teamId)
                    && machine.dimension.equals(init.dimension) && machine.pos.equals(init.pos), "Initialization/machine conflict");
        });
        return data;
    }

    private static UUID uuid(CompoundTag tag, String key) { require(tag.hasUUID(key), "Invalid UUID " + key); return tag.getUUID(key); }
    private static String dimension(CompoundTag tag) {
        String id = tag.getString("Dimension");
        require(ResourceLocation.tryParse(id) != null, "Invalid dimension"); return id;
    }
    private static BlockPos position(CompoundTag tag) { require(tag.contains("Pos", Tag.TAG_LONG), "Missing position"); return BlockPos.of(tag.getLong("Pos")); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("Sky Core entropy: " + message); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", 1);
        ListTag teamList = new ListTag(), machineList = new ListTag();
        teams.forEach((id, team) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("Id", id);
            ListTag evidence = new ListTag(); team.evidence.forEach(value -> evidence.add(StringTag.valueOf(value)));
            entry.put("Evidence", evidence);
            if (team.initialization != null) {
                Initialization init = team.initialization;
                CompoundTag value = new CompoundTag(); value.putUUID("MachineId", init.machineId);
                value.putString("Dimension", init.dimension); value.putLong("Pos", init.pos.asLong());
                value.putString("State", init.committed ? "committed" : "pending"); entry.put("Initialization", value);
            }
            teamList.add(entry);
        });
        machines.values().forEach(machine -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("Id", machine.id); entry.putUUID("OwnerTeam", machine.owner);
            entry.putString("Kind", machine.inverse ? "anti_entropy" : "entropy"); entry.putString("Dimension", machine.dimension);
            entry.putLong("Pos", machine.pos.asLong()); entry.putBoolean("Enabled", machine.enabled);
            entry.putLong("RemainingFuelTicks", machine.fuel); machineList.add(entry);
        });
        tag.put("Teams", teamList); tag.put("Machines", machineList); return tag;
    }
}
