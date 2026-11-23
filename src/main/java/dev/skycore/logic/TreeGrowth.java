package dev.skycore.logic;

import dev.skycore.SkyCore;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class TreeGrowth {
    private TreeGrowth() {}

    public static boolean tryPlace(ServerLevel level, BlockPos soil) {
        if (!level.hasChunksAt(soil.offset(-1, 1, -1), soil.offset(1, 5, 1))) return false;
        for (BlockPos at : BlockPos.betweenClosed(soil.offset(-1, 1, -1), soil.offset(1, 5, 1))) {
            if (level.isOutsideBuildHeight(at) || !level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty()) return false;
        }
        Map<BlockPos, BlockState> target = new LinkedHashMap<>();
        for (int y = 1; y <= 4; y++) target.put(soil.above(y), Blocks.OAK_LOG.defaultBlockState());
        for (int y = 4; y <= 5; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (y == 4 && x == 0 && z == 0) continue;
            int distance = Math.abs(x) + Math.abs(z) + (y - 4);
            target.put(soil.offset(x, y, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, distance));
        }
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        target.keySet().forEach(pos -> previous.put(pos, level.getBlockState(pos)));
        boolean committed = false;
        try {
            for (var entry : target.entrySet()) {
                if (!level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                        && !level.getBlockState(entry.getKey()).equals(entry.getValue())) return false;
            }
            committed = true;
            return true;
        } catch (RuntimeException exception) {
            SkyCore.LOGGER.error("Tree placement failed at {}; restoring the previous blocks", soil, exception);
            return false;
        } finally {
            if (!committed) previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        }
    }
}
