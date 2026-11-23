package dev.skycore.entropy;

import dev.skycore.CoreContent;
import dev.skycore.block.WorldFuelSynthesizerBlockEntity;
import dev.skycore.logic.WorldFuelCycle;
import dev.skycore.worldfuel.WorldFuelDaylight;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("skycore")
@PrefixGameTestTemplate(false)
public final class WorldFuelSynthesizerTests {
    @GameTest(template = "empty", batch = "fuel_synthesis", timeoutTicks = 120)
    public static void furnaceExchangeAndSidedAutomationCommitAllOutputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = EntropyTests.player(helper);
        var outsider = EntropyTests.player(helper);
        BlockPos shelterPos = helper.absolutePos(new BlockPos(1, 2, 1));
        var shelter = EntropyTests.field(helper, shelterPos, true, player);
        var identity = shelter.machineId();
        var hit = new BlockHitResult(shelterPos.getCenter(), Direction.UP, shelterPos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FURNACE, 2));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Consumes exactly one furnace");
        helper.assertValueEqual(player.getInventory().countItem(CoreContent.WORLD_FUEL_SYNTHESIZER.get().asItem()), 1, "Gives exactly one synthesizer");
        helper.assertTrue(level.getBlockEntity(shelterPos) == shelter && identity.equals(shelter.machineId()), "Keeps the existing shelter and its identity");
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FURNACE));
        outsider.gameMode.useItemOn(outsider, level, outsider.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(outsider.getMainHandItem().is(Items.FURNACE), "Other teams cannot convert on this shelter");
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FURNACE, 2));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "Full inventory does not consume or void a furnace");
        player.getInventory().clearContent();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos, CoreContent.WORLD_FUEL_SYNTHESIZER.get().defaultBlockState(), 3);
        var machine = (WorldFuelSynthesizerBlockEntity) level.getBlockEntity(pos);
        machine.bind(EntropyTests.team(player));
        var top = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        var side = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.EAST);
        var bottom = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
        helper.assertValueEqual(top.getSlots(), 1, "One top input");
        helper.assertValueEqual(side.getSlots(), 1, "One side input");
        helper.assertValueEqual(bottom.getSlots(), 3, "Three bottom outputs");
        helper.assertFalse(top.insertItem(0, new ItemStack(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get()), false).isEmpty(), "Top rejects agent");
        helper.assertFalse(side.insertItem(0, new ItemStack(CoreContent.WORLD_ACTIVITY.get()), false).isEmpty(), "Side rejects activity");
        top.insertItem(0, new ItemStack(CoreContent.WORLD_ACTIVITY.get(), 2), false);
        side.insertItem(0, new ItemStack(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get(), 2), false);
        helper.assertTrue(top.extractItem(0, 1, false).isEmpty() && side.extractItem(0, 1, false).isEmpty(), "Automation cannot extract unprocessed inputs");
        machine.setItem(WorldFuelSynthesizerBlockEntity.RESIDUE, new ItemStack(Items.DIRT, 64));
        level.setBlock(pos.above(2), Blocks.STONE.defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(machine.getItem(0).getCount(), 2, "Roof pauses synthesis");
            level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(machine.getItem(0).getCount(), 2, "Blocked third output pauses the entire batch");
            helper.assertTrue(machine.getItem(2).isEmpty() && machine.getItem(3).isEmpty(), "No partial products");
            machine.setItem(4, ItemStack.EMPTY);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(machine.getItem(0).isEmpty() && machine.getItem(1).isEmpty(), "Consumes exactly matched input pairs");
            for (int slot = 2; slot < 5; slot++) helper.assertValueEqual(machine.getItem(slot).getCount(), 2, "Equal guaranteed output counts");
            machine.clearContent();
            level.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
            level.setBlock(pos.east(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST), 3);
            level.setBlock(pos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
            ((HopperBlockEntity) level.getBlockEntity(pos.above())).setItem(0, new ItemStack(CoreContent.WORLD_ACTIVITY.get()));
            ((HopperBlockEntity) level.getBlockEntity(pos.east())).setItem(0, new ItemStack(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get()));
        });
        helper.runAfterDelay(90, () -> {
            var hopper = (HopperBlockEntity) level.getBlockEntity(pos.below());
            helper.assertValueEqual(hopper.countItem(CoreContent.CRUDE_WORLD_FUEL.get()), 1, "Actual bottom hopper exports crude fuel");
            helper.assertValueEqual(hopper.countItem(EntropyContent.ANTI_ENTROPY_STONE.get().asItem()), 1, "Actual bottom hopper exports stone");
            helper.assertValueEqual(hopper.countItem(CoreContent.TRANSMUTATION_RESIDUE.get()), 1, "Actual bottom hopper exports residue");
            helper.assertTrue(machine.isEmpty(), "No duplicated or stranded machine items");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "fuel_washing", timeoutTicks = 150)
    public static void flowingWaterRequiresOpenSkyAndThirtyTicksPerItem(GameTestHelper helper) {
        var level = helper.getLevel();
        long oldTime = level.getDayTime();
        boolean oldCycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(6_000);
        level.setWeatherParameters(100_000, 0, false, false);
        EntropyTests.cleanup(helper, () -> {
            level.setDayTime(oldTime);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(oldCycle, level.getServer());
        });
        BlockPos flow = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos source = flow.west();
        level.setBlock(flow.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(flow, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4), 3);
        var flowing = dropped(helper, new ItemStack(CoreContent.TRANSMUTATION_RESIDUE.get(), 3), flow);
        var still = dropped(helper, new ItemStack(CoreContent.TRANSMUTATION_RESIDUE.get()), source);
        helper.onEachTick(() -> {
            if (flowing.isAlive()) { flowing.setPos(flow.getX() + 0.5, flow.getY() + 0.2, flow.getZ() + 0.5); flowing.setDeltaMovement(Vec3.ZERO); }
            if (still.isAlive()) { still.setPos(source.getX() + 0.5, source.getY() + 0.2, source.getZ() + 0.5); still.setDeltaMovement(Vec3.ZERO); }
            for (ItemEntity powder : level.getEntitiesOfClass(ItemEntity.class, new AABB(flow).inflate(12),
                    entity -> entity.getItem().is(CoreContent.TRANSMUTATION_POWDER.get()))) {
                powder.setNoGravity(true);
                powder.setDeltaMovement(Vec3.ZERO);
            }
        });
        helper.runAfterDelay(20, () -> helper.assertValueEqual(count(helper, CoreContent.TRANSMUTATION_POWDER.get()), 0, "No output before 30 ticks"));
        helper.runAfterDelay(35, () -> {
            helper.assertValueEqual(count(helper, CoreContent.TRANSMUTATION_POWDER.get()), 1, "Only one item washed in the first 1.5 seconds");
            helper.assertValueEqual(flowing.getItem().getCount(), 2, "Processes a stack sequentially");
            helper.assertTrue(still.isAlive() && still.getItem().getCount() == 1, "Source water does not wash");
        });
        var paused = new AtomicInteger();
        helper.runAfterDelay(65, () -> {
            helper.assertValueEqual(count(helper, CoreContent.TRANSMUTATION_POWDER.get()), 2, "Second item requires another 30 ticks; remaining=" + flowing.getItem().getCount()
                    + ",progress=" + flowing.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0) + ",alive=" + flowing.isAlive()
                    + ",fluid=" + level.getFluidState(flow) + ",open=" + WorldFuelDaylight.isOpenSky(level, flow));
            level.setBlock(flow.above(), Blocks.STONE.defaultBlockState(), 3);
        });
        helper.runAfterDelay(70, () -> paused.set(flowing.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0)));
        helper.runAfterDelay(90, () -> {
            helper.assertValueEqual(flowing.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), paused.get(), "Roof pauses water processing without clearing progress");
            helper.assertValueEqual(count(helper, CoreContent.TRANSMUTATION_POWDER.get()), 2, "No covered washing");
            level.setBlock(flow.above(), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(125, () -> {
            helper.assertValueEqual(count(helper, CoreContent.TRANSMUTATION_POWDER.get()), 3, "Uncovering resumes the last item");
            helper.assertTrue(still.isAlive(), "Source-water item remains unchanged");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "fuel_safety", timeoutTicks = 30)
    public static void coverPausesDaylightAndHazardsCannotDestroyMaterials(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        var stack = new ItemStack(CoreContent.CRUDE_WORLD_FUEL.get(), 2);
        stack.set(CoreContent.WORLD_FUEL_PROGRESS.get(), 777);
        var entity = dropped(helper, stack, pos);
        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(WorldFuelDaylight.phase(level, pos), WorldFuelCycle.Phase.TWILIGHT, "Covered space never becomes a valid night or day processing environment");
            helper.assertValueEqual(entity.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), 777, "Cover preserves pending progress");
            helper.assertFalse(entity.hurt(level.damageSources().lava(), 100), "Lava cannot destroy the material");
            helper.assertFalse(entity.hurt(level.damageSources().inFire(), 100), "Fire cannot destroy the material");
            helper.assertFalse(entity.hurt(level.damageSources().explosion(null, null), 100), "Explosion cannot destroy the material");
            entity.setPos(pos.getX() + 0.5, level.getMinBuildHeight() - 10, pos.getZ() + 0.5);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(entity.isAlive() && entity.getY() >= pos.getY() && entity.isNoGravity(), "Void returns material to its safe origin and suspends it");
            helper.assertValueEqual(entity.getItem().getCount(), 2, "Hazards do not alter quantity");
            helper.assertValueEqual(entity.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), 777, "Void recovery preserves progress");
            helper.succeed();
        });
    }

    private static ItemEntity dropped(GameTestHelper helper, ItemStack stack, BlockPos pos) {
        var entity = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack, 0, 0, 0);
        entity.setNoGravity(true);
        entity.setNeverPickUp();
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Fixture item added");
        EntropyTests.cleanup(helper, entity::discard);
        return entity;
    }

    private static int count(GameTestHelper helper, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10),
                entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
