package dev.skycore.journey;

import dev.skycore.SkyCore;
import dev.skycore.block.OwnedBlockItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class JourneyContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyCore.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyCore.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkyCore.ID);
    public static final DeferredBlock<WorldRestorerBlock> RESTORER = BLOCKS.register("world_restorer", () -> new WorldRestorerBlock(
            BlockBehaviour.Properties.of().strength(3, 3_600_000).pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorldRestorerBlockEntity>> RESTORER_ENTITY = ENTITIES.register("world_restorer",
            () -> BlockEntityType.Builder.of(WorldRestorerBlockEntity::new, RESTORER.get()).build(null));
    public static final DeferredItem<FlightGemItem> FLIGHT_GEM = ITEMS.register("flight_gem", () -> new FlightGemItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SUBSTRATE = ITEMS.registerSimpleItem("cognitive_substrate");
    public static final Map<JourneyAction, DeferredItem<Item>> CORES = new EnumMap<>(JourneyAction.class);
    static {
        ITEMS.register("world_restorer", () -> new OwnedBlockItem(RESTORER.get(), new Item.Properties()));
        for (JourneyAction action : JourneyAction.values()) CORES.put(action, ITEMS.registerSimpleItem(action.item()));
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); JourneyEvents.register(bus); }
    private JourneyContent() {}
}
