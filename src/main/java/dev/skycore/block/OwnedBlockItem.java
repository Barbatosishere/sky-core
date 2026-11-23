package dev.skycore.block;

import dev.skycore.progress.TeamAccess;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OwnedBlockItem extends BlockItem {
    public OwnedBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        // Inventory and team ownership live in the placed block, never in portable block-item NBT.
        return false;
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if (!context.getLevel().isClientSide && (!(context.getPlayer() instanceof ServerPlayer player) || TeamAccess.team(player).isEmpty())) {
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("message.skycore.team_not_ready"), true);
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
