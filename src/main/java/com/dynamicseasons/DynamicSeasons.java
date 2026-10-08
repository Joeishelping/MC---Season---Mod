package com.dynamicseasons;

import com.dynamicseasons.command.SeasonCommand;
import com.dynamicseasons.network.SeasonSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicSeasons implements ModInitializer {
    public static final String MOD_ID = "dynamic_seasons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        SeasonsConfig.load();
        ModBlocks.init();

        PayloadTypeRegistry.playS2C().register(SeasonSyncPayload.ID, SeasonSyncPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(SeasonManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(SeasonManager::onServerStopped);
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                SeasonManager.onOverworldTick(world);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SeasonManager.sync(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SeasonCommand.register(dispatcher));
    }
}
