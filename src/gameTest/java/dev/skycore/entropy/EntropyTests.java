package dev.skycore.entropy;

import com.google.gson.JsonParser;
import dev.skycore.CoreContent;
import dev.skycore.api.SkyCoreApi;
import dev.skycore.api.entropy.EntropyContract;
import dev.skycore.api.event.TeamEntropyEvidenceGrantedEvent;
import dev.skycore.block.WorkbenchBlockEntity;
import dev.skycore.progress.Milestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("skycore")
@PrefixGameTestTemplate(false)
public final class EntropyTests {
    @GameTest(template = "empty", batch = "entropy_ranges", timeoutTicks = 80)
    public static void baseExpansionOverlapRemovalAndInitialization(GameTestHelper helper) {
        ServerPlayer player = player(helper); var backend = backend(helper); var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertValueEqual(backend.initialize(team(player), level, pos), EntropyBackend.InitializationResult.INITIALIZED, "Initialize actual shelter");
        FieldMachineBlockEntity first = (FieldMachineBlockEntity)level.getBlockEntity(pos);
        int minX = (pos.getX() >> 4) << 4, minZ = (pos.getZ() >> 4) << 4;
        for (int y : new int[]{level.getMinBuildHeight(), level.getMaxBuildHeight() - 1}) {
            helper.assertTrue(backend.protectedAt(level, new BlockPos(minX, y, minZ)), "Full-height low corner");
            helper.assertTrue(backend.protectedAt(level, new BlockPos(minX + 15, y, minZ + 15)), "Full-height high corner");
            helper.assertFalse(backend.protectedAt(level, new BlockPos(minX - 1, y, minZ)), "Outside exact base boundary");
        }
        helper.assertFalse(backend.protectedAt(level, new BlockPos(minX, level.getMaxBuildHeight(), minZ)), "Coverage ends at max build height");
        helper.assertValueEqual(backend.initialize(team(player), level, pos), EntropyBackend.InitializationResult.ALREADY_INITIALIZED, "Same-position retry");
        helper.assertValueEqual(backend.initialize(team(player), level, pos.east()), EntropyBackend.InitializationResult.CONFLICT, "No moving free starter machine");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get(), 2));
        backend.interact(first, player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Accepted exactly one real item");
        helper.assertTrue(backend.hasEvidence(team(player), EntropyContract.WORLD_FUEL_SUPPLIED), "Real supply evidence");
        helper.assertFalse(backend.hasEvidence(team(player), EntropyContract.WORLD_FUEL_PRODUCED), "Supply does not fabricate production");
        helper.assertTrue(backend.protectedAt(level, new BlockPos(minX - 16, pos.getY(), minZ - 16)), "Expanded inclusive corner");
        helper.assertFalse(backend.protectedAt(level, new BlockPos(minX - 17, pos.getY(), minZ)), "Outside expanded boundary");
        FieldMachineBlockEntity second = field(helper, pos.above(), true, player);
        helper.assertFalse(first.machineId().equals(second.machineId()), "Stable distinct machine identities in one chunk");
        UUID firstId = first.machineId();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        helper.assertFalse(backend.data.machines.containsKey(firstId), "Forced removal removes exact ledger entry");
        helper.assertTrue(backend.protectedAt(level, pos), "Overlapping base survives");
        helper.assertFalse(backend.protectedAt(level, new BlockPos(minX - 16, pos.getY(), minZ)), "Removed expansion is withdrawn");
        helper.assertValueEqual(backend.initialize(team(player), level, pos), EntropyBackend.InitializationResult.ALREADY_INITIALIZED, "Destroyed starter marker survives");
        helper.assertTrue(level.getBlockState(pos).isAir(), "Initialize cannot mint a free replacement");
        FieldMachineBlockEntity replacement = field(helper, pos, true, player);
        backend.removed(first);
        helper.assertTrue(backend.data.machines.containsKey(replacement.machineId()), "Stale removal cannot withdraw replacement");
        helper.assertTrue(replacement.getBlockState().getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "Piston immovable");
        var breaking = new BlockEvent.BreakEvent(level, pos, replacement.getBlockState(), player); NeoForge.EVENT_BUS.post(breaking);
        helper.assertTrue(breaking.isCanceled(), "Even owner cannot normally mine shelter");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entropy_permissions", timeoutTicks = 100)
    public static void crossTeamFuelSwitchAndCapacityAreAtomic(GameTestHelper helper) {
        var backend = backend(helper); ServerPlayer owner = player(helper), other = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var inverse = field(helper, pos, true, owner); var machine = backend.data.machines.get(inverse.machineId());
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get()));
        backend.interact(inverse, other);
        helper.assertValueEqual(other.getMainHandItem().getCount(), 1, "Other team keeps rejected fuel");
        helper.assertValueEqual(machine.fuel, 0L, "Other team cannot supply");
        helper.assertFalse(backend.hasEvidence(team(other), EntropyContract.WORLD_FUEL_SUPPLIED), "No rejected evidence");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get(), 64));
        for (int i = 0; i < 64; i++) backend.interact(inverse, owner);
        helper.assertValueEqual(machine.fuel, 76800L, "Exact stored capacity");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get()));
        backend.interact(inverse, owner);
        helper.assertValueEqual(owner.getMainHandItem().getCount(), 1, "Full-capacity rejection preserves item");
        helper.assertValueEqual(machine.fuel, 76800L, "No partial acceptance");
        var entropy = field(helper, pos.above(), false, owner); var entropyRecord = backend.data.machines.get(entropy.machineId());
        helper.assertFalse(entropyRecord.enabled, "Mechanical entropy starts off");
        var hit = new net.minecraft.world.phys.BlockHitResult(entropy.getBlockPos().getCenter(), net.minecraft.core.Direction.UP, entropy.getBlockPos(), false);
        other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); other.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); other.setShiftKeyDown(true);
        helper.assertTrue(other.gameMode.useItemOn(other, helper.getLevel(), other.getMainHandItem(), InteractionHand.MAIN_HAND, hit).consumesAction(), "Cross-team dispatch consumes action without offhand fallback");
        helper.assertFalse(entropyRecord.enabled, "Other team with offhand item cannot enable field through real dispatch");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); owner.setShiftKeyDown(true);
        helper.assertTrue(owner.gameMode.useItemOn(owner, helper.getLevel(), owner.getMainHandItem(), InteractionHand.MAIN_HAND, hit).consumesAction(), "Both hands empty dispatch consumes action");
        helper.assertTrue(entropyRecord.enabled, "Both hands empty toggles exactly once");
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        helper.assertTrue(owner.gameMode.useItemOn(owner, helper.getLevel(), owner.getMainHandItem(), InteractionHand.MAIN_HAND, hit).consumesAction(), "Offhand shield dispatch consumes action");
        helper.assertFalse(entropyRecord.enabled, "Main hand empty with offhand shield toggles exactly once");
        owner.gameMode.useItemOn(owner, helper.getLevel(), owner.getOffhandItem(), InteractionHand.OFF_HAND, hit);
        helper.assertFalse(entropyRecord.enabled, "An explicit offhand dispatch cannot toggle a second time");
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        helper.assertTrue(owner.gameMode.useItemOn(owner, helper.getLevel(), owner.getMainHandItem(), InteractionHand.MAIN_HAND, hit).consumesAction(), "Offhand block dispatch consumes action");
        helper.assertTrue(entropyRecord.enabled, "Main hand empty with offhand stone toggles exactly once");
        helper.assertValueEqual(owner.getOffhandItem().getCount(), 1, "Switch never places or consumes the offhand block");
        helper.assertFalse(backend.hazardous(helper.getLevel(), pos), "Inverse union overrides mechanical entropy");
        helper.assertFalse(SkyCoreApi.entropySnapshotJson(other).contains(inverse.machineId().toString()), "Team snapshot does not expose other team's machines");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entropy_clock", timeoutTicks = 200)
    public static void offlineAndUnloadedMachineClockAndExhaustion(GameTestHelper helper) {
        var backend = backend(helper); ServerPlayer owner = player(helper), other = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2)); var inverse = field(helper, pos, true, owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get())); backend.interact(inverse, owner);
        var machine = backend.data.machines.get(inverse.machineId());
        // Model a genuinely unloaded record: move its chunk out of the loaded lookup without using a BE ticker.
        backend.unloaded(helper.getLevel(), pos.getX() >> 4, pos.getZ() >> 4);
        long start = machine.fuel;
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            helper.assertTrue(machine.fuel <= start - 19 && machine.fuel >= start - 21, "Online owner spends one tick per server tick without loaded machine queue");
            helper.getLevel().getServer().getPlayerList().remove(owner);
        }).thenExecute(() -> {
            long frozen = machine.fuel;
            helper.runAfterDelay(30, () -> {
                helper.assertValueEqual(machine.fuel, frozen, "Other online team cannot advance owner's fuel clock");
                helper.assertTrue(backend.protectedAt(helper.getLevel(), pos.east(16)), "Offline expansion retained");
                // A second owned machine with one tick remaining exercises the actual expiry transition.
                var second = field(helper, pos.above(), true, other); var secondRecord = backend.data.machines.get(second.machineId());
                other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get())); backend.interact(second, other);
                secondRecord.fuel = 1; backend.data.setDirty();
                helper.runAfterDelay(2, () -> {
                    helper.assertValueEqual(secondRecord.fuel, 0L, "Fuel reaches zero without underflow");
                    helper.assertTrue(backend.protectedAt(helper.getLevel(), pos.east(16)), "Expiry preserves other machine's expansion");
                    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                    helper.assertFalse(backend.protectedAt(helper.getLevel(), pos.east(16)), "Only expired base remains after overlapping expansion removed");
                    helper.assertTrue(backend.protectedAt(helper.getLevel(), pos), "Expired machine preserves base");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "entropy_production", timeoutTicks = 700)
    public static void productionRequiresBothCommittedProductsAndPreservesLegacy(GameTestHelper helper) {
        ServerPlayer player = player(helper); var backend = backend(helper); var level = helper.getLevel();
        var progress = TeamProgressData.get(level.getServer()); for (Milestone milestone : Milestone.values()) { progress.grant(team(player), milestone); if (milestone == Milestone.MINERALS_REFINED) break; }
        String legacy = SkyCoreApi.snapshotJson(player);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, CoreContent.STONE_WORKBENCH.get().defaultBlockState(), 2 | 16);
        WorkbenchBlockEntity table = (WorkbenchBlockEntity)level.getBlockEntity(pos); table.bind(team(player));
        table.setItem(0, new ItemStack(CoreContent.MINERAL_RESIDUE.get(), 4)); table.setItem(1, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4));
        table.setItem(2, new ItemStack(Items.QUARTZ)); table.setItem(3, new ItemStack(Items.REDSTONE)); table.setItem(4, new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()));
        table.setItem(5, new ItemStack(EntropyContent.WORLD_FUEL.get(), 64)); table.setItem(6, new ItemStack(EntropyContent.ANTI_ENTROPY_STONE.get(), 63));
        int[] notifications = {0}; Consumer<TeamEntropyEvidenceGrantedEvent> listener = event -> { if (event.getTeamId().equals(team(player).toString())) notifications[0]++; };
        NeoForge.EVENT_BUS.addListener(listener); cleanup(helper, () -> NeoForge.EVENT_BUS.unregister(listener));
        helper.startSequence().thenIdle(210).thenExecute(() -> {
            helper.assertValueEqual(table.getItem(0).getCount(), 4, "Blocked fuel output preserves input");
            helper.assertValueEqual(table.getItem(4).getCount(), 1, "Blocked fuel output preserves heat");
            helper.assertFalse(backend.hasEvidence(team(player), EntropyContract.WORLD_FUEL_PRODUCED), "Legacy mineral milestone is not production");
            table.setItem(5, new ItemStack(EntropyContent.WORLD_FUEL.get(), 63)); table.setItem(6, new ItemStack(EntropyContent.ANTI_ENTROPY_STONE.get(), 64));
        }).thenIdle(210).thenExecute(() -> {
            helper.assertValueEqual(table.getItem(0).getCount(), 4, "Blocked byproduct preserves input");
            helper.assertValueEqual(notifications[0], 0, "No evidence event for blocked batch");
            table.setItem(5, ItemStack.EMPTY); table.setItem(6, ItemStack.EMPTY);
        }).thenWaitUntil(() -> helper.assertTrue(backend.hasEvidence(team(player), EntropyContract.WORLD_FUEL_PRODUCED), "Actual table commits paired outputs"))
                .thenExecute(() -> {
                    helper.assertValueEqual(table.getItem(5).getCount(), 1, "Exactly one world fuel"); helper.assertValueEqual(table.getItem(6).getCount(), 1, "Exactly one byproduct");
                    helper.assertTrue(table.getItem(4).isEmpty(), "Heat consumed once"); helper.assertValueEqual(notifications[0], 1, "Single post-commit event");
                    backend.evidence(team(player), EntropyContract.WORLD_FUEL_PRODUCED); helper.assertValueEqual(notifications[0], 1, "Permanent evidence does not re-notify");
                    helper.assertFalse(backend.hasEvidence(team(player), EntropyContract.WORLD_FUEL_SUPPLIED), "Production cannot imply supply");
                    helper.assertValueEqual(SkyCoreApi.snapshotJson(player), legacy, "New channel preserves old snapshot exactly");
                    helper.assertFalse(EntropyRuntime.productionReady(level.getServer(), EntropyContract.WORLD_FUEL_RECIPE, List.of(new ItemStack(EntropyContent.WORLD_FUEL.get()))), "ID alone cannot forge paired production");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "entropy_hazards", timeoutTicks = 400)
    public static void mobsPlantsPlayerWarningAndEvacuation(GameTestHelper helper) {
        ServerPlayer player = player(helper); var backend = backend(helper); var level = helper.getLevel();
        configureNatural(helper);
        int originalPlants = EntropyConfig.MAX_PLANT_CHECKS_PER_SERVER_TICK.get(); EntropyConfig.MAX_PLANT_CHECKS_PER_SERVER_TICK.set(65536);
        cleanup(helper, () -> EntropyConfig.MAX_PLANT_CHECKS_PER_SERVER_TICK.set(originalPlants));
        BlockPos safe = helper.absolutePos(new BlockPos(2, 2, 2)); var inverse = field(helper, safe, true, player);
        BlockPos danger = new BlockPos(((safe.getX() >> 4) << 4) - 2, safe.getY(), safe.getZ());
        helper.assertFalse(backend.protectedAt(level, danger), "Danger fixture is outside every shelter");
        // Loading test fixtures is explicit QA setup, never part of the danger handler.
        level.getChunk(danger);
        BlockPos plantSafe = (safe.getZ() & 15) == 15 ? safe.north() : safe.south();
        // Random test placement can put either standing position inside the structure's barrier wall/roof.
        for (BlockPos column : List.of(safe, danger, plantSafe))
            for (BlockPos air : BlockPos.betweenClosed(column.above(), column.above(6))) level.setBlock(air, Blocks.AIR.defaultBlockState(), 2 | 16);
        level.setBlock(danger.below(), Blocks.DIRT.defaultBlockState(), 2 | 16); level.setBlock(danger, Blocks.OAK_SAPLING.defaultBlockState(), 2 | 16);
        level.setBlock(plantSafe.below(), Blocks.DIRT.defaultBlockState(), 2 | 16); level.setBlock(plantSafe, Blocks.OAK_SAPLING.defaultBlockState(), 2 | 16);
        helper.assertTrue(backend.protectedAt(level, plantSafe), "Plant fixture stays in the protected chunk even at its edge");
        BlockPos aquatic = danger.south(2); level.setBlock(aquatic, Blocks.SEAGRASS.defaultBlockState(), 2 | 16);
        Mob doomed = EntityType.COW.create(level), kept = EntityType.VILLAGER.create(level);
        doomed.setNoAi(true); doomed.setNoGravity(true); doomed.setPos(danger.getCenter().add(0, 2, 0)); level.addFreshEntity(doomed);
        kept.setNoAi(true); kept.setNoGravity(true); kept.setPos(plantSafe.getCenter().add(0, 2, 0)); level.addFreshEntity(kept);
        cleanup(helper, () -> { doomed.discard(); kept.discard(); });
        player.setNoGravity(true); player.setPos(danger.getCenter().add(0, 3, 0)); player.setHealth(20); player.invulnerableTime = 0;
        helper.assertTrue(level.noCollision(player), "Warning fixture starts in unobstructed air");
        helper.startSequence().thenIdle(90).thenExecute(() -> {
            helper.assertFalse(doomed.isAlive(), "Unprotected mob dies with no warning"); helper.assertTrue(kept.isAlive(), "Sheltered mob survives");
            helper.assertTrue(level.getBlockState(danger).isAir(), "Unprotected tagged plant removed"); helper.assertTrue(level.getBlockState(plantSafe).is(Blocks.OAK_SAPLING), "Sheltered plant retained");
            helper.assertTrue(level.getBlockState(aquatic).is(Blocks.WATER), "Killing a water plant preserves its water");
            helper.assertValueEqual(player.getHealth(), 20F, "Player retains all health during 5-second warning; last damage=" + player.getLastDamageSource());
        }).thenIdle(15).thenExecute(() -> {
            helper.assertValueEqual(player.getHealth(), 19F, "First 1 HP at warning expiry");
        }).thenIdle(20).thenExecute(() -> {
            helper.assertValueEqual(player.getHealth(), 18F, "Next 1 HP after one second");
            player.setPos(safe.getCenter().add(0, 3, 0));
        }).thenIdle(40).thenExecute(() -> {
            helper.assertValueEqual(player.getHealth(), 18F, "Evacuation immediately stops damage"); player.setPos(danger.getCenter().add(0, 3, 0));
        }).thenIdle(90).thenExecute(() -> helper.assertValueEqual(player.getHealth(), 18F, "Reentry grants a new warning")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "entropy_blast", timeoutTicks = 250)
    public static void entropyBlastsHonorWarningBAndProtectedBoundary(GameTestHelper helper) {
        var backend = backend(helper); var level = helper.getLevel(); ServerPlayer player = player(helper);
        configureNatural(helper);
        // Keep the whole blast/standing volume outside randomly placed GameTest walls and other batches.
        BlockPos safe = new BlockPos(262184, 100, 262152); level.getChunk(safe); field(helper, safe, true, player);
        int edgeX = ((safe.getX() >> 4) << 4) - 1; BlockPos blast = new BlockPos(edgeX, safe.getY() + 2, safe.getZ());
        var fixtureChunk = new net.minecraft.world.level.ChunkPos(blast);
        // QA-only ticket: getChunk's transient UNKNOWN ticket need not survive the full warning window.
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, fixtureChunk, 2, fixtureChunk);
        cleanup(helper, () -> level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, fixtureChunk, 2, fixtureChunk));
        for (int cx = (blast.getX() - 16) >> 4; cx <= (blast.getX() + 16) >> 4; cx++) for (int cz = (blast.getZ() - 16) >> 4; cz <= (blast.getZ() + 16) >> 4; cz++) level.getChunk(cx, cz);
        BlockPos protectedBlock = blast.east(), ordinary = blast.west(), stone = blast.north();
        helper.assertFalse(backend.protectedAt(level, ordinary), "Ordinary glass fixture is outside every shelter");
        // The chunk boundary can coincide with a GameTest barrier wall; keep the explosion's origin in air.
        level.setBlock(blast, Blocks.AIR.defaultBlockState(), 2 | 16);
        for (BlockPos air : BlockPos.betweenClosed(blast.offset(-3, 1, -1), blast.offset(-1, 4, 1))) level.setBlock(air, Blocks.AIR.defaultBlockState(), 2 | 16);
        level.setBlock(protectedBlock, Blocks.GLASS.defaultBlockState(), 2 | 16); level.setBlock(ordinary, Blocks.GLASS.defaultBlockState(), 2 | 16);
        level.setBlock(stone, EntropyContent.ANTI_ENTROPY_STONE.get().defaultBlockState(), 2 | 16);
        player.setNoGravity(true); player.setPos(blast.getCenter().add(-2.0, 1.5, 0)); player.setHealth(20); player.invulnerableTime = 0;
        helper.assertTrue(level.noCollision(player), "Blast player has a clear standing volume");
        helper.assertFalse(backend.protectedAt(level, player.blockPosition()), "Blast player starts outside protection");
        helper.startSequence().thenIdle(85).thenExecute(() -> {
            helper.assertValueEqual(player.getHealth(), 20F, "Blast fixture starts without unrelated damage; last damage=" + player.getLastDamageSource());
            helper.assertTrue(backend.blastAreaLoaded(level, blast, 4), "Blast fixture still has its full loaded halo");
            backend.explode(level, blast, 4);
            helper.assertValueEqual(player.getHealth(), 20F, "B: entropy blast cannot damage player during warning");
            helper.assertTrue(level.getBlockState(protectedBlock).is(Blocks.GLASS), "Blast outside shelter cannot break protected glass");
            helper.assertTrue(level.getBlockState(ordinary).isAir(), "Unprotected ordinary block breaks");
            helper.assertTrue(level.getBlockState(stone).is(EntropyContent.ANTI_ENTROPY_STONE.get()), "Anti-entropy stone resists real blast");
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.setPos(blast.getCenter().add(-2.0, 1.5, 0));
        }).thenIdle(30).thenExecute(() -> {
            float before = player.getHealth(); helper.assertTrue(before < 20, "Sustained entropy damage also occurs");
            player.invulnerableTime = 0; backend.explode(level, blast, 4);
            helper.assertTrue(player.getHealth() < before, "B: direct blast damage applies after warning");
            level.setBlock(protectedBlock, Blocks.GLASS.defaultBlockState(), 2 | 16);
            level.explode(null, blast.getX() + 0.5, blast.getY() + 0.5, blast.getZ() + 0.5, 4, Level.ExplosionInteraction.BLOCK);
            helper.assertTrue(level.getBlockState(protectedBlock).is(Blocks.GLASS), "Protection also filters an ordinary external explosion");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "entropy_persistence", timeoutTicks = 100)
    public static void ledgerRoundTripRejectsUnknownSchemasAndReadsWithoutCreatingRecords(GameTestHelper helper) {
        ServerPlayer player = player(helper); var backend = backend(helper); var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2)); backend.initialize(team(player), level, pos);
        var inverse = (FieldMachineBlockEntity)level.getBlockEntity(pos); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get())); backend.interact(inverse, player);
        CompoundTag tag = backend.data.save(new CompoundTag(), level.registryAccess()); var restored = EntropySavedData.load(tag, level.registryAccess());
        helper.assertValueEqual(restored.save(new CompoundTag(), level.registryAccess()), tag, "Canonical NBT round trip");
        helper.assertValueEqual(restored.machines.get(inverse.machineId()).fuel, 1200L, "Accepted fuel survives serialization");
        int teamsBefore = backend.data.teams.size(); backend.teamSnapshot(UUID.randomUUID()); backend.hasEvidence(UUID.randomUUID(), EntropyContract.WORLD_FUEL_SUPPLIED);
        helper.assertValueEqual(backend.data.teams.size(), teamsBefore, "Queries do not create team records");
        BlockPos far = new BlockPos(29_000_000, 64, 29_000_000);
        helper.assertTrue(SkyCoreApi.fieldSnapshotJson(level, far) == null, "Unloaded field query is unavailable");
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Query did not load target chunk");
        CompoundTag future = tag.copy(); future.putInt("SchemaVersion", 2); reject(helper, future, "Future format rejected");
        CompoundTag unversioned = tag.copy(); unversioned.remove("SchemaVersion"); reject(helper, unversioned, "Nonempty unversioned data rejected");
        CompoundTag duplicate = tag.copy(); ListTag machines = duplicate.getList("Machines", 10); machines.add(machines.getCompound(0).copy()); reject(helper, duplicate, "Duplicate UUID rejected");
        CompoundTag negative = tag.copy(); negative.getList("Machines", 10).getCompound(0).putLong("RemainingFuelTicks", -1); reject(helper, negative, "Negative fuel rejected");
        CompoundTag futureBlock = inverse.saveWithFullMetadata(level.registryAccess()); futureBlock.putInt("EntropySchemaVersion", 2); futureBlock.putString("FuturePayload", "retain-me");
        var detached = new FieldMachineBlockEntity(true, pos, inverse.getBlockState()); detached.loadWithComponents(futureBlock, level.registryAccess());
        CompoundTag preserved = detached.saveWithFullMetadata(level.registryAccess());
        helper.assertTrue(detached.invalidLink() && preserved.getInt("EntropySchemaVersion") == 2 && preserved.getString("FuturePayload").equals("retain-me"), "Unknown BE format retained verbatim instead of being dropped by vanilla exception handling");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "entropy_recovery_config", timeoutTicks = 100)
    public static void pendingInitializationAndInvalidConfigRetainAcceptedState(GameTestHelper helper) {
        var backend = backend(helper); var level = helper.getLevel(); ServerPlayer player = player(helper); UUID team = team(player);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16);
        helper.assertValueEqual(backend.initialize(team, level, pos), EntropyBackend.InitializationResult.POSITION_OCCUPIED, "Occupied initialization rejected");
        helper.assertFalse(backend.data.teams.containsKey(team), "Rejected initialization grants no team record");
        BlockPos far = new BlockPos(27_000_000, 64, 27_000_000);
        helper.assertValueEqual(backend.initialize(team, level, far), EntropyBackend.InitializationResult.CHUNK_UNLOADED, "Unloaded initialization rejected");
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Initialize does not load target");
        helper.assertValueEqual(backend.initialize(team, level, pos.atY(level.getMaxBuildHeight())), EntropyBackend.InitializationResult.INVALID_POSITION, "Out-of-height initialization rejected");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        UUID id = UUID.randomUUID(); backend.data.team(team).initialization = new EntropySavedData.Initialization(id, level.dimension().location().toString(), pos, false); backend.data.setDirty();
        var savedPending = EntropySavedData.load(backend.data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertFalse(savedPending.teams.get(team).initialization.committed(), "Pending marker survives persistence");
        try {
            backend.data.checkpoint(level.getServer());
            var onDisk = net.minecraft.nbt.NbtIo.readCompressed(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/skycore_entropy.dat"), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            helper.assertFalse(EntropySavedData.load(onDisk.getCompound("data"), level.registryAccess()).teams.get(team).initialization.committed(), "Pending identity is on disk before block placement");
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
        level.setBlock(pos, EntropyContent.ANTI_ENTROPY_FIELD.get().defaultBlockState(), 2 | 16);
        var inverse = (FieldMachineBlockEntity)level.getBlockEntity(pos); inverse.bind(team); inverse.identify(id);
        helper.assertValueEqual(backend.initialize(team, level, pos), EntropyBackend.InitializationResult.INITIALIZED, "Resume matching pending machine");
        helper.assertValueEqual(inverse.machineId(), id, "Pending completion reuses stable identity");
        ServerPlayer interrupted = player(helper); UUID interruptedId = UUID.randomUUID(); BlockPos interruptedPos = pos.south(3);
        backend.data.team(team(interrupted)).initialization = new EntropySavedData.Initialization(interruptedId, level.dimension().location().toString(), interruptedPos, false);
        backend.data.setDirty();
        backend.reconcile(new ServerEntropyBackend.ChunkKey(level, interruptedPos.getX() >> 4, interruptedPos.getZ() >> 4));
        helper.assertTrue(level.getBlockEntity(interruptedPos) instanceof FieldMachineBlockEntity recovered && interruptedId.equals(recovered.machineId()), "Loaded-chunk reconciliation resumes pending delivery with the same UUID");
        helper.assertTrue(backend.data.teams.get(team(interrupted)).initialization.committed(), "Reconciled pending delivery commits once");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get(), 3));
        backend.interact(inverse, player); backend.interact(inverse, player); backend.interact(inverse, player);
        var record = backend.data.machines.get(id); long accepted = record.fuel;
        int capacity = EntropyConfig.MAX_STORED_FUEL_TICKS.get(), radius = EntropyConfig.EXPANDED_RADIUS_CHUNKS.get();
        cleanup(helper, () -> { EntropyConfig.MAX_STORED_FUEL_TICKS.set(capacity); EntropyConfig.EXPANDED_RADIUS_CHUNKS.set(radius); });
        EntropyConfig.MAX_STORED_FUEL_TICKS.set(1199);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertFalse(SkyCoreApi.entropyRuntimeReady(player), "Cross-invalid config makes runtime unavailable");
            helper.assertTrue(SkyCoreApi.entropySnapshotJson(player) == null, "Unavailable is not empty progress");
            helper.assertValueEqual(record.fuel, accepted, "Invalid config pauses without erasing accepted fuel");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get())); backend.interact(inverse, player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Unavailable runtime rejects fuel without deduction");
            EntropyConfig.MAX_STORED_FUEL_TICKS.set(1200); EntropyConfig.EXPANDED_RADIUS_CHUNKS.set(0);
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(SkyCoreApi.entropyRuntimeReady(player), "Corrected config restores runtime");
            helper.assertTrue(record.fuel > 1200 && record.fuel <= accepted, "Lower valid capacity never clamps stored fuel");
            backend.interact(inverse, player); helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Over-capacity storage rejects complete new unit");
            helper.assertFalse(backend.protectedAt(level, pos.east(16)), "Radius reload rebuilt existing index");
            EntropyConfig.EXPANDED_RADIUS_CHUNKS.set(radius); EntropyConfig.MAX_STORED_FUEL_TICKS.set(capacity);
        }).thenIdle(2).thenExecute(() -> helper.assertTrue(backend.protectedAt(level, pos.east(16)), "Restoring radius restores accepted-fuel coverage")).thenSucceed();
    }

    private static void reject(GameTestHelper helper, CompoundTag tag, String reason) {
        try { EntropySavedData.load(tag, helper.getLevel().registryAccess()); helper.fail(reason); }
        catch (IllegalStateException expected) { /* Expected: failed parsing never reaches data-storage registration. */ }
    }

    @GameTest(template = "empty", batch = "entropy_real_unload", timeoutTicks = 850)
    public static void actualChunkUnloadRetainsCoverageAndSpendsOnlineFuel(GameTestHelper helper) {
        var backend = backend(helper); var level = helper.getLevel(); ServerPlayer player = player(helper);
        BlockPos pos = new BlockPos(-4096, 80, -4096); level.getChunk(pos);
        var inverse = field(helper, pos, true, player); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get())); backend.interact(inverse, player);
        var record = backend.data.machines.get(inverse.machineId()); long[] fuel = {0};
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "Remote chunk actually unloaded"))
                .thenExecute(() -> { fuel[0] = record.fuel; helper.assertTrue(fuel[0] > 0 && fuel[0] < 1200, "Online clock ran before unload"); })
                .thenIdle(20).thenExecute(() -> {
                    helper.assertTrue(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null, "Runtime never reloads machine chunk");
                    helper.assertTrue(record.fuel <= fuel[0] - 19 && record.fuel >= fuel[0] - 21, "Actual unloaded machine still spends online time");
                    helper.assertTrue(backend.protectedAt(level, pos.east(16)), "Unloaded expansion retained");
                    helper.assertTrue(SkyCoreApi.fieldSnapshotJson(level, pos) == null, "Read API does not load it either");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "entropy_budget", timeoutTicks = 220)
    public static void twoDimensionsShareBudgetsWithoutCatchupOrChunkLoads(GameTestHelper helper) {
        var fixture = explosionFixture(helper, 20); var backend = fixture.backend(); var level = helper.getLevel();
        runExplosionTicks(helper, fixture, 100, 20);
        helper.startSequence().thenWaitUntil(() -> helper.assertValueEqual(backend.ticks, 100L, "100 real test ticks completed"))
                .thenExecute(() -> {
            assertBalancedBlasts(helper, fixture, 5);
            // Independently prove real world mutation, rather than only checking scheduler counters.
            BlockPos center = fixture.center(); level.setBlock(center, Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(center.east(), Blocks.GLASS.defaultBlockState(), 2 | 16);
            int[] detonations = {0}; Consumer<net.neoforged.neoforge.event.level.ExplosionEvent.Detonate> listener = event -> {
                if (event.getLevel() == level && event.getExplosion().center().equals(center.getCenter())) detonations[0]++;
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { backend.explode(level, center, 4); } finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(detonations[0], 1, "Loaded fixture reaches the real detonation event exactly once");
            helper.assertTrue(level.getBlockState(center.east()).isAir(), "Real fixture explosion breaks ordinary glass");
            BlockPos far = new BlockPos(28_000_000, 80, 28_000_000);
            helper.assertFalse(backend.blastAreaLoaded(level, far, 4), "Unloaded blast area rejected"); backend.explode(level, far, 4);
            helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Rejected explosion never loads a chunk");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "entropy_default_budget", timeoutTicks = 7300)
    public static void default1200PeriodThreeDimensionsSynchronousExpiryIsFair(GameTestHelper helper) {
        var fixture = explosionFixture(helper, 1200);
        int[] dimensions = {0}; helper.getLevel().getServer().getAllLevels().forEach(level -> dimensions[0]++);
        helper.assertValueEqual(dimensions[0], 3, "Three actual vanilla dimensions participate in ordering");
        runExplosionTicks(helper, fixture, 7200, 1200);
        helper.startSequence().thenWaitUntil(() -> helper.assertValueEqual(fixture.backend().ticks, 7200L, "Six default periods completed in real server ticks"))
                .thenExecute(() -> assertBalancedBlasts(helper, fixture, 6)).thenSucceed();
    }

    private record ExplosionFixture(ServerEntropyBackend backend, net.minecraft.server.level.ServerLevel nether, BlockPos center) {}
    private static ExplosionFixture explosionFixture(GameTestHelper helper, int interval) {
        var level = helper.getLevel(); var nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Real second dimension available");
        BlockPos center = new BlockPos(262152, 200, 262152); var chunk = new net.minecraft.world.level.ChunkPos(center);
        for (var dimension : List.of(level, nether)) {
            dimension.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 2, chunk);
            cleanup(helper, () -> dimension.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.FORCED, chunk, 2, chunk));
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) dimension.getChunk(chunk.x + x, chunk.z + z);
        }
        ServerPlayer first = player(helper), second = player(helper, nether, center.getCenter());
        first.setGameMode(net.minecraft.world.level.GameType.CREATIVE); second.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        first.teleportTo(level, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 0, 0);
        helper.assertTrue(second.serverLevel() == nether, "Legacy login fixture starts in the shared Nether");
        var original = List.copyOf(EntropyConfig.NATURAL_DIMENSIONS.get()); int previousInterval = EntropyConfig.EXPLOSION_INTERVAL_TICKS.get();
        EntropyConfig.NATURAL_DIMENSIONS.set(List.of(level.dimension().location().toString(), nether.dimension().location().toString())); EntropyConfig.EXPLOSION_INTERVAL_TICKS.set(interval);
        cleanup(helper, () -> { EntropyConfig.NATURAL_DIMENSIONS.set(original); EntropyConfig.EXPLOSION_INTERVAL_TICKS.set(previousInterval); });
        // Isolate candidate queues from spawn/other tests. The sampler itself is unchanged: every random
        // position in either queued chunk is hazardous, and its entire blast halo stays explicitly ticketed.
        // This in-memory QA ledger is never registered with SavedData storage or installed in the runtime.
        var backend = new ServerEntropyBackend(level.getServer(), new EntropySavedData());
        for (var dimension : List.of(level, nether)) backend.loaded(dimension, chunk.x, chunk.z);
        var machine = field(helper, center.below(2), false, first); first.setShiftKeyDown(true); EntropyRuntime.interact(machine, first);
        backend.data.team(team(first));
        backend.data.machines.put(machine.machineId(), new EntropySavedData.Machine(machine.machineId(), team(first), level.dimension().location().toString(), machine.getBlockPos(), false, true, 0));
        backend.rebuild();
        return new ExplosionFixture(backend, nether, center);
    }
    private static void runExplosionTicks(GameTestHelper helper, ExplosionFixture fixture, int count, int interval) {
        var backend = fixture.backend();
        helper.onEachTick(() -> {
            if (backend.ticks >= count) return;
            for (var level : List.of(helper.getLevel(), fixture.nether())) {
                // Opposite corners cover every possible chunk touched by any sampled center's radius-4 blast.
                var chunk = new net.minecraft.world.level.ChunkPos(fixture.center());
                for (int x : new int[]{0, 15}) for (int z : new int[]{0, 15})
                    helper.assertTrue(backend.blastAreaLoaded(level, new BlockPos(chunk.getMinBlockX() + x, 80, chunk.getMinBlockZ() + z), 4), "Every possible candidate retains a complete loaded halo");
            }
            backend.tick();
            helper.assertTrue(backend.lastCandidates <= 64 && backend.lastEntities <= 128 && backend.lastPlants <= 256 && backend.lastExplosions <= 1, "Default global budgets unchanged");
            helper.assertValueEqual(backend.lastExplosions, backend.ticks % interval == 0 ? 1 : 0, "Exactly one blast at synchronous expiry; none on idle ticks or as catch-up");
        });
    }
    private static void assertBalancedBlasts(GameTestHelper helper, ExplosionFixture fixture, int expected) {
        var backend = fixture.backend();
        int first = backend.explosionTotals.getOrDefault(helper.getLevel().dimension().location().toString(), 0);
        int second = backend.explosionTotals.getOrDefault(fixture.nether().dimension().location().toString(), 0);
        helper.assertValueEqual(first + second, expected, "One total blast per shared expiry, even with natural and mechanical entropy together");
        helper.assertTrue(first >= expected / 2 && second >= expected / 2 && Math.abs(first - second) <= 1, "Both enabled dimensions receive balanced service: " + first + "/" + second);
    }
    static ServerEntropyBackend backend(GameTestHelper helper) { var backend = EntropyRuntime.raw(helper.getLevel().getServer()); helper.assertTrue(backend != null && backend.available(), "Backend ready"); return backend; }
    static UUID team(ServerPlayer player) { return TeamAccess.team(player).orElseThrow(); }
    static FieldMachineBlockEntity field(GameTestHelper helper, BlockPos pos, boolean inverse, ServerPlayer owner) {
        Block block = inverse ? EntropyContent.ANTI_ENTROPY_FIELD.get() : EntropyContent.ENTROPY_FIELD.get();
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 2 | 16);
        var entity = (FieldMachineBlockEntity)helper.getLevel().getBlockEntity(pos); entity.bind(team(owner)); EntropyRuntime.register(entity);
        helper.assertTrue(entity.machineId() != null, "Machine registered"); return entity;
    }
    static ServerPlayer player(GameTestHelper helper) {
        return player(helper, helper.getLevel(), helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());
    }
    private static ServerPlayer player(GameTestHelper helper, net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "entropy-" + UUID.randomUUID().toString().substring(0, 8)), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        if (level != helper.getLevel()) {
            player.setPos(position);
            var saved = player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(saved);
            var path = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
            try {
                java.nio.file.Files.createDirectories(path.getParent());
                net.minecraft.nbt.NbtIo.writeCompressed(saved, path);
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var embedded = new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.server.getPlayerList().placeNewPlayer(connection, player, cookie); player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // Feed the embedded connection through the normal server network loop, including spawn invulnerability expiry.
        player.server.getConnection().getConnections().add(connection);
        player.setNoGravity(true); player.setPos(position);
        player.getFoodData().setFoodLevel(16);
        helper.onEachTick(() -> {
            if (player.server.getPlayerList().getPlayers().contains(player)) player.serverLevel().getChunkSource().move(player);
            Object packet;
            while ((packet = embedded.readOutbound()) != null) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keepAlive)
                    player.connection.handleKeepAlive(new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keepAlive.getId()));
            }
        });
        player.getInventory().clearContent(); player.getInventory().selected = 8;
        helper.assertTrue(TeamAccess.team(player).isPresent(), "Actual FTB team exists");
        UUID owner = team(player);
        cleanup(helper, () -> {
            if (player.server.getPlayerList().getPlayers().contains(player)) player.server.getPlayerList().remove(player);
            player.server.getConnection().getConnections().remove(connection); connection.disconnect(net.minecraft.network.chat.Component.literal("QA complete"));
        });
        cleanup(helper, () -> {
            var backend = EntropyRuntime.raw(player.server); if (backend == null) return;
            for (var machine : List.copyOf(backend.data.machines.values())) if (machine.owner.equals(owner)) {
                var machineLevel = player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(machine.dimension)));
                if (machineLevel != null) {
                    // Explicit fixture cleanup, including test-created chunks that have really unloaded.
                    machineLevel.getChunk(machine.pos);
                    if (machineLevel.getBlockEntity(machine.pos) instanceof FieldMachineBlockEntity entity && machine.id.equals(entity.machineId()))
                        machineLevel.setBlock(machine.pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        });
        return player;
    }
    public static void configureNatural(GameTestHelper helper) {
        var original = List.copyOf(EntropyConfig.NATURAL_DIMENSIONS.get()); EntropyConfig.NATURAL_DIMENSIONS.set(List.of(helper.getLevel().dimension().location().toString()));
        cleanup(helper, () -> EntropyConfig.NATURAL_DIMENSIONS.set(original));
    }
    static void cleanup(GameTestHelper helper, Runnable action) {
        helper.testInfo.addListener(new GameTestListener() {
            private boolean cleaned;
            private void finish() { if (!cleaned) { cleaned = true; action.run(); } }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { finish(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { finish(); }
            @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { finish(); }
        });
    }
    private EntropyTests() {}
}
