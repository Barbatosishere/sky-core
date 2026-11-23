package dev.skycore.worldfuel;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class WorldFuelMaterialItem extends Item {
    public WorldFuelMaterialItem(Properties properties) { super(properties); }

    @Override public int getEntityLifespan(ItemStack stack, Level level) { return Integer.MAX_VALUE; }

    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        WorldFuelTransformations.tick(entity);
        return entity.isRemoved();
    }
}
