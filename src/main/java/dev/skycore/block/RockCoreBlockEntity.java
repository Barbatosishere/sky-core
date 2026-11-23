package dev.skycore.block;

import dev.skycore.CoreContent;
import dev.skycore.logic.StackTransactions;
import dev.skycore.progress.Milestone;
import dev.skycore.progress.TeamProgressData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class RockCoreBlockEntity extends OwnedBlockEntity {
    private long nextHarvest;
    public RockCoreBlockEntity(BlockPos pos, BlockState state) { super(CoreContent.ROCK_ENTITY.get(), pos, state); }

    public boolean collect(ServerPlayer player) {
        if (!mayUse(player) || level == null) return false;
        if (!player.getMainHandItem().isEmpty()) { player.displayClientMessage(Component.translatable("message.skycore.empty_hand"), true); return false; }
        if (level.getGameTime() < nextHarvest) return false;
        var plan = StackTransactions.insert(player.getInventory().items, List.of(
                new ItemStack(CoreContent.STONE_FRAGMENT.get(), 2), new ItemStack(CoreContent.MINERAL_RESIDUE.get())));
        if (plan.isEmpty()) { player.displayClientMessage(Component.translatable("message.skycore.inventory_full"), true); return false; }
        for (int slot = 0; slot < plan.get().size(); slot++) player.getInventory().items.set(slot, plan.get().get(slot));
        player.getInventory().setChanged();
        nextHarvest = level.getGameTime() + 20;
        setChanged();
        TeamProgressData.get(player.getServer()).grant(owner, Milestone.CORE_EXTRACTED);
        return true;
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nextHarvest = tag.getLong("NextHarvest");
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("NextHarvest", nextHarvest);
    }
}
