package com.dynamicseasons.client;

import com.dynamicseasons.ModBlocks;
import com.dynamicseasons.Season;
import com.dynamicseasons.SeasonClock;
import com.dynamicseasons.SeasonsConfig;
import com.dynamicseasons.network.SeasonSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class DynamicSeasonsClient implements ClientModInitializer {
    private static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.WITHERED_PLANT, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SEASONAL_ICE, RenderLayer.getTranslucent());

        ClientPlayNetworking.registerGlobalReceiver(SeasonSyncPayload.ID, (payload, context) -> {
            SeasonClock.setDrySnowLineY(payload.drySnowLineY());
            // In singleplayer the integrated server already drives the clock directly.
            if (!context.client().isIntegratedServerRunning()) {
                SeasonClock.set(payload.elapsedTicks(), payload.seasonLengthDays());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SeasonColors.reset());

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dynamic_seasons.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.dynamic_seasons"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("seasonconfig").executes(ctx -> {
                    MinecraftClient client = ctx.getSource().getClient();
                    // Defer so the chat screen closing does not immediately close ours.
                    client.send(() -> client.setScreen(new SeasonsConfigScreen(null)));
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(DynamicSeasonsClient::tick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!SeasonsConfig.get().showHud || client.world == null || client.options.hudHidden
                    || client.getDebugHud().shouldShowDebugHud()) {
                return;
            }
            Season season = SeasonClock.season();
            Text text = Text.translatable("hud.dynamic_seasons.season",
                    Text.translatable(season.translationKey()).formatted(season.color()),
                    SeasonClock.dayOfSeason(), SeasonClock.seasonLengthDays());
            context.drawTextWithShadow(client.textRenderer, text, 4, 4, 0xFFFFFF);
        });
    }

    private static void tick(MinecraftClient client) {
        while (openConfigKey.wasPressed()) {
            client.setScreen(new SeasonsConfigScreen(client.currentScreen));
        }
        if (client.world != null && SeasonColors.refresh()) {
            // Grass/leaf colors are baked into chunk meshes; rebuild them to show the new tint.
            client.worldRenderer.reload();
        }
    }

    static void onConfigChanged() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && SeasonColors.refresh()) {
            client.worldRenderer.reload();
        }
    }
}
