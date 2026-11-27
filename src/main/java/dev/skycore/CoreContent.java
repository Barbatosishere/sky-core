package dev.skycore;

import dev.skycore.block.CoreBlock;
import dev.skycore.block.OwnedBlockItem;
import dev.skycore.block.RockCoreBlockEntity;
import dev.skycore.block.SoilBlockEntity;
import dev.skycore.block.WorkbenchBlockEntity;
import dev.skycore.block.WorldActivityCollectorBlock;
import dev.skycore.block.WorldActivityCollectorBlockEntity;
import dev.skycore.block.WorldFuelSynthesizerBlock;
import dev.skycore.block.WorldFuelSynthesizerBlockEntity;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.worldfuel.WorldFuelEvents;
import dev.skycore.worldfuel.WorldFuelMaterialItem;
import dev.skycore.journey.JourneyContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import dev.skycore.menu.WorkbenchMenu;
import dev.skycore.menu.WorldFuelSynthesizerMenu;
import dev.skycore.recipe.GerminationRecipe;
import dev.skycore.recipe.StoneProcessingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CoreContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyCore.ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyCore.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkyCore.ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SkyCore.ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, SkyCore.ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, SkyCore.ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SkyCore.ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WORLD_FUEL_PROGRESS = COMPONENTS.register("world_fuel_progress",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 4_800)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> WORLD_FUEL_OWNER = COMPONENTS.register("world_fuel_owner",
            () -> DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

    private static BlockBehaviour.Properties properties(float hardness) {
        return BlockBehaviour.Properties.of().strength(hardness, 3_600_000).pushReaction(PushReaction.BLOCK);
    }
    public static final DeferredBlock<CoreBlock> ROCK_CORE = BLOCKS.register("rock_core", () -> new CoreBlock(CoreBlock.Kind.ROCK, properties(-1)));
    public static final DeferredBlock<CoreBlock> STONE_WORKBENCH = BLOCKS.register("stone_workbench", () -> new CoreBlock(CoreBlock.Kind.WORKBENCH, properties(2)));
    public static final DeferredBlock<CoreBlock> MINERAL_SOIL = BLOCKS.register("mineral_soil", () -> new CoreBlock(CoreBlock.Kind.SOIL, properties(0.6F)));
    public static final DeferredBlock<WorldActivityCollectorBlock> WORLD_ACTIVITY_COLLECTOR = BLOCKS.register("world_activity_collector", () -> new WorldActivityCollectorBlock(properties(2)));
    public static final DeferredBlock<WorldFuelSynthesizerBlock> WORLD_FUEL_SYNTHESIZER = BLOCKS.register("world_fuel_synthesizer", () -> new WorldFuelSynthesizerBlock(properties(3.5F)));
    public static final DeferredItem<Item> STONE_FRAGMENT = ITEMS.registerSimpleItem("stone_fragment");
    public static final DeferredItem<Item> MINERAL_RESIDUE = ITEMS.registerSimpleItem("mineral_residue");
    public static final DeferredItem<Item> PRIMORDIAL_FUEL = ITEMS.registerSimpleItem("primordial_fuel");
    public static final DeferredItem<Item> CONDENSATE = ITEMS.registerSimpleItem("condensate");
    public static final DeferredItem<Item> LIFE_EMBRYO = ITEMS.registerSimpleItem("life_embryo");
    public static final DeferredItem<Item> WORLD_ACTIVITY = ITEMS.register("world_activity", () -> new WorldFuelMaterialItem(new Item.Properties()));
    public static final DeferredItem<Item> TRANSMUTATION_POWDER = ITEMS.register("transmutation_powder", () -> new WorldFuelMaterialItem(new Item.Properties()));
    public static final DeferredItem<Item> ACTIVE_TRANSMUTATION_AGENT = ITEMS.register("active_transmutation_agent", () -> new WorldFuelMaterialItem(new Item.Properties()));
    public static final DeferredItem<Item> CRUDE_WORLD_FUEL = ITEMS.register("crude_world_fuel", () -> new WorldFuelMaterialItem(new Item.Properties()));
    public static final DeferredItem<Item> TRANSMUTATION_RESIDUE = ITEMS.register("transmutation_residue", () -> new WorldFuelMaterialItem(new Item.Properties()));
    static {
        ITEMS.register("rock_core", () -> new OwnedBlockItem(ROCK_CORE.get(), new Item.Properties()));
        ITEMS.register("stone_workbench", () -> new OwnedBlockItem(STONE_WORKBENCH.get(), new Item.Properties()));
        ITEMS.register("mineral_soil", () -> new OwnedBlockItem(MINERAL_SOIL.get(), new Item.Properties()));
        ITEMS.register("world_activity_collector", () -> new OwnedBlockItem(WORLD_ACTIVITY_COLLECTOR.get(), new Item.Properties()));
        ITEMS.register("world_fuel_synthesizer", () -> new OwnedBlockItem(WORLD_FUEL_SYNTHESIZER.get(), new Item.Properties()));
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RockCoreBlockEntity>> ROCK_ENTITY = ENTITIES.register("rock_core", () -> BlockEntityType.Builder.of(RockCoreBlockEntity::new, ROCK_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkbenchBlockEntity>> WORKBENCH_ENTITY = ENTITIES.register("stone_workbench", () -> BlockEntityType.Builder.of(WorkbenchBlockEntity::new, STONE_WORKBENCH.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoilBlockEntity>> SOIL_ENTITY = ENTITIES.register("mineral_soil", () -> BlockEntityType.Builder.of(SoilBlockEntity::new, MINERAL_SOIL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorldActivityCollectorBlockEntity>> WORLD_ACTIVITY_COLLECTOR_ENTITY = ENTITIES.register("world_activity_collector", () -> BlockEntityType.Builder.of(WorldActivityCollectorBlockEntity::new, WORLD_ACTIVITY_COLLECTOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorldFuelSynthesizerBlockEntity>> WORLD_FUEL_SYNTHESIZER_ENTITY = ENTITIES.register("world_fuel_synthesizer", () -> BlockEntityType.Builder.of(WorldFuelSynthesizerBlockEntity::new, WORLD_FUEL_SYNTHESIZER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> WORKBENCH_MENU = MENUS.register("stone_workbench", () -> IMenuTypeExtension.create(WorkbenchMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WorldFuelSynthesizerMenu>> WORLD_FUEL_SYNTHESIZER_MENU = MENUS.register("world_fuel_synthesizer", () -> IMenuTypeExtension.create(WorldFuelSynthesizerMenu::new));
    public static final DeferredHolder<RecipeType<?>, RecipeType<StoneProcessingRecipe>> STONE_PROCESSING = TYPES.register("stone_processing", () -> new RecipeType<>() { @Override public String toString() { return "skycore:stone_processing"; } });
    public static final DeferredHolder<RecipeType<?>, RecipeType<GerminationRecipe>> GERMINATION = TYPES.register("germination", () -> new RecipeType<>() { @Override public String toString() { return "skycore:germination"; } });
    public static final DeferredHolder<RecipeSerializer<?>, StoneProcessingRecipe.Serializer> STONE_SERIALIZER = SERIALIZERS.register("stone_processing", StoneProcessingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, GerminationRecipe.Serializer> GERMINATION_SERIALIZER = SERIALIZERS.register("germination", GerminationRecipe.Serializer::new);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); TYPES.register(bus); SERIALIZERS.register(bus);
        EntropyContent.register(bus);
        COMPONENTS.register(bus);
        WorldFuelEvents.register(bus);
        JourneyContent.register(bus);
    }
    private CoreContent() {}
}
