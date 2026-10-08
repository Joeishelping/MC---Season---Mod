package com.dynamicseasons.client.mixin;

import com.dynamicseasons.SeasonRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla skips drawing weather in biomes without precipitation. Hot dry biomes can get snow
 * on high peaks in winter, so let the (seasonal) precipitation type decide instead.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @WrapOperation(method = "renderWeather", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/biome/Biome;hasPrecipitation()Z"))
    private boolean dynamicSeasons$highAltitudeSnow(Biome biome, Operation<Boolean> original) {
        return original.call(biome) || SeasonRules.mayPrecipitate(biome);
    }
}
