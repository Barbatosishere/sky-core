package dev.skycore.entropy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skycore.api.entropy.EntropyContract;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in, QA-source-set-only proof across two normally stopped JVMs using the same QA world. */
@EventBusSubscriber(modid = "skycore")
public final class EntropyRestartProbe {
    private static boolean complete;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) throws Exception {
        String phase = System.getProperty("skycore.entropy.restartPhase", "");
        if (phase.isEmpty() || complete) return;
        var backend = EntropyRuntime.raw(event.getServer()); if (backend == null) throw new IllegalStateException("Restart probe: backend not installed");
        var level = event.getServer().overworld();
        var file = event.getServer().getWorldPath(LevelResource.ROOT).resolve("skycore-entropy-restart-proof.json");
        if (phase.equals("seed")) {
            UUID owner = UUID.randomUUID(); BlockPos pos = new BlockPos(-8192 - (owner.hashCode() & 255) * 16, 80, -8192);
            level.getChunk(pos);
            require(backend.initialize(owner, level, pos).successful(), "Initialization failed");
            var inverse = (FieldMachineBlockEntity)level.getBlockEntity(pos); var record = backend.data.machines.get(inverse.machineId()); record.fuel = 1200;
            BlockPos second = pos.east(); level.setBlock(second, EntropyContent.ENTROPY_FIELD.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            var entropy = (FieldMachineBlockEntity)level.getBlockEntity(second); entropy.bind(owner); require(backend.register(entropy), "Second machine registration failed");
            backend.data.machines.get(entropy.machineId()).enabled = true;
            // These are persistence fixtures. Real acquisition is tested by the workbench and fuel interaction tests.
            backend.evidence(owner, EntropyContract.WORLD_FUEL_PRODUCED); backend.evidence(owner, EntropyContract.WORLD_FUEL_SUPPLIED);
            backend.rebuild(); backend.data.setDirty();
            JsonObject proof = new JsonObject(); proof.addProperty("owner", owner.toString()); proof.addProperty("inverse", inverse.machineId().toString());
            proof.addProperty("entropy", entropy.machineId().toString()); proof.addProperty("pos", pos.asLong()); proof.addProperty("seeded", true);
            Files.writeString(file, proof.toString());
        } else if (phase.equals("verify")) {
            JsonObject proof = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            UUID owner = UUID.fromString(proof.get("owner").getAsString()), inverseId = UUID.fromString(proof.get("inverse").getAsString()), entropyId = UUID.fromString(proof.get("entropy").getAsString());
            BlockPos pos = BlockPos.of(proof.get("pos").getAsLong());
            var inverse = backend.data.machines.get(inverseId); var entropy = backend.data.machines.get(entropyId);
            require(inverse != null && inverse.fuel == 1200 && inverse.owner.equals(owner), "Fuel/owner lost across JVMs");
            require(entropy != null && entropy.enabled && entropy.owner.equals(owner), "Enabled mechanical field lost across JVMs");
            require(backend.data.teams.get(owner).initialization.committed(), "Initialization marker lost");
            require(backend.hasEvidence(owner, EntropyContract.WORLD_FUEL_PRODUCED) && backend.hasEvidence(owner, EntropyContract.WORLD_FUEL_SUPPLIED), "Evidence lost");
            require(backend.protectedAt(level, pos.east(16)), "Persisted expansion index not restored before loading machine");
            require(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "Startup unexpectedly loaded fixture chunk");
            level.getChunk(pos); backend.reconcile(new ServerEntropyBackend.ChunkKey(level, pos.getX() >> 4, pos.getZ() >> 4));
            require(level.getBlockEntity(pos) instanceof FieldMachineBlockEntity entity && inverseId.equals(entity.machineId()) && owner.equals(entity.owner()), "Block link lost across JVMs");
            require(backend.data.machines.containsKey(inverseId) && inverse.fuel == 1200, "Reconciliation removed a valid machine");
            proof.addProperty("verified_after_jvm_restart", true); Files.writeString(file, proof.toString());
        } else throw new IllegalArgumentException("Unknown restart phase " + phase);
        complete = true;
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("Entropy restart probe: " + message); }
}
