package dev.skycore.worldfuel;

import dev.skycore.logic.WorldFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;

public final class WorldFuelDaylight {
    private WorldFuelDaylight() {}

    public static int signal(int skyLight, int skyDarken, float sunAngle, boolean inverted) {
        int power = skyLight - skyDarken;
        if (inverted) {
            power = 15 - power;
        } else if (power > 0) {
            float target = sunAngle < (float) Math.PI ? 0 : (float) (Math.PI * 2);
            float adjustedAngle = sunAngle + (target - sunAngle) * 0.2F;
            power = Math.round(power * Mth.cos(adjustedAngle));
        }
        return Mth.clamp(power, 0, 15);
    }

    public static WorldFuelCycle.Phase phase(Level level, BlockPos pos) {
        if (!isOpenSky(level, pos) || level.dimensionType().hasFixedTime()) return WorldFuelCycle.Phase.TWILIGHT;
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        int skyDarken = level.getSkyDarken();
        float sunAngle = level.getSunAngle(1.0F);
        return WorldFuelCycle.phase(signal(skyLight, skyDarken, sunAngle, false), signal(skyLight, skyDarken, sunAngle, true));
    }

    public static boolean isOpenSky(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) return false;
        var surface = pos.mutable();
        while (surface.getY() < level.getMaxBuildHeight() && level.getFluidState(surface).is(FluidTags.WATER)) surface.move(Direction.UP);
        return level.canSeeSky(surface);
    }
}
