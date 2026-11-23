package dev.skycore;

import com.mojang.logging.LogUtils;
import dev.skycore.api.SkyCoreApi;
import dev.skycore.block.OwnedBlockEntity;
import dev.skycore.entropy.EntropyConfig;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.journey.JourneyContent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

@Mod(SkyCore.ID)
public final class SkyCore {
    public static final String ID = "skycore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyCore(IEventBus bus, ModContainer container) {
        CoreContent.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, EntropyConfig.SPEC, "skycore-server.toml");
        bus.addListener(this::creativeTab);
        NeoForge.EVENT_BUS.addListener(this::breakBlock);
        NeoForge.EVENT_BUS.addListener(this::commands);
    }
    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            CoreContent.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
            EntropyContent.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
            JourneyContent.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }
    private void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof OwnedBlockEntity owned
                && event.getPlayer() instanceof ServerPlayer player && !owned.mayUse(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.skycore.not_owner"), true);
        }
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skycore")
                .then(Commands.literal("progress")
                        .executes(context -> showProgress(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(source -> source.hasPermission(2))
                                .executes(context -> showProgress(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("milestone").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("test").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("milestone_id", ResourceLocationArgument.id())
                                        .executes(context -> SkyCoreApi.hasMilestone(EntityArgument.getPlayer(context, "player"),
                                                ResourceLocationArgument.getId(context, "milestone_id").toString()) ? 1 : 0))))));
    }
    private static int showProgress(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
        String snapshot = SkyCoreApi.snapshotJson(player);
        if (snapshot == null) { source.sendFailure(Component.translatable("message.skycore.team_not_ready")); return 0; }
        source.sendSuccess(() -> Component.literal(snapshot), false);
        return 1;
    }
}
