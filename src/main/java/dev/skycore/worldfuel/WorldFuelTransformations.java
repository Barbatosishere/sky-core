package dev.skycore.worldfuel;

import dev.skycore.CoreContent;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.entropy.EntropyRuntime;
import dev.skycore.logic.WorldFuelCycle;
import dev.skycore.progress.TeamAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class WorldFuelTransformations {
    private static final String WORK = "SkyCoreWorldFuelWork";

    private WorldFuelTransformations() {}

    public static void tick(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;
        ItemStack stack = entity.getItem();
        if (!stack.has(CoreContent.WORLD_FUEL_OWNER.get()) && entity.getOwner() instanceof ServerPlayer player) {
            TeamAccess.team(player).ifPresent(team -> stack.set(CoreContent.WORLD_FUEL_OWNER.get(), team));
        }
        if (stack.is(CoreContent.TRANSMUTATION_RESIDUE.get())) {
            var fluid = level.getFluidState(entity.blockPosition());
            boolean enabled = fluid.is(FluidTags.WATER) && !fluid.isSource() && WorldFuelDaylight.isOpenSky(level, entity.blockPosition());
            if (advance(entity, WorldFuelCycle.WASHING_TICKS, enabled) != null
                    && spawn(entity, List.of(new ItemStack(CoreContent.TRANSMUTATION_POWDER.get())))) {
                reset(entity);
                if (stack.getCount() == 1) entity.discard();
                else entity.setItem(stack.copyWithCount(stack.getCount() - 1));
            }
        } else if (stack.is(CoreContent.TRANSMUTATION_POWDER.get())) {
            boolean enabled = WorldFuelDaylight.phase(level, entity.blockPosition()) == WorldFuelCycle.Phase.NIGHT;
            if (advance(entity, WorldFuelCycle.ACTIVATION_TICKS, enabled) != null
                    && spawn(entity, List.of(new ItemStack(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get(), stack.getCount())))) {
                entity.discard();
            }
        } else if (stack.is(CoreContent.CRUDE_WORLD_FUEL.get())) {
            refine(entity, level);
        } else {
            reset(entity);
        }
    }

    static void reset(ItemEntity entity) {
        entity.getPersistentData().remove(WORK);
    }

    @Nullable
    private static CompoundTag advance(ItemEntity entity, int duration, boolean enabled) {
        if (!enabled) {
            reset(entity);
            return null;
        }
        var persistentData = entity.getPersistentData();
        var work = persistentData.getCompound(WORK);
        int count = entity.getItem().getCount();
        if (work.getInt("Count") != count || work.getInt("Duration") != duration) {
            work = new CompoundTag();
            work.putInt("Count", count);
            work.putInt("Duration", duration);
            persistentData.put(WORK, work);
        }
        int elapsed = WorldFuelCycle.advance(work.getInt("Ticks"), duration, true);
        work.putInt("Ticks", elapsed);
        return elapsed >= duration ? work : null;
    }

    private static void refine(ItemEntity entity, ServerLevel level) {
        ItemStack stack = entity.getItem();
        var owner = stack.get(CoreContent.WORLD_FUEL_OWNER.get());
        boolean enabled = WorldFuelDaylight.phase(level, entity.blockPosition()) == WorldFuelCycle.Phase.DAY
                && (owner == null || EntropyRuntime.ready(level.getServer()));
        var work = advance(entity, WorldFuelCycle.REFINEMENT_TICKS, enabled);
        if (work == null) return;
        int count = stack.getCount();
        if (!work.contains("Residues")) {
            int residues = 0;
            for (int unit = 0; unit < count; unit++) if (WorldFuelCycle.yieldsResidue(level.random.nextInt(20))) residues++;
            work.putInt("Residues", residues);
        }
        List<ItemStack> results = new ArrayList<>();
        results.add(new ItemStack(EntropyContent.WORLD_FUEL.get(), count));
        int residues = work.getInt("Residues");
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
