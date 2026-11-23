package dev.skycore.journey.mixin;

import dev.skycore.journey.JourneyWorlds;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void skycore$teamWeather(CallbackInfo callback) {
        if (JourneyWorlds.weather((ServerLevel) (Object) this)) callback.cancel();
    }
}
