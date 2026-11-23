package dev.skycore.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class StackTransactions {
    private StackTransactions() {}

    /** Plans against detached copies; failure leaves every original stack untouched. */
    public static Optional<List<ItemStack>> insert(List<ItemStack> original, List<ItemStack> additions) {
        List<ItemStack> result = new ArrayList<>(original.stream().map(ItemStack::copy).toList());
        for (ItemStack addition : additions) {
            ItemStack remaining = addition.copy();
            for (int slot = 0; slot < result.size() && !remaining.isEmpty(); slot++) {
                ItemStack present = result.get(slot);
                if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, remaining)) {
                    int amount = Math.min(remaining.getCount(), Math.max(0, present.getMaxStackSize() - present.getCount()));
                    present.grow(amount); remaining.shrink(amount);
                }
            }
            for (int slot = 0; slot < result.size() && !remaining.isEmpty(); slot++) {
                if (result.get(slot).isEmpty()) result.set(slot, remaining.split(Math.min(remaining.getCount(), remaining.getMaxStackSize())));
            }
            if (!remaining.isEmpty()) return Optional.empty();
        }
        return Optional.of(result);
    }
}
