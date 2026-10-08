package com.dynamicseasons;

import com.dynamicseasons.block.SeasonalSnowBlock;
import com.dynamicseasons.block.WitheredPlantBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Runs alongside vanilla's ice-and-snow tick for one random column of a loaded chunk.
 * Vanilla keeps handling naturally cold places; this only handles places that are cold
 * because of the season, and only ever places the mod's own reversible blocks there.
 */
public final class SeasonalWorldTicker {
    private SeasonalWorldTicker() {
    }

    public static void tickColumn(ServerWorld world, BlockPos randomPos) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        SeasonsConfig config = SeasonsConfig.get();
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, randomPos);
        BlockPos below = top.down();
        Biome biome = world.getBiome(top).value();

        if (config.freezeWater && SeasonRules.isSeasonallyFrozen(biome, below) && canFreeze(world, below)) {
            world.setBlockState(below, ModBlocks.SEASONAL_ICE.getDefaultState(), Block.NOTIFY_ALL);
        }

        if (world.isRaining() && SeasonRules.isSeasonallyFrozen(biome, top)) {
            accumulateSnow(world, top, config.maxSnowLayers);
        }

        if (SeasonRules.shouldWither(biome, top)) {
            wither(world, top);
        }
    }

    /** Same as vanilla's ice check minus the temperature test (the caller handles that). */
    private static boolean canFreeze(ServerWorld world, BlockPos pos) {
        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()
                || world.getLightLevel(LightType.BLOCK, pos) >= 10) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (world.getFluidState(pos).getFluid() != Fluids.WATER || !(state.getBlock() instanceof FluidBlock)) {
            return false;
        }
        // Like vanilla: freeze from the edges inwards, never the middle of open water first.
        return !(world.isWater(pos.west()) && world.isWater(pos.east())
                && world.isWater(pos.north()) && world.isWater(pos.south()));
    }

    private static void accumulateSnow(ServerWorld world, BlockPos pos, int maxLayers) {
        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()
                || world.getLightLevel(LightType.BLOCK, pos) >= 10) {
            return;
        }
        BlockState state = world.getBlockState(pos);
        if (state.isOf(ModBlocks.SEASONAL_SNOW)) {
            int layers = state.get(SeasonalSnowBlock.LAYERS);
            if (layers < maxLayers) {
                BlockState thicker = state.with(SeasonalSnowBlock.LAYERS, layers + 1);
                Block.pushEntitiesUpBeforeBlockChange(state, thicker, world, pos);
                world.setBlockState(pos, thicker, Block.NOTIFY_ALL);
            }
        } else if (state.isAir() && Blocks.SNOW.getDefaultState().canPlaceAt(world, pos)) {
            world.setBlockState(pos, ModBlocks.SEASONAL_SNOW.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private static void wither(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isIn(ModBlocks.WITHERS) || state.hasBlockEntity()) {
            return;
        }
        world.setBlockState(pos, ModBlocks.WITHERED_PLANT.getDefaultState(), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(pos) instanceof WitheredPlantBlockEntity entity) {
            entity.setOriginal(state);
        } else {
            // Could not remember what it was - put it straight back rather than lose it.
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        }
    }
}
