package com.dynamicseasons;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/** Per-world season progress, saved with the overworld. */
public class SeasonData extends PersistentState {
    public static final PersistentState.Type<SeasonData> TYPE =
            new PersistentState.Type<>(SeasonData::new, SeasonData::fromNbt, null);

    /** Ticks of in-game time that have passed since the mod started tracking this world. */
    public long elapsedTicks = 0L;
    /** The season length {@link #elapsedTicks} was last measured with (0 = unknown). */
    public int seasonLengthDays = 0;

    public static SeasonData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SeasonData data = new SeasonData();
        data.elapsedTicks = Math.max(0L, nbt.getLong("ElapsedTicks"));
        data.seasonLengthDays = nbt.getInt("SeasonLengthDays");
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putLong("ElapsedTicks", elapsedTicks);
        nbt.putInt("SeasonLengthDays", seasonLengthDays);
        return nbt;
    }
}
