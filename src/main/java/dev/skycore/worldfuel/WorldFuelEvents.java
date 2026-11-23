package dev.skycore.worldfuel;

import dev.skycore.CoreContent;
import dev.skycore.progress.TeamAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

public final class WorldFuelEvents {
    public static final String OWNER = "SkyCoreWorldFuelOwner";

    private WorldFuelEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(WorldFuelEvents::capabilities);
        NeoForge.EVENT_BUS.addListener(WorldFuelEvents::placed);
        NeoForge.EVENT_BUS.addListener(WorldFuelEvents::spawned);
        WorldFuelSafety.register();
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreContent.WORLD_ACTIVITY_COLLECTOR_ENTITY.get(),
                (collector, side) -> new SidedInvWrapper(collector, side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreContent.WORLD_FUEL_SYNTHESIZER_ENTITY.get(),
                (machine, side) -> new SidedInvWrapper(machine, side));
    }

    private static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel().getBlockEntity(event.getPos()) instanceof DispenserBlockEntity dispenser) {
            TeamAccess.team(player).ifPresent(team -> {
                dispenser.getPersistentData().putUUID(OWNER, team);
                dispenser.setChanged();
            });
        }
    }

    private static void spawned(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ItemEntity item)
                || !(item.getItem().getItem() instanceof WorldFuelMaterialItem)) return;
        if (item.getPersistentData().hasUUID(OWNER)) {
            item.getItem().set(CoreContent.WORLD_FUEL_OWNER.get(), item.getPersistentData().getUUID(OWNER));
            return;
        }
        if (item.getOwner() instanceof ServerPlayer player) {
            TeamAccess.team(player).ifPresent(team -> item.getItem().set(CoreContent.WORLD_FUEL_OWNER.get(), team));
            return;
        }
        BlockPos center = item.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser)
                    || !dispenser.getPersistentData().hasUUID(OWNER)) continue;
            var state = dispenser.getBlockState();
            if (!state.hasProperty(DispenserBlock.FACING)) continue;
            var direction = state.getValue(DispenserBlock.FACING);
            var origin = DispenserBlock.getDispensePosition(new BlockSource(level, pos, state, dispenser));
            double offset = direction.getAxis() == Direction.Axis.Y ? 0.125 : 0.15625;
            if (item.distanceToSqr(origin.x(), origin.y() - offset, origin.z()) < 0.000001) {
                item.getItem().set(CoreContent.WORLD_FUEL_OWNER.get(), dispenser.getPersistentData().getUUID(OWNER));
                return;
            }
        }
    }
}
