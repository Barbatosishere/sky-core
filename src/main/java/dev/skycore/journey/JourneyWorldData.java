package dev.skycore.journey;

import dev.skycore.SkyCore;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

public final class JourneyWorldData extends SavedData {
    public static final int SLOTS = 64;
    private static final String ID = "skycore_journey_worlds";
    private static final int SCHEMA = 2;
    private static final Map<MinecraftServer, JourneyWorldData> OPEN = new WeakHashMap<>();
    private final Map<UUID, WorldState> teams = new HashMap<>();
    private final boolean available;

    private JourneyWorldData(boolean available) { this.available = available; }

    public static final class WorldState {
        public final int slot;
        public int initialized;
        public long fuel;
        public long starvation;
        public boolean enabled = true;
        int weatherTick;
        private WorldState(int slot) { this.slot = slot; }
    }

    public static JourneyWorldData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Journey requires the server thread");
        synchronized (OPEN) {
            return OPEN.computeIfAbsent(server, JourneyWorldData::open);
        }
    }

    private static JourneyWorldData open(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ID + ".dat");
        try {
            var result = read(path);
            server.overworld().getDataStorage().set(ID, result);
            return result;
        } catch (IOException | RuntimeException failure) {
            SkyCore.LOGGER.error("Cannot open Journey ledger at {}. Team worlds are disabled until the ledger is repaired and the server restarted.", path, failure);
            return new JourneyWorldData(false);
        }
    }

    static JourneyWorldData read(Path path) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return new JourneyWorldData(true);
        }
        if (!attributes.isRegularFile()) throw new IOException("Journey ledger is not a regular file");
        try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
            stream.mark(2);
            boolean compressed = stream.read() == 0x1f && stream.read() == 0x8b;
            stream.reset();
            NbtAccounter quota = NbtAccounter.create(2 * 1024 * 1024);
            CompoundTag envelope = compressed ? NbtIo.readCompressed(stream, quota) : NbtIo.read(new DataInputStream(stream), quota);
            require(envelope.contains("data", Tag.TAG_COMPOUND), "Missing Journey SavedData envelope");
            return load(envelope.getCompound("data"));
        }
    }

    public boolean available() { return available; }

    @Nullable public WorldState state(UUID team) { return teams.get(team); }
    public void forEach(java.util.function.BiConsumer<UUID, WorldState> action) { teams.forEach(action); }
    @Nullable public UUID owner(int slot) { return teams.entrySet().stream().filter(entry -> entry.getValue().slot == slot).map(Map.Entry::getKey).findFirst().orElse(null); }

    @Nullable public WorldState allocate(UUID team) {
        if (!available) return null;
        if (teams.containsKey(team)) return teams.get(team);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (owner(slot) == null) {
                var state = new WorldState(slot);
                teams.put(team, state);
                setDirty();
                return state;
            }
        }
        return null;
    }

    private static JourneyWorldData load(CompoundTag tag) {
        require(tag.contains("SchemaVersion", Tag.TAG_INT), "Missing Journey schema version");
        int schema = tag.getInt("SchemaVersion");
        require(schema == 1 || schema == SCHEMA, "Unsupported Journey schema version " + schema);
        require(tag.get("Teams") instanceof ListTag, "Missing Journey team list");
        var result = new JourneyWorldData(true);
        for (Tag value : (ListTag) tag.get("Teams")) {
            require(value instanceof CompoundTag, "Invalid Journey team list entry");
            CompoundTag entry = (CompoundTag) value;
            require(entry.hasUUID("Id") && entry.contains("Slot", Tag.TAG_INT) && entry.contains("Initialized", Tag.TAG_INT)
                    && entry.contains("Fuel", Tag.TAG_LONG) && entry.contains("Starvation", Tag.TAG_LONG)
                    && entry.contains("Enabled", Tag.TAG_BYTE), "Missing or invalid Journey team fields");
            int slot = entry.getInt("Slot");
            UUID team = entry.getUUID("Id");
            require(slot >= 0 && slot < SLOTS && result.owner(slot) == null && !result.teams.containsKey(team),
                    "Invalid or duplicate Journey world assignment");
            var state = new WorldState(slot);
            state.initialized = entry.getInt("Initialized");
            state.fuel = entry.getLong("Fuel");
            state.starvation = entry.getLong("Starvation");
            state.enabled = entry.getBoolean("Enabled");
            require(state.initialized >= 0 && state.initialized <= 7 && state.fuel >= 0 && state.fuel <= 76_800
                    && state.starvation >= 0 && state.starvation <= 72_000, "Invalid Journey world state");
            if (schema == SCHEMA) {
                require(entry.contains("WeatherTick", Tag.TAG_INT), "Missing Journey weather phase");
                state.weatherTick = entry.getInt("WeatherTick");
                require(state.weatherTick >= 0 && state.weatherTick < JourneyWorlds.WEATHER_PERIOD, "Invalid Journey weather phase");
            }
            result.teams.put(team, state);
        }
        return result;
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        require(available, "Cannot save an unavailable Journey ledger");
        tag.putInt("SchemaVersion", SCHEMA);
        ListTag records = new ListTag();
        teams.forEach((team, state) -> {
            var entry = new CompoundTag();
            entry.putUUID("Id", team); entry.putInt("Slot", state.slot); entry.putInt("Initialized", state.initialized);
            entry.putLong("Fuel", state.fuel); entry.putLong("Starvation", state.starvation); entry.putBoolean("Enabled", state.enabled);
            entry.putInt("WeatherTick", state.weatherTick);
            records.add(entry);
        });
        tag.put("Teams", records);
        return tag;
    }
}
