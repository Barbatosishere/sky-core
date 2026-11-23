package dev.skycore.block;

import com.mojang.serialization.MapCodec;
import dev.skycore.CoreContent;
import dev.skycore.progress.Milestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CoreBlock extends BaseEntityBlock {
    public enum Kind { ROCK, WORKBENCH, SOIL }
    private final Kind kind;
    public CoreBlock(Kind kind, Properties properties) { super(properties); this.kind = kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(properties -> new CoreBlock(kind, properties)); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) { case ROCK -> new RockCoreBlockEntity(pos, state); case WORKBENCH -> new WorkbenchBlockEntity(pos, state); case SOIL -> new SoilBlockEntity(pos, state); };
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof OwnedBlockEntity entity) {
            TeamAccess.team(player).ifPresent(team -> {
                entity.bind(team);
                if (kind == Kind.WORKBENCH) TeamProgressData.get(player.getServer()).grant(team, Milestone.WORKBENCH_PLACED);
            });
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return player instanceof ServerPlayer serverPlayer && use(level, pos, serverPlayer) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return player instanceof ServerPlayer serverPlayer && use(level, pos, serverPlayer) ? ItemInteractionResult.CONSUME : ItemInteractionResult.FAIL;
    }
    private boolean use(Level level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof OwnedBlockEntity entity) || !entity.mayUse(player)) {
            player.displayClientMessage(Component.translatable("message.skycore.not_owner"), true);
            return false;
        }
        return switch (kind) {
            case ROCK -> ((RockCoreBlockEntity) entity).collect(player);
            case WORKBENCH -> { player.openMenu((WorkbenchBlockEntity) entity, pos); yield true; }
            case SOIL -> ((SoilBlockEntity) entity).begin(player);
        };
    }

    @Override protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!oldState.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof OwnedInventoryBlockEntity inventory && !level.isClientSide) {
            Containers.dropContents(level, pos, inventory);
            inventory.clearContent();
        }
        super.onRemove(oldState, level, pos, newState, moved);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return switch (kind) {
            case ROCK -> null;
            case WORKBENCH -> createTickerHelper(type, CoreContent.WORKBENCH_ENTITY.get(), (l, p, s, entity) -> entity.serverTick());
            case SOIL -> createTickerHelper(type, CoreContent.SOIL_ENTITY.get(), (l, p, s, entity) -> entity.serverTick());
        };
    }
}
