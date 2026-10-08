package com.dynamicseasons.mixin;

import com.dynamicseasons.ModBlocks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Frozen irrigation water still counts as water, so farmland next to a frozen pond stays
 * hydrated over winter and is never turned back into dirt by the season.
 */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @WrapOperation(method = "isWaterNearby", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/WorldView;getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;"))
    private static FluidState dynamicSeasons$frozenWaterCounts(WorldView world, BlockPos pos, Operation<FluidState> original) {
        FluidState fluid = original.call(world, pos);
        if (fluid.isEmpty() && world.getBlockState(pos).isOf(ModBlocks.SEASONAL_ICE)) {
            return Fluids.WATER.getStill(false);
        }
        return fluid;
    }
}
