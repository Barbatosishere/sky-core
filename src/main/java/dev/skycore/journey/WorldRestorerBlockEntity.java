package dev.skycore.journey;

import dev.skycore.CoreContent;
import dev.skycore.block.OwnedInventoryBlockEntity;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.logic.StackTransactions;
import dev.skycore.progress.Milestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import dev.skycore.worldfuel.WorldFuelDaylight;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldRestorerBlockEntity extends OwnedInventoryBlockEntity implements WorldlyContainer {
    private int progress;
    private String working = "";
    public WorldRestorerBlockEntity(BlockPos pos, BlockState state) { super(JourneyContent.RESTORER_ENTITY.get(), pos, state, 4); }

    @Nullable private JourneyAction action() {
        for (JourneyAction value : JourneyAction.values()) if (getItem(0).is(JourneyContent.CORES.get(value).get())) return value;
        return null;
    }

    @Override public int[] getSlotsForFace(Direction side) { return owner == null ? new int[0] : side == Direction.DOWN ? new int[]{2, 3} : side == Direction.UP ? new int[]{0} : new int[]{1}; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 1 ? stack.is(EntropyContent.WORLD_FUEL.get()) : slot == 0 && JourneyContent.CORES.values().stream().anyMatch(item -> stack.is(item.get()));
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return side != null && canPlaceItem(slot, stack) && (side == Direction.UP && slot == 0 || side.getAxis().isHorizontal() && slot == 1);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return side == Direction.DOWN && slot >= 2; }

    public void interact(ServerPlayer player) {
        if (!mayUse(player)) { player.displayClientMessage(Component.translatable("message.skycore.not_owner"), true); return; }
        if (!(level instanceof ServerLevel serverLevel) || !JourneyWorlds.owned(player, serverLevel)) {
            player.displayClientMessage(Component.translatable("message.skycore.journey_use_world"), true); return;
        }
        ItemStack held = player.getMainHandItem();
        var data = JourneyWorldData.get(player.server);
        var state = data.state(owner);
        if (held.isEmpty() && player.isShiftKeyDown()) {
            state.enabled = !state.enabled; data.setDirty();
        } else if (!held.isEmpty()) {
            int slot = held.is(EntropyContent.WORLD_FUEL.get()) ? 1 : canPlaceItem(0, held) ? 0 : -1;
            if (slot >= 0) {
                ItemStack present = getItem(slot);
                if (present.isEmpty() || ItemStack.isSameItemSameComponents(present, held) && present.getCount() < present.getMaxStackSize()) {
                    setItem(slot, held.copyWithCount(present.getCount() + 1)); held.shrink(1); player.getInventory().setChanged();
                }
            }
        } else {
            for (int slot = 2; slot < 4; slot++) {
                ItemStack result = getItem(slot).copy();
                player.getInventory().add(result);
                setItem(slot, result);
            }
        }
        player.displayClientMessage(Component.translatable("message.skycore.restorer_status", action() == null ? "-" : action().name(), progress,
                state.fuel, state.starvation, state.enabled ? "ON" : "OFF"), false);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || owner == null || !owner.equals(JourneyWorlds.owner(serverLevel)) || !TeamAccess.online(serverLevel.getServer(), owner)) return;
        var data = JourneyWorldData.get(serverLevel.getServer());
        var state = data.state(owner);
        if (getItem(1).is(EntropyContent.WORLD_FUEL.get()) && state.fuel <= 75_600) {
            removeItem(1, 1); state.fuel += 1_200; data.setDirty();
        }
        JourneyAction action = action();
        if (action == null) { progress = 0; working = ""; return; }
        if (!working.equals(action.name())) { progress = 0; working = action.name(); }
        var teams = TeamProgressData.get(serverLevel.getServer());
        if (!teams.has(owner, Milestone.FIRST_TREE.id()) || action.milestone().prerequisites().stream().anyMatch(value -> !teams.has(owner, value.id()))
                || !state.enabled || state.fuel == 0 || !WorldFuelDaylight.isOpenSky(serverLevel, worldPosition.above())) return;
        List<ItemStack> outputs = outputs(action);
        var plan = StackTransactions.insert(List.of(getItem(2), getItem(3)), outputs);
        if (plan.isEmpty()) return;
        if (++progress < 200) { setChanged(); return; }
        if (!perform(serverLevel, action)) { progress = 200; setChanged(); return; }
        removeItem(0, 1); setItem(2, plan.get().get(0)); setItem(3, plan.get().get(1)); progress = 0;
        if (teams.grantJourney(owner, action.milestone()) && (action == JourneyAction.RAIN || action == JourneyAction.STORM)) {
            state.weatherTick = 0;
            data.setDirty();
        }
        setChanged();
    }

    private List<ItemStack> outputs(JourneyAction action) {
        return switch (action) {
            case RAIN -> List.of(new ItemStack(CoreContent.CONDENSATE.get(), 4));
            case SNOW -> List.of(new ItemStack(Items.SNOW_BLOCK, 8), new ItemStack(Items.ICE, 2));
            case STORM -> List.of(new ItemStack(Items.REDSTONE, 8), new ItemStack(Items.COPPER_INGOT, 4));
            case MOSS -> List.of(new ItemStack(Items.MOSS_BLOCK, 8), new ItemStack(Items.BONE_MEAL, 4));
            case ECOSYSTEM -> List.of(new ItemStack(Items.WHEAT_SEEDS, 8), new ItemStack(Items.SUGAR_CANE, 2));
            case COGNITION -> List.of(new ItemStack(JourneyContent.SUBSTRATE.get()));
            case VILLAGER -> List.of(new ItemStack(Items.EMERALD, 4));
            case ARCHIVE -> List.of(new ItemStack(Items.WRITABLE_BOOK));
        };
    }

    private boolean perform(ServerLevel level, JourneyAction action) {
        BlockPos output = worldPosition.south(2);
        if (action == JourneyAction.RAIN || action == JourneyAction.ARCHIVE || action == JourneyAction.COGNITION) return true;
        if (action == JourneyAction.SNOW) {
            var chunk = level.getChunk((worldPosition.getX() >> 4) + 1, worldPosition.getZ() >> 4);
            var biome = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> biome, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
            level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
            return true;
        }
        if (!level.getBlockState(output).canBeReplaced() || !level.getBlockState(output.above()).canBeReplaced()) return false;
        if (!level.getBlockState(output.below()).isFaceSturdy(level, output.below(), Direction.UP)) return false;
        if (action == JourneyAction.MOSS) { level.setBlock(output.below(), Blocks.MOSS_BLOCK.defaultBlockState(), 3); return true; }
        if (action == JourneyAction.STORM) {
            var lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning == null) return false;
            lightning.setPos(output.getCenter()); lightning.setVisualOnly(true);
            return level.addFreshEntity(lightning);
        }
        List<Entity> entities = new ArrayList<>();
        if (action == JourneyAction.VILLAGER) {
            var villager = EntityType.VILLAGER.create(level);
            if (villager == null) return false;
            entities.add(villager);
        } else {
            for (var type : List.of(EntityType.COW, EntityType.COW, EntityType.CHICKEN, EntityType.CHICKEN, EntityType.SHEEP, EntityType.SHEEP)) {
                var animal = type.create(level);
                if (animal == null) return false;
                entities.add(animal);
            }
        }
        List<Entity> added = new ArrayList<>();
        for (Entity entity : entities) {
            entity.setPos(output.getX() + 0.5, output.getY(), output.getZ() + 0.5);
            entity.getPersistentData().putUUID("SkyCoreCreatedTeam", owner);
            if (!level.addFreshEntity(entity)) { added.forEach(Entity::discard); return false; }
            added.add(entity);
        }
        return true;
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); progress = Math.clamp(tag.getInt("JourneyProgress"), 0, 200); working = tag.getString("JourneyAction");
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("JourneyProgress", progress); tag.putString("JourneyAction", working);
    }
}
