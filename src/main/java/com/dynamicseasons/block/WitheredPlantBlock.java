package com.dynamicseasons.block;

import com.dynamicseasons.SeasonRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A grass tuft or flower that died off in the cold. It stores the exact original block state and
 * puts it back when the weather warms up. Until then it acts like the original plant would:
 * it drops the original plant's loot, needs the same ground to survive, can be replaced by
 * placing blocks only when the original could, and middle-click picks the original.
 */
public class WitheredPlantBlock extends Block implements BlockEntityProvider {
    public static final MapCodec<WitheredPlantBlock> CODEC = createCodec(WitheredPlantBlock::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public WitheredPlantBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WitheredPlantBlockEntity(pos, state);
    }

    @Nullable
    private static BlockState original(@Nullable BlockView world, BlockPos pos) {
        if (world != null && world.getBlockEntity(pos) instanceof WitheredPlantBlockEntity entity) {
            BlockState original = entity.getOriginal();
            return original.isAir() ? null : original;
        }
        return null;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockState original = original(world, pos);
        if (original != null) {
            return original.canPlaceAt(world, pos);
        }
        BlockState floor = world.getBlockState(pos.down());
        return floor.isIn(BlockTags.DIRT) || floor.isOf(Blocks.FARMLAND);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !this.canPlaceAt(state, world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (SeasonRules.shouldRevive(world.getBiome(pos).value(), pos)) {
            revive(world, pos);
        }
    }

    /** Puts the original plant back. Safe to call at any time. */
    public static void revive(ServerWorld world, BlockPos pos) {
        BlockState original = original(world, pos);
        if (original == null) {
            // Nothing to restore (should never happen) - just clear the dead plant.
            world.removeBlock(pos, false);
            return;
        }
        world.setBlockState(pos, original, Block.NOTIFY_ALL);
        if (!original.canPlaceAt(world, pos)) {
            // Ground changed while it was withered: pop it off like vanilla would, with its drops.
            world.breakBlock(pos, true);
        }
    }

    @Override
    protected boolean canReplace(BlockState state, ItemPlacementContext context) {
        BlockState original = original(context.getWorld(), context.getBlockPos());
        return original != null && original.canReplace(context);
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        BlockEntity entity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (entity instanceof WitheredPlantBlockEntity withered && !withered.getOriginal().isAir()) {
            return withered.getOriginal().getDroppedStacks(builder);
        }
        return Collections.emptyList();
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        BlockState original = original(world, pos);
        return original != null ? original.getBlock().getPickStack(world, pos, original) : ItemStack.EMPTY;
    }
}
