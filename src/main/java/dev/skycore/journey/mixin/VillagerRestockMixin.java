package dev.skycore.journey.mixin;

import dev.skycore.journey.JourneyEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import dev.skycore.progress.TeamAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerRestockMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void skycore$ownTeamTrade(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        var villager = (Villager) (Object) this;
        var data = villager.getPersistentData();
        if (data.hasUUID("SkyCoreCreatedTeam") && player instanceof ServerPlayer serverPlayer
                && TeamAccess.team(serverPlayer).filter(data.getUUID("SkyCoreCreatedTeam")::equals).isEmpty())
            callback.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method = "shouldRestock", at = @At("HEAD"), cancellable = true)
    private void skycore$pauseRestock(CallbackInfoReturnable<Boolean> callback) {
        if (JourneyEvents.ecologyStopped((Villager) (Object) this, 72_000)) callback.setReturnValue(false);
    }
}
