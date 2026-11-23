package dev.skycore.entropy;

import dev.skycore.api.SkyCoreApi;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Idempotent admin initialization and read-only shelter status. */
public final class EntropyCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skycore")
                .then(Commands.literal("shelter")
                        .then(Commands.literal("status")
                                .executes(context -> show(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).requires(source -> source.hasPermission(2))
                                        .executes(context -> show(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("initialize").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                                                    var result = EntropyRuntime.initialize(EntityArgument.getPlayer(context, "player"),
                                                            DimensionArgument.getDimension(context, "dimension"), BlockPosArgument.getBlockPos(context, "pos"));
                                                    if (!result.successful()) {
                                                        context.getSource().sendFailure(Component.translatable("message.skycore.shelter_init_failed", result.name()));
                                                        return 0;
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal(result.name()), true);
                                                    return 1;
                                                })))))));
    }

    private static int show(net.minecraft.commands.CommandSourceStack source, net.minecraft.server.level.ServerPlayer player) {
        String snapshot = SkyCoreApi.entropySnapshotJson(player);
        if (snapshot == null) {
            source.sendFailure(Component.translatable("message.skycore.entropy_not_ready"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(snapshot), false);
        return 1;
    }

    private EntropyCommands() {}
}
