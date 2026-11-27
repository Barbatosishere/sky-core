package dev.skycore.worldfuel;

import dev.skycore.CoreContent;
import dev.skycore.entropy.EntropyContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WorldFuelSafety {
    private WorldFuelSafety() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldFuelSafety::spawned);
        NeoForge.EVENT_BUS.addListener(WorldFuelSafety::tick);
    }

    private static boolean protectedMaterial(ItemEntity entity) {
        var stack = entity.getItem();
        return stack.getItem() instanceof WorldFuelMaterialItem || stack.is(EntropyContent.WORLD_FUEL.get())
                || stack.is(EntropyContent.ANTI_ENTROPY_STONE.get().asItem());
    }

    private static void spawned(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof ItemEntity entity && protectedMaterial(entity)) {
            entity.getItem().remove(CoreContent.WORLD_FUEL_PROGRESS.get());
            if (entity.getPersistentData().contains("SkyCoreSafeDimension")) {
                if (entity.getAge() == -32_768) entity.age = 0;
                if (entity.lifespan == Integer.MAX_VALUE) entity.lifespan = entity.getItem().getEntityLifespan(event.getLevel());
            }
            protect(entity);
        }
    }

    private static void tick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof ItemEntity entity && entity.level() instanceof ServerLevel && protectedMaterial(entity)) protect(entity);
    }

    private static void protect(ItemEntity entity) {
        entity.setInvulnerable(true);
        var data = entity.getPersistentData();
        String dimension = entity.level().dimension().location().toString();
        if (!dimension.equals(data.getString("SkyCoreSafeDimension")) && entity.getY() > entity.level().getMinBuildHeight()) {
            data.putString("SkyCoreSafeDimension", dimension);
            data.putDouble("SkyCoreSafeX", entity.getX());
            data.putDouble("SkyCoreSafeY", entity.getY());
            data.putDouble("SkyCoreSafeZ", entity.getZ());
        }
        if (entity.getY() < entity.level().getMinBuildHeight() + 1) {
            WorldFuelTransformations.reset(entity);
            double height = data.contains("SkyCoreSafeY") ? data.getDouble("SkyCoreSafeY") : entity.level().getSeaLevel() + 1;
            double horizontal = data.contains("SkyCoreSafeX") ? data.getDouble("SkyCoreSafeX") : entity.getX();
            double depth = data.contains("SkyCoreSafeZ") ? data.getDouble("SkyCoreSafeZ") : entity.getZ();
            entity.setPos(horizontal, height, depth);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(true);
        }
    }
}
