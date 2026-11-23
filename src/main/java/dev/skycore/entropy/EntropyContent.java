package dev.skycore.entropy;

import dev.skycore.SkyCore;
import dev.skycore.block.OwnedBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared registry entries and server lifecycle wiring. Natural entropy remains opt-in. */
public final class EntropyContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyCore.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyCore.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkyCore.ID);

    public static final DeferredBlock<FieldMachineBlock> ANTI_ENTROPY_FIELD = BLOCKS.register("mechanical_anti_entropy_field",
            () -> new FieldMachineBlock(true, machineProperties(-1)));
    public static final DeferredBlock<FieldMachineBlock> ENTROPY_FIELD = BLOCKS.register("mechanical_entropy_field",
            () -> new FieldMachineBlock(false, machineProperties(2)));
    public static final DeferredBlock<Block> ANTI_ENTROPY_STONE = BLOCKS.register("anti_entropy_stone",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.5F, 3_600_000)));
    public static final DeferredItem<Item> WORLD_FUEL = ITEMS.registerSimpleItem("world_fuel");
    static {
        ITEMS.register("mechanical_anti_entropy_field", () -> new OwnedBlockItem(ANTI_ENTROPY_FIELD.get(), new Item.Properties()));
        ITEMS.register("mechanical_entropy_field", () -> new OwnedBlockItem(ENTROPY_FIELD.get(), new Item.Properties()));
        ITEMS.registerSimpleBlockItem(ANTI_ENTROPY_STONE);
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FieldMachineBlockEntity>> ANTI_ENTROPY_ENTITY = ENTITIES.register("mechanical_anti_entropy_field",
            () -> BlockEntityType.Builder.of((pos, state) -> new FieldMachineBlockEntity(true, pos, state), ANTI_ENTROPY_FIELD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FieldMachineBlockEntity>> ENTROPY_ENTITY = ENTITIES.register("mechanical_entropy_field",
            () -> BlockEntityType.Builder.of((pos, state) -> new FieldMachineBlockEntity(false, pos, state), ENTROPY_FIELD.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        NeoForge.EVENT_BUS.addListener(EntropyCommands::register);
        EntropyEvents.register();
    }

    private static BlockBehaviour.Properties machineProperties(float hardness) {
        return BlockBehaviour.Properties.of().strength(hardness, 3_600_000).pushReaction(PushReaction.BLOCK);
    }

    private EntropyContent() {}
}
