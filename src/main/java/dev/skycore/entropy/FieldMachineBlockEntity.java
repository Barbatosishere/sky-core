package dev.skycore.entropy;

import dev.skycore.block.OwnedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent identity link; all operational state belongs to EntropySavedData. */
public final class FieldMachineBlockEntity extends OwnedBlockEntity {
    private final boolean inverse;
    @Nullable private UUID machineId;
    @Nullable private CompoundTag invalidSource;
    public FieldMachineBlockEntity(boolean inverse, BlockPos pos, BlockState state) {
        super(inverse ? EntropyContent.ANTI_ENTROPY_ENTITY.get() : EntropyContent.ENTROPY_ENTITY.get(), pos, state);
        this.inverse = inverse;
    }
    public boolean inverse() { return inverse; }
    @Nullable public UUID machineId() { return machineId; }
    boolean invalidLink() { return invalidSource != null; }
    void identify(UUID id) {
        if (machineId != null && !machineId.equals(id)) throw new IllegalStateException("Cannot replace machine identity");
        machineId = id; setChanged();
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        invalidSource = tag.contains("EntropySchemaVersion") && (!tag.contains("EntropySchemaVersion", 3) || tag.getInt("EntropySchemaVersion") != 1)
                || tag.contains("MachineId") && !tag.hasUUID("MachineId")
                || tag.contains("OwnerTeam") && !tag.hasUUID("OwnerTeam") ? tag.copy() : null;
        machineId = tag.hasUUID("MachineId") ? tag.getUUID("MachineId") : null;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (invalidSource != null) { tag.merge(invalidSource.copy()); return; }
        tag.putInt("EntropySchemaVersion", 1);
        if (machineId != null) tag.putUUID("MachineId", machineId);
    }
}
