package com.dynamicseasons.test;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Dev-only: /seasontest tick <chunkX> <chunkZ> <radius> <times> runs vanilla's own chunk tick
 * (random ticks + ice/snow column ticks) on loaded chunks, so behaviour can be tested on a
 * dedicated server without a player standing nearby.
 */
public class SeasonTestMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
                CommandManager.literal("seasontest").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("tick")
                                .then(CommandManager.argument("cx", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("cz", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(0, 8))
                                                        .then(CommandManager.argument("times", IntegerArgumentType.integer(1, 100000))
                                                                .executes(ctx -> {
                                                                    ServerWorld world = ctx.getSource().getServer().getOverworld();
                                                                    int cx = IntegerArgumentType.getInteger(ctx, "cx");
                                                                    int cz = IntegerArgumentType.getInteger(ctx, "cz");
                                                                    int r = IntegerArgumentType.getInteger(ctx, "radius");
                                                                    int times = IntegerArgumentType.getInteger(ctx, "times");
                                                                    int speed = world.getGameRules().getInt(net.minecraft.world.GameRules.RANDOM_TICK_SPEED);
                                                                    int ticked = 0;
                                                                    for (int t = 0; t < times; t++) {
                                                                        for (int x = cx - r; x <= cx + r; x++) {
                                                                            for (int z = cz - r; z <= cz + r; z++) {
                                                                                WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
                                                                                if (chunk != null) {
                                                                                    world.tickChunk(chunk, speed);
                                                                                    ticked++;
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                    int total = ticked;
                                                                    ctx.getSource().sendFeedback(() -> Text.literal("seasontest ticked " + total), false);
                                                                    return total;
                                                                }))))))));
    }
}
