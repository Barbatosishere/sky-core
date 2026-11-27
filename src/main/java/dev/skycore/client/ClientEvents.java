package dev.skycore.client;

import dev.skycore.CoreContent;
import dev.skycore.SkyCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SkyCore.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(CoreContent.WORKBENCH_MENU.get(), WorkbenchScreen::new);
        event.register(CoreContent.WORLD_FUEL_SYNTHESIZER_MENU.get(), WorldFuelSynthesizerScreen::new);
    }
    private ClientEvents() {}
}
