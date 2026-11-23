package dev.skycore.worldfuel;

import dev.skycore.CoreContent;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.entropy.EntropyRuntime;
import dev.skycore.logic.WorldFuelCycle;
import dev.skycore.progress.TeamAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class WorldFuelTransformations {
    private WorldFuelTransformations() {}

    public static void tick(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        ItemStack stack = entity.getItem();
        if (!stack.has(CoreContent.WORLD_FUEL_OWNER.get()) && entity.getOwner() instanceof ServerPlayer player) {
            TeamAccess.team(player).ifPresent(team -> stack.set(CoreContent.WORLD_FUEL_OWNER.get(), team));
        }
        if (stack.is(CoreContent.TRANSMUTATION_RESIDUE.get())) {
            var fluid = level.getFluidState(entity.blockPosition());
            if (WorldFuelDaylight.isOpenSky(level, entity.blockPosition()) && fluid.is(FluidTags.WATER) && !fluid.isSource()
                    && advance(stack, WorldFuelCycle.WASHING_TICKS, true)
                    && spawn(entity, List.of(new ItemStack(CoreContent.TRANSMUTATION_POWDER.get())))) {
                ItemStack remaining = stack.copyWithCount(stack.getCount() - 1);
                remaining.remove(CoreContent.WORLD_FUEL_PROGRESS.get());
                if (remaining.isEmpty()) entity.discard(); else entity.setItem(remaining);
            }
        } else if (stack.is(CoreContent.TRANSMUTATION_POWDER.get())) {
            if (advance(stack, WorldFuelCycle.ACTIVATION_TICKS, WorldFuelDaylight.phase(level, entity.blockPosition()) == WorldFuelCycle.Phase.NIGHT)
                    && spawn(entity, List.of(new ItemStack(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get(), stack.getCount())))) entity.discard();
        } else if (stack.is(CoreContent.CRUDE_WORLD_FUEL.get())) {
            refine(entity, level);
        }
    }

    private static boolean advance(ItemStack stack, int duration, boolean enabled) {
        if (!enabled) return false;
        int elapsed = WorldFuelCycle.advance(stack.getOrDefault(CoreContent.WORLD_FUEL_PROGRESS.get(), 0), duration, true);
        stack.set(CoreContent.WORLD_FUEL_PROGRESS.get(), elapsed);
        return elapsed >= duration;
    }

    private static void refine(ItemEntity entity, ServerLevel level) {
        ItemStack stack = entity.getItem();
        if (!advance(stack, WorldFuelCycle.REFINEMENT_TICKS, WorldFuelDaylight.phase(level, entity.blockPosition()) == WorldFuelCycle.Phase.DAY)) return;
        var data = entity.getPersistentData();
        var owner = stack.get(CoreContent.WORLD_FUEL_OWNER.get());
        if (owner != null && !EntropyRuntime.ready(level.getServer())) return;
        int count = stack.getCount();
        if (data.getInt("SkyCoreRefinementBatch") != count) {
            int residues = 0;
            for (int unit = 0; unit < count; unit++) if (WorldFuelCycle.yieldsResidue(level.random.nextInt(20))) residues++;
            data.putInt("SkyCoreRefinementBatch", count);
            data.putInt("SkyCoreRefinementResidues", residues);
        }
        List<ItemStack> results = new ArrayList<>();
        results.add(new ItemStack(EntropyContent.WORLD_FUEL.get(), count));
        int residues = data.getInt("SkyCoreRefinementResidues");
        if (residues > 0) results.add(new ItemStack(CoreContent.TRANSMUTATION_RESIDUE.get(), residues));
        if (spawn(entity, results)) {
            entity.discard();
            if (owner != null) EntropyRuntime.worldFuelRefined(level.getServer(), owner);
        }
    }

    private static boolean spawn(ItemEntity source, List<ItemStack> results) {
        List<ItemEntity> added = new ArrayList<>();
        for (ItemStack result : results) {
            var motion = source.getDeltaMovement();
            var output = new ItemEntity(source.level(), source.getX(), source.getY(), source.getZ(), result, motion.x, motion.y, motion.z);
            output.setDefaultPickUpDelay();
            var owner = source.getItem().get(CoreContent.WORLD_FUEL_OWNER.get());
            if (owner != null && result.getItem() instanceof WorldFuelMaterialItem) {
                output.getPersistentData().putUUID(WorldFuelEvents.OWNER, owner);
            }
            if (!source.level().addFreshEntity(output)) {
                added.forEach(ItemEntity::discard);
                return false;
            }
            added.add(output);
        }
        return true;
    }

}
