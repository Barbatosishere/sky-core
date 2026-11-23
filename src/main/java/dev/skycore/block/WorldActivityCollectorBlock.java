package dev.skycore.block;

import com.mojang.serialization.MapCodec;
import dev.skycore.CoreContent;
import dev.skycore.progress.TeamAccess;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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

public final class WorldActivityCollectorBlock extends BaseEntityBlock {
    public WorldActivityCollectorBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(WorldActivityCollectorBlock::new); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WorldActivityCollectorBlockEntity(pos, state);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof WorldActivityCollectorBlockEntity collector) {
            TeamAccess.team(player).ifPresent(collector::bind);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof WorldActivityCollectorBlockEntity collector) || !collector.mayUse(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("message.skycore.not_owner"), true);
            return InteractionResult.FAIL;
        }
        serverPlayer.openMenu(collector, pos);
        return InteractionResult.CONSUME;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof WorldActivityCollectorBlockEntity collector) {
            collector.dropInventory();
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, CoreContent.WORLD_ACTIVITY_COLLECTOR_ENTITY.get(), (world, pos, block, collector) -> collector.serverTick());
    }
}
