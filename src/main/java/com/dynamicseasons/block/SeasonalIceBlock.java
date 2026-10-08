package com.dynamicseasons.block;

import com.dynamicseasons.SeasonRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.IceBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ice that formed on a water source only because of the season. Always turns back into a water
 * source - when it thaws, and when it is broken - so lakes, rivers and farms come back intact.
 */
public class SeasonalIceBlock extends IceBlock {
    public static final MapCodec<SeasonalIceBlock> CODEC = createCodec(SeasonalIceBlock::new);

    public SeasonalIceBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends IceBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        super.randomTick(state, world, pos, random); // vanilla: melts next to bright light
        BlockState current = world.getBlockState(pos);
        if (current.isOf(this) && SeasonRules.shouldThaw(world.getBiome(pos).value(), pos)) {
            this.melt(current, world, pos);
        }
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.afterBreak(world, player, pos, state, blockEntity, tool);
        // Vanilla ice only leaves water when something solid is below it. Seasonal ice was always
        // a water source, so always give the water back (unless silk-touched, like vanilla).
        if (!EnchantmentHelper.hasAnyEnchantmentsIn(tool, EnchantmentTags.PREVENTS_ICE_MELTING)
                && !world.getDimension().ultrawarm()
                && world.getBlockState(pos).isAir()) {
            world.setBlockState(pos, getMeltedState());
        }
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return Blocks.ICE.getDefaultState().getDroppedStacks(builder);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        return new ItemStack(Items.ICE);
    }
}
