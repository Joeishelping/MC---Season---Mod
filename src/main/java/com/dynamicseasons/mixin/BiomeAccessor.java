package com.dynamicseasons.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface BiomeAccessor {
    /** Vanilla's (cached) position temperature, before any seasonal change. */
    @Invoker("getTemperature")
    float dynamicSeasons$getTemperature(BlockPos pos);
}
