package dev.skycore.entropy;

import dev.skycore.SkyCore;
import dev.skycore.journey.*;
import dev.skycore.progress.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Only compiled into the dedicated QA source set. Reads the previous JVM's real saved state. */
@EventBusSubscriber(modid = SkyCore.ID)
public final class JourneyRestartProbe {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        var weather = server.getCommandStorage().get(ResourceLocation.parse("skycore:journey_weather_qa"));
        if (weather.contains("State")) {
            var expected = weather.getCompound("State");
            var team = expected.getUUID("Id");
            var sky = server.getLevel(JourneyWorlds.key(expected.getInt("Slot"), "sky"));
            if (sky == null || !expected.equals(JourneyTests.worldRecord(sky, team)))
                throw new IllegalStateException("Journey disk restart changed weather phase, fuel or team ownership");
            SkyCore.LOGGER.info("JOURNEY_WEATHER_RESTART_VERIFIED team={} phase={} fuel={}", team, expected.getInt("WeatherTick"), expected.getLong("Fuel"));
        }
        var marker = server.getCommandStorage().get(ResourceLocation.parse("skycore:journey_qa"));
        if (!marker.getBoolean("Complete")) return;
        var team = marker.getUUID("Team");
        var state = JourneyWorldData.get(server).state(team);
        var level = server.getLevel(JourneyWorlds.key(marker.getInt("Slot"), "sky"));
        var pos = BlockPos.of(marker.getLong("Restorer"));
        // LevelReader.getNoiseBiome(..., create=false) otherwise falls back to the generator for an unloaded chunk.
        if (level != null) level.getChunk(new BlockPos(20, 100, 8));
        if (state == null || state.slot != marker.getInt("Slot") || level == null
                || !TeamProgressData.get(server).has(team, JourneyMilestone.CIVILIZATION.id())
                || !level.getBiome(new BlockPos(20, 100, 8)).is(Biomes.SNOWY_PLAINS))
            throw new IllegalStateException("Journey disk restart lost state: assignment=" + (state == null ? "null" : state.slot)
                    + " expected=" + marker.getInt("Slot") + " civilization=" + TeamProgressData.get(server).has(team, JourneyMilestone.CIVILIZATION.id())
                    + " cold=" + (level != null && level.getBiome(new BlockPos(20, 100, 8)).is(Biomes.SNOWY_PLAINS)));
        level.getChunk(pos);
        if (!(level.getBlockEntity(pos) instanceof WorldRestorerBlockEntity machine)
                || !machine.getItem(0).is(JourneyContent.CORES.get(JourneyAction.SNOW).get()))
            throw new IllegalStateException("Journey disk restart lost pending core");
        SkyCore.LOGGER.info("JOURNEY_RESTART_VERIFIED team={} slot={} milestone=sky_civilization pending=frost_core", team, state.slot);
    }
}
