package com.dynamicseasons;

import com.dynamicseasons.block.SeasonalIceBlock;
import com.dynamicseasons.block.SeasonalSnowBlock;
import com.dynamicseasons.block.WitheredPlantBlock;
import com.dynamicseasons.block.WitheredPlantBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block SEASONAL_SNOW = register("seasonal_snow",
            new SeasonalSnowBlock(AbstractBlock.Settings.copy(Blocks.SNOW)));

    public static final Block SEASONAL_ICE = register("seasonal_ice",
            new SeasonalIceBlock(AbstractBlock.Settings.copy(Blocks.ICE)));

    public static final Block WITHERED_PLANT = register("withered_plant",
            new WitheredPlantBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.OAK_TAN)
                    .noCollision()
                    .breakInstantly()
                    .ticksRandomly()
                    .sounds(BlockSoundGroup.GRASS)
                    .pistonBehavior(PistonBehavior.DESTROY)));

    public static final BlockEntityType<WitheredPlantBlockEntity> WITHERED_PLANT_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            DynamicSeasons.id("withered_plant"),
            BlockEntityType.Builder.create(WitheredPlantBlockEntity::new, WITHERED_PLANT).build(null));

    /** Plants that wither in the cold. Data-driven so packs can add modded flowers. */
    public static final TagKey<Block> WITHERS = TagKey.of(RegistryKeys.BLOCK, DynamicSeasons.id("withers_in_cold"));
    /** Plants whose growth speed follows the seasons. */
    public static final TagKey<Block> SEASONAL_GROWTH = TagKey.of(RegistryKeys.BLOCK, DynamicSeasons.id("seasonal_growth"));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, DynamicSeasons.id(name), block);
    }

    public static void init() {
        // Class loading registers everything above.
    }
}
