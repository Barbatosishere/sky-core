package dev.skycore.worldfuel;

import dev.skycore.logic.WorldFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

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
        if (skyLight != 15) return WorldFuelCycle.Phase.TWILIGHT;
        int skyDarken = level.getSkyDarken();
        float sunAngle = level.getSunAngle(1.0F);
        var phase = WorldFuelCycle.phase(signal(skyLight, skyDarken, sunAngle, false), signal(skyLight, skyDarken, sunAngle, true));
        float sunElevation = Mth.cos(sunAngle);
        if (phase == WorldFuelCycle.Phase.DAY && sunElevation <= 0) return WorldFuelCycle.Phase.TWILIGHT;
        if (phase == WorldFuelCycle.Phase.NIGHT && sunElevation >= 0) return WorldFuelCycle.Phase.TWILIGHT;
        return phase;
    }

    public static boolean isOpenSky(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) return false;
        var surface = pos.mutable();
        while (surface.getY() < level.getMaxBuildHeight() && level.getFluidState(surface).is(FluidTags.WATER)) surface.move(Direction.UP);
        int lowestSkySourceY = level.getChunkAt(surface).getSkyLightSources()
                .getLowestSourceY(SectionPos.sectionRelative(surface.getX()), SectionPos.sectionRelative(surface.getZ()));
        return surface.getY() >= lowestSkySourceY && level.canSeeSky(surface);
    }
}
