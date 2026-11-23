package dev.skycore.journey;

import dev.skycore.progress.JourneyMilestone;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

public final class JourneyEvents {
    private record Trade(JourneyMilestone milestone, Item output, int emeralds) {}
    private static final Map<VillagerProfession, Trade> TRADES = Map.of(
            VillagerProfession.FARMER, new Trade(JourneyMilestone.FARMER, Items.GOLDEN_CARROT, 3),
            VillagerProfession.LIBRARIAN, new Trade(JourneyMilestone.LIBRARIAN, Items.NAME_TAG, 12),
            VillagerProfession.CLERIC, new Trade(JourneyMilestone.CLERIC, Items.EXPERIENCE_BOTTLE, 8),
            VillagerProfession.ARMORER, new Trade(JourneyMilestone.ARMORER, Items.DIAMOND_CHESTPLATE, 24),
            VillagerProfession.TOOLSMITH, new Trade(JourneyMilestone.TOOLSMITH, Items.DIAMOND_PICKAXE, 16),
            VillagerProfession.WEAPONSMITH, new Trade(JourneyMilestone.WEAPONSMITH, Items.DIAMOND_SWORD, 12));

    private JourneyEvents() {}
    public static void register(IEventBus bus) {
        bus.addListener(JourneyEvents::capabilities);
        NeoForge.EVENT_BUS.addListener(JourneyWorlds::commands);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::tick);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::playerTick);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::guardRealm);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::guardInteraction);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::guardBreak);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::travel);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::trade);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::entityTick);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::growth);
        NeoForge.EVENT_BUS.addListener(JourneyEvents::breeding);
        dev.ftb.mods.ftbteams.api.event.TeamEvent.PLAYER_CHANGED.register(event -> {
            if (event.getPlayer() != null) event.getPlayer().closeContainer();
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, JourneyContent.RESTORER_ENTITY.get(), (entity, side) -> new SidedInvWrapper(entity, side));
    }

    private static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var data = JourneyWorldData.get(server);
        var progress = TeamProgressData.get(server);
        data.forEach((team, state) -> {
            if (!TeamAccess.online(server, team)) return;
            if (state.enabled && state.fuel > 0) { state.fuel--; state.starvation = 0; }
            else state.starvation = Math.min(72_000, state.starvation + 1);
            if (state.enabled && state.starvation < 12_000 && progress.has(team, JourneyMilestone.RAIN.id()))
                state.weatherTick = (state.weatherTick + 1) % JourneyWorlds.WEATHER_PERIOD;
            data.setDirty();
        });
    }

    private static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (!data.hasUUID("SkyCoreFlightTeam")) return;
        UUID team = TeamAccess.team(player).orElse(null);
        if (!data.getUUID("SkyCoreFlightTeam").equals(team) || player.level().getGameTime() - data.getLong("SkyCoreFlightStart") > 400) {
            data.remove("SkyCoreFlightTeam"); return;
        }
        if (JourneyWorlds.owned(player, player.serverLevel()) && player.getY() >= data.getDouble("SkyCoreFlightY") + 4 && !player.onGround()) {
            TeamProgressData.get(player.server).grantJourney(team, JourneyMilestone.FLIGHT);
            data.remove("SkyCoreFlightTeam");
        }
    }

    private static boolean foreignRealm(ServerPlayer player) {
        return JourneyWorlds.slot(player.level().dimension()) >= 0
                && TeamAccess.team(player).isPresent() && !JourneyWorlds.owned(player, player.serverLevel());
    }

    private static void guardRealm(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !foreignRealm(player)) return;
        if (JourneyWorlds.enter(player, "sky") == 0) {
            var fallback = player.server.overworld();
            var spawn = fallback.getSharedSpawnPos();
            player.teleportTo(fallback, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, java.util.Set.of(), 0, 0);
            player.setRespawnPosition(fallback.dimension(), spawn, 0, true, false);
        }
    }

    private static void guardInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && foreignRealm(player)) event.setCanceled(true);
    }

    private static void guardBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && foreignRealm(player)) event.setCanceled(true);
    }

    private static void travel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int targetSlot = JourneyWorlds.slot(event.getDimension());
        if (targetSlot >= 0) {
            UUID owner = JourneyWorldData.get(player.server).owner(targetSlot);
            String path = event.getDimension().location().getPath();
            boolean allowed = owner != null && TeamAccess.team(player).filter(owner::equals).isPresent()
                    && JourneyWorlds.unlocked(TeamProgressData.get(player.server), owner, path.substring(path.lastIndexOf('/') + 1));
            if (!allowed) event.setCanceled(true);
        } else if (JourneyWorlds.sharedRealm(event.getDimension()) instanceof String realm) {
            event.setCanceled(true);
            player.server.execute(() -> JourneyWorlds.enter(player, realm));
        }
    }

    public static boolean ecologyStopped(Entity entity, long threshold) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        UUID team = JourneyWorlds.owner(level);
        var state = team == null ? null : JourneyWorldData.get(level.getServer()).state(team);
        return state != null && (!TeamAccess.online(level.getServer(), team) || state.starvation >= threshold);
    }

    private static void growth(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        UUID team = JourneyWorlds.owner(level);
        var state = team == null ? null : JourneyWorldData.get(level.getServer()).state(team);
        if (state == null) return;
        if (!TeamAccess.online(level.getServer(), team) || state.starvation >= 36_000 || state.starvation >= 12_000 && level.random.nextInt(4) != 0)
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    private static void breeding(BabyEntitySpawnEvent event) {
        if (ecologyStopped(event.getParentA(), 36_000)) { event.setCanceled(true); return; }
        var first = event.getParentA().getPersistentData();
        var second = event.getParentB().getPersistentData();
        if (event.getChild() != null && first.hasUUID("SkyCoreCreatedTeam") && second.hasUUID("SkyCoreCreatedTeam")
                && first.getUUID("SkyCoreCreatedTeam").equals(second.getUUID("SkyCoreCreatedTeam")))
            event.getChild().getPersistentData().putUUID("SkyCoreCreatedTeam", first.getUUID("SkyCoreCreatedTeam"));
    }

    private static void entityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)
                || villager.tickCount % 20 != 0 || !villager.getPersistentData().hasUUID("SkyCoreCreatedTeam")) return;
        Trade trade = TRADES.get(villager.getVillagerData().getProfession());
        if (trade == null) return;
        String key = trade.milestone.id();
        if (!key.equals(villager.getPersistentData().getString("SkyCoreInstalledTrade"))) {
            MerchantOffers offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, trade.emeralds), new ItemStack(trade.output), 16, 5, 0));
            villager.setOffers(offers);
            villager.getPersistentData().putString("SkyCoreInstalledTrade", key);
        }
    }

    private static void trade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getAbstractVillager() instanceof Villager villager)
                || !villager.getPersistentData().hasUUID("SkyCoreCreatedTeam") || ecologyStopped(villager, 72_000)) return;
        UUID team = TeamAccess.team(player).orElse(null);
        Trade trade = TRADES.get(villager.getVillagerData().getProfession());
        if (trade != null && team != null && team.equals(villager.getPersistentData().getUUID("SkyCoreCreatedTeam"))
                && event.getMerchantOffer().getUses() > 0 && event.getMerchantOffer().getResult().is(trade.output))
            TeamProgressData.get(player.server).grantJourney(team, trade.milestone);
    }
}
