package com.dynamicseasons.block;

import com.dynamicseasons.SeasonRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;

import java.util.List;

/**
 * Snow that fell only because of the season. It looks, behaves and drops exactly like vanilla
 * snow, but because it is its own block the mod knows it is safe to melt it - natural snow and
 * snow placed by players is never touched.
 */
public class SeasonalSnowBlock extends SnowBlock {
    public static final MapCodec<SeasonalSnowBlock> CODEC = createCodec(SeasonalSnowBlock::new);

    public SeasonalSnowBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<SnowBlock> getCodec() {
        return (MapCodec) CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        super.randomTick(state, world, pos, random); // vanilla: melts next to bright light
        BlockState current = world.getBlockState(pos);
        if (!current.isOf(this)) {
            return;
        }
        if (SeasonRules.shouldThaw(world.getBiome(pos).value(), pos)) {
            int layers = current.get(LAYERS);
            if (layers > 1) {
                world.setBlockState(pos, current.with(LAYERS, layers - 1), Block.NOTIFY_ALL);
            } else {
                world.removeBlock(pos, false);
            }
        }
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        // Same loot as vanilla snow with the same number of layers.
        return Blocks.SNOW.getDefaultState().with(LAYERS, state.get(LAYERS)).getDroppedStacks(builder);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        return new ItemStack(Items.SNOW);
    }
}
