package dev.skycore.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.skycore.CoreContent;
import dev.skycore.api.SkyCoreApi;
import dev.skycore.api.entropy.EntropyContract;
import dev.skycore.entropy.EntropyConfig;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.block.CoreBlock;
import dev.skycore.block.OwnedBlockEntity;
import dev.skycore.block.RockCoreBlockEntity;
import dev.skycore.block.SoilBlockEntity;
import dev.skycore.block.WorkbenchBlockEntity;
import dev.skycore.logic.StackTransactions;
import dev.skycore.progress.Milestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import dev.skycore.recipe.GerminationRecipe;
import dev.skycore.recipe.StoneProcessingRecipe;
import dev.skycore.recipe.WorkbenchInput;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("skycore")
@PrefixGameTestTemplate(false)
public final class ChapterOneTests {
    private static final BlockPos ROCK = new BlockPos(2, 2, 2);
    private static final BlockPos TABLE = new BlockPos(5, 2, 2);
    private static final BlockPos SOIL = new BlockPos(9, 2, 8);

    /** Default-off runtime and initialization must preserve the legacy progression channel. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void entropyRuntimeAndInitializationPreserveLegacyProgress(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        String before = SkyCoreApi.snapshotJson(player);
        BlockPos target = helper.absolutePos(new BlockPos(1, 2, 1));
        var oldState = helper.getLevel().getBlockState(target);
        helper.assertTrue(EntropyConfig.NATURAL_DIMENSIONS.get().isEmpty(), "QA defaults must not opt in a dimension");
        helper.assertTrue(SkyCoreApi.entropyRuntimeReady(player), "Real backend must be installed");
        helper.assertTrue(SkyCoreApi.entropySnapshotJson(player) != null, "Ready team snapshot");
        helper.assertFalse(JsonParser.parseString(SkyCoreApi.fieldSnapshotJson(helper.getLevel(), target)).getAsJsonObject().get("hazardous").getAsBoolean(), "Default does not enable natural entropy");
        helper.assertFalse(SkyCoreApi.hasEntropyEvidence(player, EntropyContract.WORLD_FUEL_SUPPLIED), "Old progress cannot imply real supply");
        String dimension = helper.getLevel().dimension().location().toString();
        helper.assertValueEqual(command(helper, player, "skycore shelter initialize " + player.getGameProfile().getName()
                + " " + dimension + " " + target.getX() + " " + target.getY() + " " + target.getZ()), 1, "Admin initializes shelter");
        helper.assertTrue(helper.getLevel().getBlockState(target).is(EntropyContent.ANTI_ENTROPY_FIELD.get()), "Initialization places the actual machine");
        helper.assertValueEqual(command(helper, player, "skycore shelter initialize " + player.getGameProfile().getName()
                + " " + dimension + " " + target.getX() + " " + target.getY() + " " + target.getZ()), 1, "Retry is idempotent");
        helper.assertValueEqual(SkyCoreApi.snapshotJson(player), before, "Legacy progress remains byte-for-byte unchanged");
        helper.assertTrue(new ItemStack(EntropyContent.ANTI_ENTROPY_FIELD.get()).is(EntropyContent.ANTI_ENTROPY_FIELD.get().asItem()), "Registered anti-entropy BlockItem");
        helper.assertTrue(new ItemStack(EntropyContent.ENTROPY_FIELD.get()).is(EntropyContent.ENTROPY_FIELD.get().asItem()), "Registered entropy BlockItem");
        helper.getLevel().setBlock(target, oldState, 2 | 16);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void entropyDualProductsAndRecoveryRecipeUseExistingCodecs(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getAllRecipesFor(CoreContent.STONE_PROCESSING.get()).stream()
                .filter(holder -> holder.id().toString().equals(EntropyContract.WORLD_FUEL_RECIPE)).findFirst().orElseThrow().value();
        helper.assertValueEqual(recipe.results().size(), 2, "World fuel requires the real byproduct");
        helper.assertTrue(recipe.results().get(0).is(EntropyContent.WORLD_FUEL.get()), "World fuel is distinct from primordial heat fuel");
        helper.assertTrue(recipe.results().get(1).is(EntropyContent.ANTI_ENTROPY_STONE.get().asItem()), "Paired anti-entropy stone output");
        for (int blocked = 0; blocked < 2; blocked++) {
            List<ItemStack> inventory = List.of(new ItemStack(EntropyContent.WORLD_FUEL.get(), blocked == 0 ? 64 : 63),
                    new ItemStack(EntropyContent.ANTI_ENTROPY_STONE.get(), blocked == 1 ? 64 : 63));
            helper.assertTrue(StackTransactions.insert(inventory, recipe.results()).isEmpty(), "Either blocked output rejects the complete pair");
            helper.assertValueEqual(inventory.get(0).getCount(), blocked == 0 ? 64 : 63, "Rejected transaction preserves fuel output slot");
            helper.assertValueEqual(inventory.get(1).getCount(), blocked == 1 ? 64 : 63, "Rejected transaction preserves byproduct slot");
        }
        var recovery = CraftingInput.of(2, 2, List.of(new ItemStack(CoreContent.STONE_FRAGMENT.get()),
                new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()), new ItemStack(CoreContent.MINERAL_RESIDUE.get()),
                new ItemStack(CoreContent.STONE_FRAGMENT.get())));
        var holder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, recovery, level).orElseThrow();
        helper.assertValueEqual(holder.id().toString(), EntropyContract.ANTI_ENTROPY_FIELD_RECIPE, "2x2 renewable recovery needs no machine, water, wood, FE or life");
        helper.assertTrue(holder.value().assemble(recovery, level.registryAccess()).is(EntropyContent.ANTI_ENTROPY_FIELD.get().asItem()), "Recovery creates the registered machine item");
        helper.succeed();
    }

    @GameTest(template = "entropy_start", batch = "entropy_first_chapter", timeoutTicks = 4800, skyAccess = true)
    public static void firstChapterInNaturalEntropyToTrueWorldFuelSupply(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int dx = Math.floorMod(-origin.getX(), 16), dz = Math.floorMod(-origin.getZ(), 16);
        BlockPos rock = ROCK.offset(dx, 0, dz), tablePos = TABLE.offset(dx, 0, dz), soilPos = SOIL.offset(dx, 0, dz);
        BlockPos shelter = new BlockPos(dx, 2, dz), shelterAbsolute = helper.absolutePos(shelter);
        dev.skycore.entropy.EntropyTests.configureNatural(helper);
        String dimension = helper.getLevel().dimension().location().toString();
        helper.assertValueEqual(command(helper, player, "skycore shelter initialize @s " + dimension + " "
                + shelterAbsolute.getX() + " " + shelterAbsolute.getY() + " " + shelterAbsolute.getZ()), 1, "Start with one real unfueled shelter");
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanupShelter() { helper.getLevel().setBlock(shelterAbsolute, Blocks.AIR.defaultBlockState(), 2 | 16); }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanupShelter(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanupShelter(); }
            @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { cleanupShelter(); }
        });
        RockCoreBlockEntity core = place(helper, rock, CoreContent.ROCK_CORE.get(), player);
        var sequence = helper.startSequence();
        for (int i = 0; i < 28; i++) {
            sequence.thenExecute(() -> {
                helper.assertTrue(core.collect(player), "Core harvest should fit and succeed");
                helper.assertFalse(core.collect(player), "Duplicate harvest during cooldown must fail");
            }).thenIdle(20);
        }
        sequence.thenExecute(() -> {
            helper.assertValueEqual(count(player, CoreContent.MINERAL_RESIDUE.get()), 28, "Renewable residue");
            for (int i = 0; i < 6; i++) craftBackpack(helper, player, "primordial_fuel", CoreContent.MINERAL_RESIDUE.get());
            craftBackpack(helper, player, "stone_workbench", CoreContent.STONE_FRAGMENT.get());
            take(player, CoreContent.STONE_WORKBENCH.get().asItem(), 1);
            WorkbenchBlockEntity table = place(helper, tablePos, CoreContent.STONE_WORKBENCH.get(), player);
            load(player, table, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 12), new ItemStack(CoreContent.PRIMORDIAL_FUEL.get(), 3));
        }).thenWaitUntil(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            helper.assertValueEqual(table.getItem(5).getCount(), 3, "Three quartz batches");
            helper.assertValueEqual(table.getItem(6).getCount(), 3, "Three redstone batches");
        }).thenExecute(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            unload(player, table);
            load(player, table, List.of(new ItemStack(Items.QUARTZ, 2), new ItemStack(Items.REDSTONE, 2)), new ItemStack(CoreContent.PRIMORDIAL_FUEL.get(), 2));
        }).thenWaitUntil(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            helper.assertValueEqual(table.getItem(5).getCount(), 4, "Four condensate");
        }).thenExecute(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            unload(player, table);
            load(player, table, List.of(new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4), new ItemStack(CoreContent.MINERAL_RESIDUE.get(), 4),
                    new ItemStack(CoreContent.CONDENSATE.get(), 2)), ItemStack.EMPTY);
        }).thenWaitUntil(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            helper.assertValueEqual(table.getItem(5).getCount(), 2, "Two soil blocks");
        }).thenExecute(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            unload(player, table);
            load(player, table, List.of(new ItemStack(CoreContent.MINERAL_SOIL.get()), new ItemStack(CoreContent.CONDENSATE.get()),
                    new ItemStack(Items.QUARTZ), new ItemStack(Items.REDSTONE)), new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()));
        }).thenWaitUntil(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            helper.assertTrue(table.getItem(5).is(CoreContent.LIFE_EMBRYO.get()), "Actual embryo output");
        }).thenExecute(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            unload(player, table);
            take(player, CoreContent.MINERAL_SOIL.get().asItem(), 1);
            SoilBlockEntity soil = place(helper, soilPos, CoreContent.MINERAL_SOIL.get(), player);
            player.setItemInHand(InteractionHand.MAIN_HAND, take(player, CoreContent.LIFE_EMBRYO.get(), 1));
            player.setItemInHand(InteractionHand.OFF_HAND, take(player, CoreContent.CONDENSATE.get(), 1));
            helper.assertTrue(soil.begin(player), "Owned soil must accept the earned embryo");
            helper.assertTrue(soil.begin(player), "Repeated use must not charge another embryo");
        }).thenWaitUntil(() -> helper.assertTrue(SkyCoreApi.hasMilestone(player, Milestone.FIRST_TREE.id()), "Tree commit must grant the real team milestone"))
                .thenExecute(() -> {
                    for (Milestone milestone : Milestone.values()) helper.assertTrue(SkyCoreApi.hasMilestone(player, milestone.id()), milestone.id());
                    helper.assertBlockPresent(Blocks.OAK_LOG, soilPos.above());
                    helper.assertBlockPresent(Blocks.OAK_LEAVES, soilPos.above(5));
                    helper.assertValueEqual(count(player, CoreContent.STONE_FRAGMENT.get()), 36, "Exact leftover raw material");
                    helper.assertTrue(((SoilBlockEntity) helper.getBlockEntity(soilPos)).isEmpty(), "Committed escrow empty");
                    helper.assertValueEqual(JsonParser.parseString(SkyCoreApi.snapshotJson(player)).getAsJsonObject().get("unlocked_chapter").getAsInt(), 2, "Chapter 2 unlocked");
                    var data = TeamProgressData.get(helper.getLevel().getServer());
                    var reloaded = TeamProgressData.load(disk(helper, data.save(new CompoundTag(), helper.getLevel().registryAccess()), "progress"), helper.getLevel().registryAccess());
                    UUID team = TeamAccess.team(player).orElseThrow();
                    helper.assertValueEqual(reloaded.snapshot(team), data.snapshot(team), "Team progress disk round trip");
                });
        for (int i = 0; i < 12; i++) sequence.thenExecute(() -> helper.assertTrue(core.collect(player), "Renewable post-tree collection")).thenIdle(20);
        sequence.thenExecute(() -> {
            craftBackpack(helper, player, "primordial_fuel", CoreContent.MINERAL_RESIDUE.get());
            craftBackpack(helper, player, "primordial_fuel", CoreContent.MINERAL_RESIDUE.get());
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            load(player, table, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4), new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()));
        }).thenWaitUntil(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos);
            helper.assertTrue(table.getItem(5).is(Items.QUARTZ) && table.getItem(6).is(Items.REDSTONE), "Earn fresh minerals for world fuel");
        }).thenExecute(() -> {
            WorkbenchBlockEntity table = helper.getBlockEntity(tablePos); unload(player, table);
            load(player, table, List.of(new ItemStack(CoreContent.MINERAL_RESIDUE.get(), 4), new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4),
                    new ItemStack(Items.QUARTZ), new ItemStack(Items.REDSTONE)), new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()));
        }).thenWaitUntil(() -> helper.assertTrue(SkyCoreApi.hasEntropyEvidence(player, EntropyContract.WORLD_FUEL_PRODUCED), "Earn true paired production evidence"))
                .thenExecute(() -> {
                    WorkbenchBlockEntity table = helper.getBlockEntity(tablePos); unload(player, table);
                    player.setItemInHand(InteractionHand.MAIN_HAND, take(player, EntropyContent.WORLD_FUEL.get(), 1));
                    helper.useBlock(shelter, player);
                    helper.assertTrue(SkyCoreApi.hasEntropyEvidence(player, EntropyContract.WORLD_FUEL_SUPPLIED), "Real first-chapter fuel expands shelter");
                    helper.assertTrue(JsonParser.parseString(SkyCoreApi.fieldSnapshotJson(helper.getLevel(), shelterAbsolute.east(16))).getAsJsonObject().get("protected").getAsBoolean(), "Neighbor chunk now protected");
                    helper.assertTrue(SkyCoreApi.hasMilestone(player, Milestone.FIRST_TREE.id()), "Supply preserves permanent first-tree milestone");
                }).thenSucceed();

    }

    @GameTest(template = "empty", timeoutTicks = 600, skyAccess = true)
    public static void outputBackpressurePauseAndWorkbenchDiskRestore(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        grantThrough(player, Milestone.WORKBENCH_PLACED);
        WorkbenchBlockEntity table = place(helper, TABLE, CoreContent.STONE_WORKBENCH.get(), player);
        table.setItem(0, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4));
        table.setItem(4, new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()));
        table.setItem(5, new ItemStack(Items.QUARTZ, 64)); table.setItem(6, new ItemStack(Items.REDSTONE, 64));
        helper.startSequence().thenIdle(210).thenExecute(() -> {
            helper.assertValueEqual(table.getItem(0).getCount(), 4, "Blocked output preserves input");
            helper.assertValueEqual(table.getItem(4).getCount(), 1, "Blocked output preserves heat source");
            helper.assertValueEqual(table.progress(), 0, "Blocked output does not advance");
            helper.assertFalse(SkyCoreApi.hasMilestone(player, Milestone.MINERALS_REFINED.id()), "No phantom milestone");
            table.setItem(5, ItemStack.EMPTY); table.setItem(6, ItemStack.EMPTY);
        }).thenIdle(90).thenExecute(() -> {
            helper.assertTrue(table.progress() >= 80 && table.progress() < 200, "Actual partial progress");
            int progress = table.progress();
            CompoundTag saved = disk(helper, table.saveWithFullMetadata(helper.getLevel().registryAccess()), "workbench");
            WorkbenchBlockEntity restored = new WorkbenchBlockEntity(table.getBlockPos(), table.getBlockState());
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(table.getBlockPos());
            helper.getLevel().setBlockEntity(restored);
            helper.assertValueEqual(restored.progress(), progress, "Progress restored");
            helper.assertValueEqual(restored.owner(), table.owner(), "Owner restored");
        }).thenWaitUntil(() -> helper.assertTrue(SkyCoreApi.hasMilestone(player, Milestone.MINERALS_REFINED.id()), "Restored machine finishes"))
                .thenExecute(() -> {
                    WorkbenchBlockEntity restored = helper.getBlockEntity(TABLE);
                    helper.assertTrue(restored.getItem(0).isEmpty() && restored.getItem(4).isEmpty(), "Only the completed batch is consumed");
                    helper.assertValueEqual(restored.getItem(5).getCount(), 1, "Single quartz result");
                    helper.assertValueEqual(restored.getItem(6).getCount(), 1, "Single redstone result");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1400, skyAccess = true)
    public static void blockedGerminationKeepsEscrowAcrossDiskRestore(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        grantThrough(player, Milestone.LIFE_EMBRYO_CREATED);
        SoilBlockEntity soil = place(helper, SOIL, CoreContent.MINERAL_SOIL.get(), player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreContent.LIFE_EMBRYO.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CoreContent.CONDENSATE.get()));
        helper.assertTrue(soil.begin(player), "Start germination");
        helper.setBlock(SOIL.above(3), Blocks.STONE);
        helper.startSequence().thenIdle(1210).thenExecute(() -> {
            helper.assertFalse(SkyCoreApi.hasMilestone(player, Milestone.FIRST_TREE.id()), "Blocked tree cannot grant progress");
            helper.assertValueEqual(soil.getItem(0).getCount(), 1, "Embryo held exactly once");
            helper.assertValueEqual(soil.getItem(1).getCount(), 1, "Condensate held exactly once");
            helper.assertBlockPresent(Blocks.AIR, SOIL.above());
            SoilBlockEntity restored = new SoilBlockEntity(soil.getBlockPos(), soil.getBlockState());
            restored.loadWithComponents(disk(helper, soil.saveWithFullMetadata(helper.getLevel().registryAccess()), "soil"), helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(soil.getBlockPos()); helper.getLevel().setBlockEntity(restored);
            helper.assertValueEqual(restored.progress(), 1200, "Completed timer restored without another charge");
            helper.setBlock(SOIL.above(3), Blocks.AIR);
        }).thenWaitUntil(() -> helper.assertTrue(SkyCoreApi.hasMilestone(player, Milestone.FIRST_TREE.id()), "Unblocked tree finishes"))
                .thenExecute(() -> {
                    helper.assertTrue(((SoilBlockEntity) helper.getBlockEntity(SOIL)).isEmpty(), "Escrow consumed after successful tree");
                    helper.assertBlockPresent(Blocks.OAK_LOG, SOIL.above(4));
                    BlockPos cancelledPos = new BlockPos(9, 2, 12);
                    SoilBlockEntity cancelled = place(helper, cancelledPos, CoreContent.MINERAL_SOIL.get(), player);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreContent.LIFE_EMBRYO.get()));
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CoreContent.CONDENSATE.get()));
                    helper.assertTrue(cancelled.begin(player), "Begin a cancellable germination");
                    helper.getLevel().destroyBlock(cancelled.getBlockPos(), true, player);
                    helper.assertItemEntityCountIs(CoreContent.LIFE_EMBRYO.get(), cancelledPos, 2, 1);
                    helper.assertItemEntityCountIs(CoreContent.CONDENSATE.get(), cancelledPos, 2, 1);
                    helper.assertItemEntityCountIs(CoreContent.MINERAL_SOIL.get().asItem(), cancelledPos, 2, 1);
                    helper.getLevel().destroyBlock(cancelled.getBlockPos(), true, player);
                    helper.assertItemEntityCountIs(CoreContent.LIFE_EMBRYO.get(), cancelledPos, 2, 1);
                    helper.assertItemEntityCountIs(CoreContent.CONDENSATE.get(), cancelledPos, 2, 1);
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, skyAccess = true)
    public static void realPartyIdentityAndCrossTeamPermissions(GameTestHelper helper) {
        ServerPlayer first = player(helper);
        ServerPlayer second = player(helper);
        try {
            FTBTeamsAPI.api().getManager().createPartyTeam(first, "qa-" + first.getUUID().toString().substring(0, 8), "", Color4I.WHITE);
        } catch (Exception exception) { throw new RuntimeException(exception); }
        UUID firstTeam = TeamAccess.team(first).orElseThrow();
        helper.assertFalse(firstTeam.equals(first.getUUID()), "API must resolve the actual party UUID");
        RockCoreBlockEntity core = place(helper, ROCK, CoreContent.ROCK_CORE.get(), first);
        helper.assertFalse(core.collect(second), "Other team cannot harvest");
        for (int slot = 0; slot < first.getInventory().items.size(); slot++) first.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        first.getInventory().items.set(first.getInventory().selected, ItemStack.EMPTY);
        helper.assertFalse(core.collect(first), "Only one empty slot cannot hold both distinct harvest results");
        helper.assertValueEqual(count(first, Items.STONE), 35 * 64, "Failed harvest preserves inventory");
        helper.assertValueEqual(count(first, CoreContent.STONE_FRAGMENT.get()), 0, "No partial harvest");
        first.getInventory().clearContent();
        helper.assertTrue(core.collect(first), "Owning party can harvest");
        helper.assertTrue(SkyCoreApi.hasMilestone(first, Milestone.CORE_EXTRACTED.id()), "Owner progress");
        helper.assertFalse(SkyCoreApi.hasMilestone(second, Milestone.CORE_EXTRACTED.id()), "Other team unchanged");
        var denied = new BlockEvent.BreakEvent(helper.getLevel(), core.getBlockPos(), core.getBlockState(), second);
        NeoForge.EVENT_BUS.post(denied);
        helper.assertTrue(denied.isCanceled(), "Cross-team destruction denied");
        WorkbenchBlockEntity table = place(helper, TABLE, CoreContent.STONE_WORKBENCH.get(), first);
        helper.assertFalse(table.stillValid(second), "Cross-team menu denied");
        helper.assertFalse(SkyCoreApi.hasMilestone(first, Milestone.STORM), "Future gates remain locked");
        helper.assertFalse(SkyCoreApi.hasMilestone(first, "skycore:unknown"), "Unknown milestone is not granted");
        helper.assertValueEqual(command(helper, first, "skycore milestone test @s skycore:ch01/core_extracted"), 1, "Namespaced milestone command succeeds");
        helper.assertValueEqual(command(helper, first, "skycore milestone test @s skycore:ch01/first_tree"), 0, "Locked milestone command returns zero");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260, skyAccess = true)
    public static void offlineOwnedMachineDoesNotAdvance(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        grantThrough(player, Milestone.WORKBENCH_PLACED);
        WorkbenchBlockEntity table = place(helper, TABLE, CoreContent.STONE_WORKBENCH.get(), player);
        table.setItem(0, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4));
        table.setItem(4, new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.startSequence().thenIdle(220).thenExecute(() -> {
            helper.assertValueEqual(table.progress(), 0, "All members offline pauses the timer");
            helper.assertValueEqual(table.getItem(0).getCount(), 4, "Offline materials retained");
            helper.assertTrue(table.getItem(5).isEmpty(), "No offline product");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, skyAccess = true)
    public static void recipeNetworkCodecsAndValidation(GameTestHelper helper) {
        var level = helper.getLevel();
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (var holder : level.getRecipeManager().getAllRecipesFor(CoreContent.STONE_PROCESSING.get())) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                var serializer = CoreContent.STONE_SERIALIZER.get();
                serializer.streamCodec().encode(buffer, holder.value());
                var decoded = serializer.streamCodec().decode(buffer);
                helper.assertValueEqual(StoneProcessingRecipe.CODEC.codec().encodeStart(ops, decoded).getOrThrow(),
                        StoneProcessingRecipe.CODEC.codec().encodeStart(ops, holder.value()).getOrThrow(), "Stone processing wire round trip");
            } finally { buffer.release(); }
        }
        var germination = level.getRecipeManager().getAllRecipesFor(CoreContent.GERMINATION.get()).getFirst().value();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            CoreContent.GERMINATION_SERIALIZER.get().streamCodec().encode(buffer, germination);
            var decoded = CoreContent.GERMINATION_SERIALIZER.get().streamCodec().decode(buffer);
            helper.assertValueEqual(GerminationRecipe.CODEC.codec().encodeStart(ops, decoded).getOrThrow(), GerminationRecipe.CODEC.codec().encodeStart(ops, germination).getOrThrow(), "Germination wire round trip");
        } finally { buffer.release(); }
        var encoded = StoneProcessingRecipe.CODEC.codec().encodeStart(ops, level.getRecipeManager().getAllRecipesFor(CoreContent.STONE_PROCESSING.get()).getFirst().value()).getOrThrow().getAsJsonObject();
        var condensate = level.getRecipeManager().getAllRecipesFor(CoreContent.STONE_PROCESSING.get()).stream().filter(r -> r.id().toString().equals("skycore:ch01/condensate")).findFirst().orElseThrow().value();
        helper.assertFalse(condensate.matches(new WorkbenchInput(List.of(new ItemStack(CoreContent.MINERAL_SOIL.get()),
                new ItemStack(CoreContent.CONDENSATE.get()), new ItemStack(Items.QUARTZ), new ItemStack(Items.REDSTONE),
                new ItemStack(CoreContent.PRIMORDIAL_FUEL.get()))), level), "Condensate must not steal an embryo's inputs");
        encoded.addProperty("required_milestone", "skycore:typo");
        helper.assertTrue(StoneProcessingRecipe.CODEC.codec().parse(ops, encoded).error().isPresent(), "Unknown milestone rejected");
        encoded.remove("required_milestone"); encoded.addProperty("duration_ticks", 0);
        helper.assertTrue(StoneProcessingRecipe.CODEC.codec().parse(ops, encoded).error().isPresent(), "Zero duration rejected");
        var input = new ArrayList<>(List.of(new ItemStack(Items.STONE, 64), new ItemStack(Items.DIRT, 64)));
        helper.assertTrue(StackTransactions.insert(input, List.of(new ItemStack(Items.QUARTZ))).isEmpty(), "Full output cannot partially mutate");
        helper.assertValueEqual(input.getFirst().getCount(), 64, "Original stack untouched");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 180, skyAccess = true)
    public static void inputEditsResetImmediatelyAndCannotForgeTreeEvidence(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        grantThrough(player, Milestone.WORKBENCH_PLACED);
        WorkbenchBlockEntity table = place(helper, TABLE, CoreContent.STONE_WORKBENCH.get(), player);
        table.setItem(0, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 8));
        table.setItem(4, new ItemStack(CoreContent.PRIMORDIAL_FUEL.get(), 2));
        helper.startSequence().thenIdle(90).thenExecute(() -> {
            helper.assertTrue(table.progress() >= 80, "Machine accumulated real progress");
            table.setItem(0, new ItemStack(CoreContent.STONE_FRAGMENT.get(), 4));
            helper.assertValueEqual(table.progress(), 0, "Input edit resets before logout/save");
            grantThrough(player, Milestone.LIFE_EMBRYO_CREATED);
            var data = TeamProgressData.get(player.getServer());
            data.processingCommitted(TeamAccess.team(player).orElseThrow(), "skycore:ch01/first_tree");
            helper.assertFalse(SkyCoreApi.hasMilestone(player, Milestone.FIRST_TREE.id()), "Workbench recipe ID is not tree-generation evidence");
        }).thenSucceed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absolutePos(TABLE).getCenter());
        player.getInventory().clearContent(); player.getInventory().selected = 8;
        helper.assertTrue(TeamAccess.team(player).isPresent(), "Real FTB Teams login must provide a team");
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() {
                if (helper.getLevel().getServer().getPlayerList().getPlayers().contains(player)) helper.getLevel().getServer().getPlayerList().remove(player);
            }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { cleanup(); }
        });
        return player;
    }
    @SuppressWarnings("unchecked") private static <T extends OwnedBlockEntity> T place(GameTestHelper helper, BlockPos relative, CoreBlock block, ServerPlayer player) {
        helper.setBlock(relative.below(), Blocks.STONE);
        helper.setBlock(relative, block);
        BlockPos absolute = helper.absolutePos(relative);
        block.setPlacedBy(helper.getLevel(), absolute, block.defaultBlockState(), player, new ItemStack(block));
        return (T) helper.getLevel().getBlockEntity(absolute);
    }
    private static void grantThrough(ServerPlayer player, Milestone stop) {
        var progress = TeamProgressData.get(player.getServer());
        for (Milestone milestone : Milestone.values()) {
            progress.grant(TeamAccess.team(player).orElseThrow(), milestone);
            if (milestone == stop) return;
        }
    }
    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static ItemStack take(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) { int take = Math.min(remaining, stack.getCount()); stack.shrink(take); remaining -= take; }
        }
        if (remaining != 0) throw new AssertionError("Missing renewable ingredient " + item + ": " + remaining);
        return new ItemStack(item, amount);
    }
    private static void craftBackpack(GameTestHelper helper, ServerPlayer player, String id, Item ingredient) {
        List<ItemStack> stacks = List.of(new ItemStack(ingredient), new ItemStack(ingredient), new ItemStack(ingredient), new ItemStack(ingredient));
        var input = CraftingInput.of(2, 2, stacks);
        var holder = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertValueEqual(holder.id().toString(), "skycore:ch01/" + id, "Backpack recipe ID");
        take(player, ingredient, 4);
        helper.assertTrue(player.addItem(holder.value().assemble(input, helper.getLevel().registryAccess())), "Backpack output fits");
    }
    private static void load(ServerPlayer player, WorkbenchBlockEntity table, ItemStack input, ItemStack fuel) { load(player, table, List.of(input), fuel); }
    private static void load(ServerPlayer player, WorkbenchBlockEntity table, List<ItemStack> input, ItemStack fuel) {
        if (!table.isEmpty()) throw new AssertionError("Expected an empty workbench between recipe batches");
        for (int slot = 0; slot < input.size(); slot++) table.setItem(slot, take(player, input.get(slot).getItem(), input.get(slot).getCount()));
        if (!fuel.isEmpty()) table.setItem(4, take(player, fuel.getItem(), fuel.getCount()));
    }
    private static void unload(ServerPlayer player, WorkbenchBlockEntity table) {
        for (int slot = 0; slot < table.getContainerSize(); slot++) {
            ItemStack stack = table.removeItemNoUpdate(slot);
            if (!stack.isEmpty() && !player.addItem(stack)) throw new AssertionError("Output unexpectedly did not fit");
        }
        table.setChanged();
    }
    private static CompoundTag disk(GameTestHelper helper, CompoundTag tag, String name) {
        try {
            Path dir = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("skycore-qa");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + "-" + UUID.randomUUID() + ".dat");
            NbtIo.writeCompressed(tag, file);
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
    }
    private static int command(GameTestHelper helper, ServerPlayer actor, String command) {
        var server = helper.getLevel().getServer();
        try { return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withEntity(actor)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new RuntimeException(exception); }
    }
    private ChapterOneTests() {}
}
