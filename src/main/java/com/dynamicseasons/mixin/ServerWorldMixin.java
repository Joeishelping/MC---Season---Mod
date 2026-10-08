package com.dynamicseasons.mixin;

import com.dynamicseasons.ModBlocks;
import com.dynamicseasons.SeasonClock;
import com.dynamicseasons.SeasonalWorldTicker;
import com.dynamicseasons.SeasonsConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    /** Seasonal snow, ice and withering piggyback on vanilla's random ice/snow column tick. */
    @Inject(method = "tickIceAndSnow", at = @At("TAIL"))
    private void dynamicSeasons$tickSeasonalColumn(BlockPos pos, CallbackInfo ci) {
        SeasonalWorldTicker.tickColumn((ServerWorld) (Object) this, pos);
    }

    /** Scale natural rain / dry-spell / thunder durations by season. /weather is untouched. */
    @WrapOperation(method = "tickWeather", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/intprovider/IntProvider;get(Lnet/minecraft/util/math/random/Random;)I"))
    private int dynamicSeasons$seasonalWeather(IntProvider provider, Random random, Operation<Integer> original) {
        int value = original.call(provider, random);
        ServerWorld self = (ServerWorld) (Object) this;
        if (!SeasonsConfig.get().seasonalWeather || self.getRegistryKey() != World.OVERWORLD) {
            return value;
        }
        float multiplier;
        if (provider == ServerWorld.RAIN_WEATHER_DURATION_PROVIDER) {
            multiplier = SeasonClock.rainDurationMultiplier();
        } else if (provider == ServerWorld.CLEAR_WEATHER_DURATION_PROVIDER) {
            multiplier = SeasonClock.clearDurationMultiplier();
        } else if (provider == ServerWorld.THUNDER_WEATHER_DURATION_PROVIDER) {
            multiplier = 1.0f;
        } else {
            multiplier = SeasonClock.thunderDelayMultiplier(); // the private clear-thunder provider
        }
        return Math.max(1, Math.round(value * multiplier));
    }

    /** Crops and saplings under open sky grow faster in summer and slower in the cold. */
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V"))
    private void dynamicSeasons$seasonalGrowth(BlockState state, ServerWorld world, BlockPos pos, Random random, Operation<Void> original) {
        if (!SeasonsConfig.get().seasonalCropGrowth || !state.isIn(ModBlocks.SEASONAL_GROWTH)
                || world.getRegistryKey() != World.OVERWORLD || !world.isSkyVisible(pos.up())) {
            original.call(state, world, pos, random);
            return;
        }
        float multiplier = SeasonClock.growthMultiplier();
        if (multiplier < 1.0f) {
            if (random.nextFloat() < multiplier) {
                original.call(state, world, pos, random);
            }
            return;
        }
        original.call(state, world, pos, random);
        if (random.nextFloat() < multiplier - 1.0f) {
            BlockState current = world.getBlockState(pos);
            if (current.isIn(ModBlocks.SEASONAL_GROWTH) && current.hasRandomTicks()) {
                original.call(current, world, pos, random);
            }
        }
    }
}
