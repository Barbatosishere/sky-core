package dev.skycore.entropy;

import com.mojang.serialization.MapCodec;
import dev.skycore.progress.TeamAccess;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Interactions execute once on the logical server; chunk unloading is never removal. */
public final class FieldMachineBlock extends BaseEntityBlock {
    private final boolean inverse;

    public FieldMachineBlock(boolean inverse, Properties properties) { super(properties); this.inverse = inverse; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(properties -> new FieldMachineBlock(inverse, properties)); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FieldMachineBlockEntity(inverse, pos, state); }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof FieldMachineBlockEntity entity) {
            TeamAccess.team(player).ifPresent(team -> { entity.bind(team); EntropyRuntime.register(entity); });
        }
    }

    private static void interact(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof FieldMachineBlockEntity entity) EntropyRuntime.interact(entity, serverPlayer);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        interact(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        interact(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!oldState.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof FieldMachineBlockEntity entity) EntropyRuntime.remove(entity);
        super.onRemove(oldState, level, pos, newState, moved);
    }
}
