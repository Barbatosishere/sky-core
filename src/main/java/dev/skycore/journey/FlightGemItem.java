package dev.skycore.journey;

import dev.skycore.progress.Milestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FlightGemItem extends Item {
    public FlightGemItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(held);
        var team = TeamAccess.team(serverPlayer).orElse(null);
        if (team == null || !TeamProgressData.get(serverPlayer.server).has(team, Milestone.FIRST_TREE.id())
                || !JourneyWorlds.owned(serverPlayer, serverPlayer.serverLevel())) {
            player.displayClientMessage(Component.translatable("message.skycore.journey_use_world"), true);
            return InteractionResultHolder.fail(held);
        }
        var data = player.getPersistentData();
        data.putDouble("SkyCoreFlightY", player.getY());
        data.putLong("SkyCoreFlightStart", level.getGameTime());
        data.putUUID("SkyCoreFlightTeam", team);
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 160, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
        player.getCooldowns().addCooldown(this, 200);
        return InteractionResultHolder.consume(held);
    }
}
