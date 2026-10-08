package com.dynamicseasons.mixin;

import com.dynamicseasons.SeasonRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only the precipitation TYPE (rain vs snow) is seasonal. Biome temperature itself is left alone,
 * so world generation, natural snow, snow golems, etc. behave exactly like vanilla - new chunks
 * generated in winter are identical to ones generated in summer.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void dynamicSeasons$seasonalPrecipitation(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        Biome.Precipitation seasonal = SeasonRules.precipitation((Biome) (Object) this, pos);
        if (seasonal != null) {
            cir.setReturnValue(seasonal);
        }
    }
}
