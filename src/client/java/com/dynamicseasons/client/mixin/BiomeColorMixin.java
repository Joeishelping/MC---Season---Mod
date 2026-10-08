package com.dynamicseasons.client.mixin;

import com.dynamicseasons.client.SeasonColors;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeColorMixin {
    @Shadow
    public abstract float getTemperature();

    @Inject(method = "getGrassColorAt", at = @At("RETURN"), cancellable = true)
    private void dynamicSeasons$seasonalGrass(double x, double z, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(SeasonColors.grass(cir.getReturnValueI(), getTemperature()));
    }

    @Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
    private void dynamicSeasons$seasonalFoliage(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(SeasonColors.foliage(cir.getReturnValueI(), getTemperature()));
    }
}
