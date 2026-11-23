package dev.skycore.journey.mixin;

import dev.skycore.journey.JourneyWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({NetherPortalBlock.class, EndPortalBlock.class})
public abstract class JourneyPortalMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void skycore$routePortal(ServerLevel source, Entity entity, BlockPos pos, CallbackInfoReturnable<DimensionTransition> callback) {
        String realm = (Object) this instanceof EndPortalBlock ? "end" : "nether";
        callback.setReturnValue(JourneyWorlds.portal(entity, source, realm));
    }
}
