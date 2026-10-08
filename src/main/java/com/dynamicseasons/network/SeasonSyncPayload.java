package com.dynamicseasons.network;

import com.dynamicseasons.DynamicSeasons;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: current season clock. */
public record SeasonSyncPayload(long elapsedTicks, int seasonLengthDays, int drySnowLineY) implements CustomPayload {
    public static final CustomPayload.Id<SeasonSyncPayload> ID = new CustomPayload.Id<>(DynamicSeasons.id("season_sync"));
    public static final PacketCodec<RegistryByteBuf, SeasonSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, SeasonSyncPayload::elapsedTicks,
            PacketCodecs.VAR_INT, SeasonSyncPayload::seasonLengthDays,
            PacketCodecs.VAR_INT, SeasonSyncPayload::drySnowLineY,
            SeasonSyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
