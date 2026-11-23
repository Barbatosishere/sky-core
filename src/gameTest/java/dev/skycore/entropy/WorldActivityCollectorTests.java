package dev.skycore.entropy;

import dev.skycore.CoreContent;
import dev.skycore.api.entropy.EntropyContract;
import dev.skycore.block.WorldActivityCollectorBlockEntity;
import dev.skycore.logic.WorldFuelCycle;
import dev.skycore.menu.WorldActivityCollectorMenu;
import dev.skycore.worldfuel.WorldFuelEvents;
import dev.skycore.worldfuel.WorldFuelDaylight;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.skycore.menu.WorldActivityCollectorMenu.*;

@GameTestHolder("skycore")
@PrefixGameTestTemplate(false)
public final class WorldActivityCollectorTests {
    @GameTest(template = "empty", batch = "world_fuel_automation", timeoutTicks = 80)
    public static void collectorExportsThroughActualHopperAndPreservesLegacyInputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = EntropyTests.player(helper);
        var outsider = EntropyTests.player(helper);
        BlockPos relative = new BlockPos(3, 3, 3);
        helper.setBlock(relative.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(relative, CoreContent.WORLD_ACTIVITY_COLLECTOR.get());
        var collector = (WorldActivityCollectorBlockEntity) level.getBlockEntity(helper.absolutePos(relative));
        collector.bind(EntropyTests.team(player));
        collector.setItem(POWDER_IN, new ItemStack(CoreContent.TRANSMUTATION_POWDER.get(), 3));
        var saved = collector.saveWithoutMetadata(level.registryAccess());
        saved.putInt("CollectionTicks", WorldFuelCycle.COLLECTION_TICKS - 1);
        saved.putInt("ActivationTicks", 1_900);
        collector.loadWithComponents(saved, level.registryAccess());
        var menu = (WorldActivityCollectorMenu) collector.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null && collector.createMenu(2, outsider.getInventory(), outsider) == null, "Manual use requires the owning team");
        helper.assertFalse(menu.getSlot(POWDER_IN).mayPlace(new ItemStack(CoreContent.TRANSMUTATION_POWDER.get())), "Collector no longer accepts processing inputs");
        var capability = level.getCapability(Capabilities.ItemHandler.BLOCK, collector.getBlockPos(), Direction.DOWN);
        helper.assertTrue(capability != null, "Provides sided automation inventory");
        int simulated = 0;
        for (int slot = 0; slot < capability.getSlots(); slot++) {
            helper.assertFalse(capability.insertItem(slot, new ItemStack(Items.DIRT), true).isEmpty(), "Automation cannot insert items");
            ItemStack stack = capability.extractItem(slot, 1, true);
            simulated += stack.getCount();
        }
        helper.assertValueEqual(simulated, 1, "Simulation exposes recoverable legacy items");
        helper.assertValueEqual(collector.getItem(POWDER_IN).getCount(), 3, "Simulation leaves inventory untouched");
        helper.assertValueEqual(collector.getItem(POWDER_IN).getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), 1_900, "Preserves pending old progress on its item");
        helper.runAfterDelay(45, () -> {
            var hopper = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(relative.below()));
            helper.assertValueEqual(hopper.countItem(CoreContent.WORLD_ACTIVITY.get()), 1, "Real hopper extracts newly collected activity");
            helper.assertValueEqual(hopper.countItem(CoreContent.TRANSMUTATION_POWDER.get()), 3, "Real hopper recovers all old inputs");
            helper.assertTrue(collector.isEmpty(), "No duplicated or stranded items remain");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "world_fuel_detector", timeoutTicks = 150)
    public static void nativeDetectorSignalsControlGroundItemsAndTeamEvidence(GameTestHelper helper) {
        var level = helper.getLevel();
        floor(helper);
        long oldTime = level.getDayTime();
        boolean oldCycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setWeatherParameters(100_000, 0, false, false);
        level.setDayTime(6_000);
        EntropyTests.cleanup(helper, () -> {
            level.setDayTime(oldTime);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(oldCycle, level.getServer());
        });
        ServerPlayer player = EntropyTests.player(helper);
        ServerPlayer outsider = EntropyTests.player(helper);
        player.setPos(helper.absolutePos(new BlockPos(7, 2, 7)).getCenter());
        outsider.setPos(helper.absolutePos(new BlockPos(7, 2, 7)).getCenter());
        BlockPos normal = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos inverted = normal.east(3);
        level.setBlock(normal, Blocks.DAYLIGHT_DETECTOR.defaultBlockState(), 3);
        level.setBlock(inverted, Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(DaylightDetectorBlock.INVERTED, true), 3);
        var powderStack = new ItemStack(CoreContent.TRANSMUTATION_POWDER.get(), 2);
        powderStack.set(CoreContent.WORLD_FUEL_PROGRESS.get(), WorldFuelCycle.ACTIVATION_TICKS - 1);
        ItemEntity powder = item(helper, powderStack, new BlockPos(2, 3, 2));
        var crude = new ItemStack(CoreContent.CRUDE_WORLD_FUEL.get(), 3);
        crude.set(CoreContent.WORLD_FUEL_PROGRESS.get(), WorldFuelCycle.REFINEMENT_TICKS - 1);
        crude.set(CoreContent.WORLD_FUEL_OWNER.get(), EntropyTests.team(player));
        item(helper, crude, new BlockPos(4, 3, 4));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(level.getBlockState(normal).getValue(DaylightDetectorBlock.POWER) > 0, "Ordinary sensor produces power at noon");
            helper.assertValueEqual(level.getBlockState(inverted).getValue(DaylightDetectorBlock.POWER), 0, "Inverted sensor is zero at noon");
            helper.assertValueEqual(WorldFuelDaylight.phase(level, inverted), WorldFuelCycle.Phase.DAY, "Zero inverted detector output means day");
            helper.assertValueEqual(powder.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), 3_799, "Powder pauses during the day");
            helper.assertValueEqual(worldCount(helper, EntropyContent.WORLD_FUEL.get()), 3, "Refines the entire dropped stack exactly once");
            helper.assertTrue(EntropyRuntime.hasEvidence(player, EntropyContract.WORLD_FUEL_PRODUCED), "Real owner receives production evidence");
            helper.assertFalse(EntropyRuntime.hasEvidence(outsider, EntropyContract.WORLD_FUEL_PRODUCED), "Other team receives no production evidence");
            level.setDayTime(18_000);
        });
        helper.runAfterDelay(55, () -> {
            helper.assertValueEqual(level.getBlockState(normal).getValue(DaylightDetectorBlock.POWER), 0, "Ordinary sensor is zero at night");
            helper.assertTrue(level.getBlockState(inverted).getValue(DaylightDetectorBlock.POWER) > 0, "Inverted sensor is active at night");
            helper.assertValueEqual(WorldFuelDaylight.phase(level, normal), WorldFuelCycle.Phase.NIGHT, "Zero ordinary detector output means night");
            helper.assertValueEqual(worldCount(helper, CoreContent.ACTIVE_TRANSMUTATION_AGENT.get()), 2, "Night converts both powders");
            ItemStack paused = new ItemStack(CoreContent.CRUDE_WORLD_FUEL.get());
            paused.set(CoreContent.WORLD_FUEL_PROGRESS.get(), 777);
            ItemEntity pending = item(helper, paused, new BlockPos(4, 3, 1));
            var tag = new CompoundTag();
            pending.saveWithoutId(tag);
            var restored = new ItemEntity(level, 0, 0, 0, ItemStack.EMPTY);
            restored.load(tag);
            helper.assertValueEqual(restored.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), 777, "Dropped-item serialization preserves incomplete progress");
            helper.runAfterDelay(5, () -> {
                helper.assertValueEqual(pending.getItem().getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), 777, "Night leaves crude fuel progress paused");
                pending.discard();
                level.setDayTime(6_000);
                level.setWeatherParameters(0, 100_000, true, true);
            });
        });
        helper.runAfterDelay(125, () -> {
            int normalPower = level.getBlockState(normal).getValue(DaylightDetectorBlock.POWER);
            int invertedPower = level.getBlockState(inverted).getValue(DaylightDetectorBlock.POWER);
            helper.assertValueEqual(WorldFuelDaylight.phase(level, normal), WorldFuelCycle.phase(normalPower, invertedPower), "Weather matches the two actual sensor outputs");
            helper.assertTrue(invertedPower > 0, "Rain prevents the inverted detector from reaching zero");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "world_fuel_automation", timeoutTicks = 35)
    public static void groundIngredientsNoLongerBypassTheSynthesizer(GameTestHelper helper) {
        floor(helper);
        item(helper, new ItemStack(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get(), 2), new BlockPos(2, 3, 2));
        item(helper, new ItemStack(CoreContent.WORLD_ACTIVITY.get(), 3), new BlockPos(2, 3, 2));
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(worldCount(helper, CoreContent.WORLD_ACTIVITY.get()), 3, "World mixing preserves activity");
            helper.assertValueEqual(worldCount(helper, CoreContent.ACTIVE_TRANSMUTATION_AGENT.get()), 2, "World mixing preserves agent");
            helper.assertValueEqual(worldCount(helper, CoreContent.CRUDE_WORLD_FUEL.get()), 0, "No machine bypass");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "world_fuel_automation", timeoutTicks = 40)
    public static void actualDropperCreatesGroundInputAndPreservesProvenance(GameTestHelper helper) {
        var level = helper.getLevel();
        floor(helper);
        var player = EntropyTests.player(helper);
        player.setPos(helper.absolutePos(new BlockPos(7, 5, 7)).getCenter());
        BlockPos dropperPos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(dropperPos, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST), 3);
        var dropper = (DispenserBlockEntity) level.getBlockEntity(dropperPos);
        dropper.getPersistentData().putUUID(WorldFuelEvents.OWNER, EntropyTests.team(player));
        dropper.setItem(0, new ItemStack(CoreContent.TRANSMUTATION_POWDER.get(), 2));
        level.setBlock(dropperPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.runAfterDelay(12, () -> {
            helper.assertValueEqual(dropper.countItem(CoreContent.TRANSMUTATION_POWDER.get()), 1, "Real redstone-powered dropper ejects one input");
            var source = level.getEntitiesOfClass(ItemEntity.class, bounds(helper), entity -> entity.getItem().is(CoreContent.TRANSMUTATION_POWDER.get())).getFirst();
            helper.assertValueEqual(source.getItem().get(CoreContent.WORLD_FUEL_OWNER.get()), EntropyTests.team(player), "Native dropped item records the dropper team");
            helper.assertTrue(source.lifespan > 6_000, "Paused material survives ordinary dropped-item expiry");
            ItemStack foreign = source.getItem().copy();
            foreign.set(CoreContent.WORLD_FUEL_OWNER.get(), java.util.UUID.randomUUID());
            helper.assertFalse(ItemEntity.areMergable(source.getItem(), foreign), "Different teams cannot merge away provenance");
            helper.succeed();
        });
    }

    private static ItemEntity item(GameTestHelper helper, ItemStack stack, BlockPos relative) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        var entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack, 0, 0, 0);
        entity.setNoGravity(true);
        entity.setNeverPickUp();
        helper.assertTrue(level.addFreshEntity(entity), "Fixture item added");
        EntropyTests.cleanup(helper, entity::discard);
        return entity;
    }

    private static AABB bounds(GameTestHelper helper) { return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8); }

    private static void floor(GameTestHelper helper) {
        for (int column = 0; column < 7; column++) {
            for (int row = 0; row < 7; row++) helper.setBlock(new BlockPos(column, 0, row), Blocks.STONE);
        }
    }

    private static int worldCount(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds(helper), entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
