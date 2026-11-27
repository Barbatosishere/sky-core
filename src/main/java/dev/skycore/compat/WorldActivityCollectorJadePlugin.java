package dev.skycore.compat;

import dev.skycore.SkyCore;
import dev.skycore.block.WorldActivityCollectorBlock;
import dev.skycore.block.WorldActivityCollectorBlockEntity;
import dev.skycore.logic.WorldFuelCycle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class WorldActivityCollectorJadePlugin implements IWailaPlugin, IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SkyCore.ID, "world_activity_progress");
    private static final String TICKS = "SkyCoreCollectionTicks";

    @Override public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(this, WorldActivityCollectorBlockEntity.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(this, WorldActivityCollectorBlock.class);
    }

    @Override public ResourceLocation getUid() { return ID; }

    @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof WorldActivityCollectorBlockEntity collector) {
            data.putInt(TICKS, collector.collectionTicks());
        }
    }

    @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains(TICKS)) {
            tooltip.add(Component.literal(data.getInt(TICKS) + "/" + WorldFuelCycle.COLLECTION_TICKS));
        }
    }
}
