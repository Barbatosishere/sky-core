package dev.skycore.block;

import dev.skycore.progress.TeamAccess;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class OwnedBlockEntity extends BlockEntity {
    @Nullable protected UUID owner;

    protected OwnedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Nullable public UUID owner() { return owner; }
    public void bind(UUID team) {
        if (owner != null && !owner.equals(team)) throw new IllegalStateException("Cannot rebind an owned block");
        owner = team;
        setChanged();
    }
    public boolean mayUse(ServerPlayer player) { return owner != null && TeamAccess.team(player).filter(owner::equals).isPresent(); }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("OwnerTeam") ? tag.getUUID("OwnerTeam") : null;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("OwnerTeam", owner);
    }
}
