package dev.skycore.entropy;

import dev.skycore.CoreContent;
import dev.skycore.journey.*;
import dev.skycore.logic.WorldFuelCycle;
import dev.skycore.progress.*;
import dev.skycore.worldfuel.WorldFuelDaylight;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("skycore")
@PrefixGameTestTemplate(false)
public final class JourneyTests {
    private static void observe(GameTestHelper helper, String name) {
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) {}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) {
                var server = helper.getLevel().getServer();
                var marker = server.getCommandStorage().get(ResourceLocation.parse("skycore:journey_checks")).copy();
                marker.putBoolean(name, true); server.getCommandStorage().set(ResourceLocation.parse("skycore:journey_checks"), marker);
                dev.skycore.SkyCore.LOGGER.info("JOURNEY_TEST_PASS {}", name);
            }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { dev.skycore.SkyCore.LOGGER.error("JOURNEY_TEST_FAIL {}", name); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldInfo, net.minecraft.gametest.framework.GameTestInfo newInfo, net.minecraft.gametest.framework.GameTestRunner runner) {}
        });
    }
    private static void chapterOne(ServerPlayer player) {
        var data = TeamProgressData.get(player.server);
        for (Milestone milestone : Milestone.values()) data.grant(EntropyTests.team(player), milestone);
    }

    @GameTest(template = "empty", batch = "journey_isolation", timeoutTicks = 140)
    public static void worldsAreDistinctAndGatesAndLegacyShelterSurvive(GameTestHelper helper) {
        observe(helper, "worlds_gates_legacy_shelter");
        var player = EntropyTests.player(helper);
        var second = EntropyTests.player(helper);
        UUID team = EntropyTests.team(player), other = EntropyTests.team(second);
        var oldPos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(EntropyRuntime.initialize(player, helper.getLevel(), oldPos).successful(), "Initialize original one-time shelter");
        var oldShelter = (FieldMachineBlockEntity) helper.getLevel().getBlockEntity(oldPos);
        var data = JourneyWorldData.get(player.server);
        helper.assertValueEqual(JourneyWorlds.enter(player, "nether"), 0, "Nether locked before thunder");
        helper.assertValueEqual(JourneyWorlds.enter(player, "end"), 0, "End locked before cognition");
        for (var realm : List.of(Level.NETHER, Level.END)) {
            var source = player.serverLevel();
            player.teleportTo(player.server.getLevel(realm), 8.5, 100, 6.5, 0, 0);
            helper.assertTrue(player.serverLevel() == source, "Direct shared-world transfer honors the milestone gate");
        }
        helper.assertValueEqual(JourneyWorlds.enter(player, "sky"), 1, "Real sky dimension exists and accepts owner");
        helper.assertValueEqual(JourneyWorlds.enter(second, "sky"), 1, "Second team enters its own sky");
        helper.assertFalse(player.level() == second.level(), "Distinct actual ServerLevels");
        helper.assertFalse(JourneyWorlds.owned(player, second.serverLevel()), "Other team's dimension not owned");
        var home = player.serverLevel();
        player.teleportTo(second.serverLevel(), 8.5, 100, 6.5, java.util.Set.of(), 0, 0);
        helper.assertTrue(player.serverLevel() == home, "Actual cross-team teleport canceled by travel event");
        int slot = data.state(team).slot;
        helper.assertTrue(data.owner(slot).equals(team) && !data.owner(slot).equals(other), "Stable owner assignment");
        helper.assertValueEqual(player.server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).keySet().stream()
                .filter(id -> id.getNamespace().equals("skycore") && id.getPath().startsWith("team/")).count(), 192L, "All realm codecs loaded");
        var teams = TeamProgressData.get(player.server);
        helper.assertFalse(teams.grantJourney(team, JourneyMilestone.RAIN), "Cannot skip first tree/flight");
        chapterOne(player);
        helper.assertFalse(teams.grantJourney(team, JourneyMilestone.RAIN), "Cannot skip actual flight record");
        helper.assertTrue(teams.grantJourney(team, JourneyMilestone.FLIGHT), "Fixture grants prerequisite");
        helper.assertTrue(teams.grantJourney(team, JourneyMilestone.RAIN), "Rain prerequisite chain");
        helper.assertTrue(teams.grantJourney(team, JourneyMilestone.STORM), "Thunder prerequisite chain");
        helper.assertValueEqual(JourneyWorlds.enter(player, "nether"), 1, "Nether opens after thunder");
        helper.assertValueEqual(JourneyWorlds.enter(player, "end"), 0, "End remains locked");
        var ownNether = player.serverLevel();
        player.teleportTo(player.server.overworld(), oldPos.getX() + 0.5, oldPos.getY() + 1, oldPos.getZ() + 0.5, 0, 0);
        player.teleportTo(player.server.getLevel(Level.NETHER), 8.5, 100, 6.5, 0, 0);
        helper.assertTrue(player.serverLevel() == ownNether, "Direct legacy transfer routes into the same team's Nether");
        helper.assertValueEqual(JourneyWorlds.enter(player, "sky"), 1, "Return home");
        var restored = TeamProgressData.load(teams.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
        helper.assertTrue(restored.has(team, JourneyMilestone.STORM.id()) && !restored.has(other, JourneyMilestone.STORM.id()), "Saved milestones remain team-specific");
        helper.assertValueEqual(data.allocate(team).slot, slot, "Repeated entry never reallocates a world");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(player.serverLevel().isThundering(), "Actual owner dimension thunderstorms");
            helper.assertFalse(second.serverLevel().isRaining(), "Other sky remains dry");
            helper.assertTrue(helper.getLevel().getBlockEntity(oldShelter.getBlockPos()) == oldShelter, "Original shelter retained");
            helper.succeed();
        });
    }

    static CompoundTag worldRecord(ServerLevel level, UUID team) {
        var server = level.getServer();
        return JourneyWorldData.get(server).save(new CompoundTag(), server.registryAccess()).getList("Teams", Tag.TAG_COMPOUND)
                .stream().map(CompoundTag.class::cast).filter(entry -> entry.getUUID("Id").equals(team)).findFirst().orElseThrow();
    }

    @GameTest(template = "empty", batch = "journey_weather", timeoutTicks = 16_000)
    public static void stormRetainsNaturalDaylightAndPausesOffline(GameTestHelper helper) {
        observe(helper, "natural_daylight_offline");
        var player = EntropyTests.player(helper);
        var other = EntropyTests.player(helper);
        var server = player.server;
        UUID team = EntropyTests.team(player);
        chapterOne(player);
        var progress = TeamProgressData.get(server);
        progress.grantJourney(team, JourneyMilestone.FLIGHT);
        progress.grantJourney(team, JourneyMilestone.RAIN);
        progress.grantJourney(team, JourneyMilestone.STORM);
        helper.assertValueEqual(JourneyWorlds.enter(player, "sky"), 1, "Storm team enters its sky");
        helper.assertValueEqual(JourneyWorlds.enter(other, "sky"), 1, "Dry team enters its sky");
        var sky = player.serverLevel();
        var state = JourneyWorldData.get(server).state(team);
        state.fuel = 30_000;
        server.overworld().setDayTime(18_000);
        helper.assertTrue(sky.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT), "Natural daylight cycle is running");
        var stormTicks = new AtomicInteger();
        var clearDayTicks = new AtomicInteger();
        var recorded = new CompoundTag();
        helper.onEachTick(() -> {
            helper.assertFalse(other.serverLevel().isRaining() || other.serverLevel().isThundering(), "Other team's sky stays dry");
            if (!recorded.isEmpty()) {
                if (helper.getTick() - recorded.getLong("OfflineSince") < 40) return;
                helper.assertTrue(recorded.getCompound("State").equals(worldRecord(sky, team)), "Weather phase, fuel and starvation pause together offline");
                server.getCommandStorage().set(ResourceLocation.parse("skycore:journey_weather_qa"), recorded);
                helper.succeed();
                return;
            }
            if (sky.isThundering()) stormTicks.incrementAndGet();
            if (WorldFuelDaylight.phase(sky, new BlockPos(8, 102, 6)) != WorldFuelCycle.Phase.DAY) {
                clearDayTicks.set(0);
                return;
            }
            if (clearDayTicks.incrementAndGet() < WorldFuelCycle.REFINEMENT_TICKS) return;
            helper.assertTrue(stormTicks.get() >= 1_190, "Observed sustained real thunder before naturally arriving at the clear window");
            helper.assertTrue(state.fuel > 0 && state.starvation == 0, "Clear window arrived without loss of supply");
            dev.skycore.SkyCore.LOGGER.info("JOURNEY_WEATHER_WINDOW stormTicks={} continuousNaturalDayTicks={}", stormTicks.get(), clearDayTicks.get());
            server.getPlayerList().remove(player);
            recorded.put("State", worldRecord(sky, team));
            recorded.putLong("OfflineSince", helper.getTick());
        });
    }

    @GameTest(template = "empty", batch = "journey_team_change", timeoutTicks = 100)
    public static void changingTeamLeavesOldWorldAndKeepsOldRecords(GameTestHelper helper) {
        observe(helper, "team_change_ejection");
        var player = EntropyTests.player(helper);
        JourneyWorlds.enter(player, "sky");
        UUID previous = EntropyTests.team(player);
        var old = player.serverLevel();
        chapterOne(player);
        try {
            dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().createPartyTeam(player,
                    "journey-" + player.getUUID().toString().substring(0, 8), "", dev.ftb.mods.ftblibrary.icon.Color4I.WHITE);
        } catch (Exception failure) { throw new RuntimeException(failure); }
        helper.runAfterDelay(5, () -> {
            UUID now = EntropyTests.team(player);
            helper.assertFalse(now.equals(previous), "Actual FTB membership changed");
            helper.assertFalse(player.serverLevel() == old, "Player is moved out of former team's world");
            helper.assertTrue(JourneyWorlds.owned(player, player.serverLevel()), "Moved into own new world");
            var progress = TeamProgressData.get(player.server);
            helper.assertTrue(progress.has(previous, Milestone.FIRST_TREE.id()), "Old team's progress retained");
            helper.assertFalse(progress.has(now, Milestone.FIRST_TREE.id()), "New team cannot inherit old milestones");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "journey_supply", timeoutTicks = 120)
    public static void onlineSupplyOfflinePauseAndSidedOwnership(GameTestHelper helper) {
        observe(helper, "supply_offline_sides");
        var player = EntropyTests.player(helper);
        var stranger = EntropyTests.player(helper);
        JourneyWorlds.enter(player, "sky");
        UUID team = EntropyTests.team(player);
        var level = player.serverLevel();
        BlockPos pos = new BlockPos(7, 100, 8);
        level.setBlock(pos, JourneyContent.RESTORER.get().defaultBlockState(), 3);
        var machine = (WorldRestorerBlockEntity) level.getBlockEntity(pos); machine.bind(team);
        var state = JourneyWorldData.get(player.server).state(team);
        state.fuel = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get(), 2));
        machine.interact(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Owner inserts one real fuel");
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EntropyContent.WORLD_FUEL.get()));
        machine.interact(stranger);
        helper.assertValueEqual(stranger.getMainHandItem().getCount(), 1, "Other team cannot insert fuel");
        var top = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        var side = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.EAST);
        var bottom = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
        helper.assertTrue(top.insertItem(0, new ItemStack(EntropyContent.WORLD_FUEL.get()), false).isEmpty() == false, "Top rejects fuel");
        helper.assertTrue(side.insertItem(0, new ItemStack(JourneyContent.CORES.get(JourneyAction.RAIN).get()), false).isEmpty() == false, "Side rejects cores");
        helper.assertValueEqual(bottom.getSlots(), 2, "Two product slots");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(state.fuel > 0 && state.fuel < 1200, "Fuel actually consumed by online ticks; remaining=" + state.fuel);
            state.fuel = 80; state.starvation = 123;
            player.server.getPlayerList().remove(player);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertValueEqual(state.fuel, 80L, "Full team offline pauses fuel");
            helper.assertValueEqual(state.starvation, 123L, "Full team offline pauses decline");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "journey_chain", timeoutTicks = 2600)
    public static void physicalRitualsAndSixRealMerchantTransactionsCompleteJourney(GameTestHelper helper) {
        observe(helper, "physical_rituals_six_paid_trades");
        var player = EntropyTests.player(helper);
        UUID team = EntropyTests.team(player);
        chapterOne(player); // Chapter-one recipes/tree have their own existing native tests.
        JourneyWorlds.enter(player, "sky");
        var level = player.serverLevel();
        var teams = TeamProgressData.get(player.server);
        var state = JourneyWorldData.get(player.server).state(team);
        // The gem must actually be used. The simulated client then reports its ascent.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(JourneyContent.FLIGHT_GEM.get()));
        JourneyContent.FLIGHT_GEM.get().use(level, player, InteractionHand.MAIN_HAND);
        helper.assertFalse(teams.has(team, JourneyMilestone.FLIGHT.id()), "Using the gem on the ground is insufficient");
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION), "Gem applies real flight effect");
        player.setPos(player.getX(), player.getY() + 5, player.getZ()); player.setOnGround(false);
        for (BlockPos floor : BlockPos.betweenClosed(new BlockPos(3, 99, 3), new BlockPos(14, 99, 14))) level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        BlockPos pos = new BlockPos(8, 100, 5);
        level.setBlock(pos, JourneyContent.RESTORER.get().defaultBlockState(), 3);
        var machine = (WorldRestorerBlockEntity) level.getBlockEntity(pos); machine.bind(team);
        state.fuel = 50_000; state.starvation = 0;
        AtomicInteger step = new AtomicInteger(0);
        List<JourneyAction> sequence = List.of(JourneyAction.RAIN, JourneyAction.SNOW, JourneyAction.STORM, JourneyAction.MOSS,
                JourneyAction.ECOSYSTEM, JourneyAction.COGNITION, JourneyAction.VILLAGER, JourneyAction.ARCHIVE);
        machine.setItem(0, new ItemStack(JourneyContent.CORES.get(JourneyAction.RAIN).get()));
        // Roof and blocked outputs must not consume the core or emit evidence.
        level.setBlock(pos.above(2), Blocks.STONE.defaultBlockState(), 3);
        machine.setItem(2, new ItemStack(Items.DIRT, 64)); machine.setItem(3, new ItemStack(Items.DIRT, 64));
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(machine.getItem(0).isEmpty(), "Roof preserves core");
            helper.assertFalse(teams.has(team, JourneyMilestone.RAIN.id()), "Roof emits no completion");
            level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(machine.getItem(0).isEmpty(), "Blocked output preserves core");
            machine.setItem(2, ItemStack.EMPTY); machine.setItem(3, ItemStack.EMPTY);
        });
        helper.onEachTick(() -> {
            int index = step.get();
            if (index >= sequence.size()) return;
            JourneyAction action = sequence.get(index);
            if (!teams.has(team, action.milestone().id())) return;
            if (action == JourneyAction.RAIN && !level.isRaining()) return; // Weather updates on the next level tick.
            helper.assertTrue(machine.getItem(0).isEmpty(), "Committed operation consumes one core");
            helper.assertFalse(machine.getItem(2).isEmpty(), "Committed operation has actual products");
            if (action == JourneyAction.RAIN) helper.assertTrue(level.isRaining(), "Real team weather");
            if (action == JourneyAction.SNOW) helper.assertTrue(level.getBiome(new BlockPos(20, 100, 8)).is(Biomes.SNOWY_PLAINS), "Actual cold biome chunk");
            if (action == JourneyAction.MOSS) helper.assertTrue(level.getBlockState(pos.south(2).below()).is(Blocks.MOSS_BLOCK), "Actual moss in world");
            if (action == JourneyAction.ECOSYSTEM) {
                helper.assertValueEqual(level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, new AABB(pos).inflate(25)).stream()
                        .filter(e -> e.getPersistentData().hasUUID("SkyCoreCreatedTeam") && e.getPersistentData().getUUID("SkyCoreCreatedTeam").equals(team)).count(), 6L, "Six real owned animals");
            }
            if (action == JourneyAction.VILLAGER) {
                var villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(25));
                var villager = villagers.stream().filter(v -> v.getPersistentData().hasUUID("SkyCoreCreatedTeam") && v.getPersistentData().getUUID("SkyCoreCreatedTeam").equals(team)).findFirst().orElseThrow();
                villager.setNoAi(true);
                scheduleTrades(helper, player, villager, machine);
            }
            machine.setItem(2, ItemStack.EMPTY); machine.setItem(3, ItemStack.EMPTY);
            step.incrementAndGet();
            if (step.get() == sequence.size()) {
                helper.assertValueEqual(teams.unlockedChapter(team), 6, "All six chapters unlocked");
                helper.assertValueEqual(JourneyWorlds.enter(player, "end"), 1, "Actual team End now enterable");
                JourneyWorlds.enter(player, "sky");
                // Leave one genuine pending operation and a cold chunk for a disk restart check.
                machine.setItem(0, new ItemStack(JourneyContent.CORES.get(JourneyAction.SNOW).get()));
                CompoundTag marker = new CompoundTag(); marker.putUUID("Team", team); marker.putInt("Slot", state.slot);
                marker.putLong("Restorer", pos.asLong()); marker.putBoolean("Complete", true);
                player.server.getCommandStorage().set(ResourceLocation.parse("skycore:journey_qa"), marker);
                helper.succeed();
            } else if (action != JourneyAction.VILLAGER) machine.setItem(0, new ItemStack(JourneyContent.CORES.get(sequence.get(step.get())).get()));
        });
    }

    private static void scheduleTrades(GameTestHelper helper, ServerPlayer player, Villager villager, WorldRestorerBlockEntity machine) {
        var professions = List.of(VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.CLERIC,
                VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH);
        var milestones = List.of(JourneyMilestone.FARMER, JourneyMilestone.LIBRARIAN, JourneyMilestone.CLERIC,
                JourneyMilestone.ARMORER, JourneyMilestone.TOOLSMITH, JourneyMilestone.WEAPONSMITH);
        // Professions are set by the fixture; native merchant payment/result processing is exercised.
        for (int index = 0; index < professions.size(); index++) {
            int i = index;
            helper.runAfterDelay(1 + i * 35, () -> {
                player.closeContainer();
                villager.setVillagerData(villager.getVillagerData().setProfession(professions.get(i)));
            });
            helper.runAfterDelay(25 + i * 35, () -> {
                villager.setTradingPlayer(player);
                villager.openTradingScreen(player, villager.getDisplayName(), 1);
                helper.assertTrue(player.containerMenu instanceof MerchantMenu, "Real merchant menu opens");
                var menu = (MerchantMenu) player.containerMenu;
                var offer = villager.getOffers().getFirst();
                menu.setSelectionHint(0);
                menu.getSlot(0).set(new ItemStack(Items.EMERALD, offer.getCostA().getCount()));
                menu.slotsChanged(menu.getSlot(0).container);
                ItemStack bought = menu.quickMoveStack(player, 2);
                helper.assertFalse(bought.isEmpty(), "Player actually takes a paid trade result");
                helper.assertValueEqual(offer.getUses(), 1, "Merchant consumes a real offer use");
                helper.assertTrue(TeamProgressData.get(player.server).has(EntropyTests.team(player), milestones.get(i).id()), "Native trade event grants correct team record");
                player.closeContainer();
                if (i == professions.size() - 1) machine.setItem(0, new ItemStack(JourneyContent.CORES.get(JourneyAction.ARCHIVE).get()));
            });
        }
    }
}
