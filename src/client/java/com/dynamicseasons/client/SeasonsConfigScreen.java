package com.dynamicseasons.client;

import com.dynamicseasons.SeasonManager;
import com.dynamicseasons.SeasonsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

public class SeasonsConfigScreen extends Screen {
    private static final int FULL_WIDTH = 310;
    private static final int HALF_WIDTH = 150;
    private static final int ROW = 24;

    @Nullable
    private final Screen parent;
    private final SeasonsConfig working;
    /** Gameplay settings belong to the server; only editable in singleplayer or the main menu. */
    private boolean canEditGameplay;

    public SeasonsConfigScreen(@Nullable Screen parent) {
        super(Text.translatable("config.dynamic_seasons.title"));
        this.parent = parent;
        this.working = SeasonsConfig.get().copy();
    }

    @Override
    protected void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        canEditGameplay = client.world == null || client.isIntegratedServerRunning();

        int left = this.width / 2 - FULL_WIDTH / 2;
        int right = left + FULL_WIDTH - HALF_WIDTH;
        int y = Math.max(40, this.height / 2 - (ROW * 6) / 2);

        addDrawableChild(new IntSlider(left, y, FULL_WIDTH, "config.dynamic_seasons.season_length",
                SeasonsConfig.MIN_SEASON_LENGTH, SeasonsConfig.MAX_SEASON_LENGTH, working.seasonLengthDays,
                v -> working.seasonLengthDays = v)).active = canEditGameplay;
        y += ROW;

        addDrawableChild(new IntSlider(left, y, HALF_WIDTH, "config.dynamic_seasons.snow_layers",
                1, 8, working.maxSnowLayers, v -> working.maxSnowLayers = v)).active = canEditGameplay;
        addDrawableChild(toggle(right, y, "config.dynamic_seasons.weather", working.seasonalWeather,
                v -> working.seasonalWeather = v, true));
        y += ROW;

        addDrawableChild(toggle(left, y, "config.dynamic_seasons.freeze_water", working.freezeWater,
                v -> working.freezeWater = v, true));
        addDrawableChild(toggle(right, y, "config.dynamic_seasons.wither_plants", working.witherPlants,
                v -> working.witherPlants = v, true));
        y += ROW;

        addDrawableChild(toggle(left, y, "config.dynamic_seasons.crop_growth", working.seasonalCropGrowth,
                v -> working.seasonalCropGrowth = v, true));
        addDrawableChild(toggle(right, y, "config.dynamic_seasons.announce", working.announceSeasons,
                v -> working.announceSeasons = v, true));
        y += ROW;

        addDrawableChild(toggle(left, y, "config.dynamic_seasons.colors", working.seasonalColors,
                v -> working.seasonalColors = v, false));
        addDrawableChild(toggle(right, y, "config.dynamic_seasons.hud", working.showHud,
                v -> working.showHud = v, false));
        y += ROW + 8;

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> saveAndClose())
                .dimensions(left, y, HALF_WIDTH, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
                .dimensions(right, y, HALF_WIDTH, 20).build());
    }

    private CyclingButtonWidget<Boolean> toggle(int x, int y, String key, boolean value,
                                                java.util.function.Consumer<Boolean> setter, boolean gameplay) {
        CyclingButtonWidget<Boolean> button = CyclingButtonWidget.onOffBuilder(value)
                .build(x, y, HALF_WIDTH, 20, Text.translatable(key), (b, v) -> setter.accept(v));
        button.active = !gameplay || canEditGameplay;
        return button;
    }

    private void saveAndClose() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (canEditGameplay) {
            SeasonsConfig.replace(working);
        } else {
            // Connected to a server: only the visual options are ours to change.
            SeasonsConfig updated = SeasonsConfig.get().copy();
            updated.seasonalColors = working.seasonalColors;
            updated.showHud = working.showHud;
            SeasonsConfig.replace(updated);
        }
        IntegratedServer server = client.getServer();
        if (server != null) {
            server.execute(() -> SeasonManager.onConfigChanged(server));
        }
        DynamicSeasonsClient.onConfigChanged();
        close();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        Text note = canEditGameplay
                ? Text.translatable("config.dynamic_seasons.note").formatted(Formatting.GRAY)
                : Text.translatable("config.dynamic_seasons.server_note").formatted(Formatting.YELLOW);
        context.drawCenteredTextWithShadow(textRenderer, note, width / 2, 28, 0xFFFFFF);
    }

    private static final class IntSlider extends SliderWidget {
        private final String key;
        private final int min;
        private final int max;
        private final IntConsumer setter;

        IntSlider(int x, int y, int width, String key, int min, int max, int value, IntConsumer setter) {
            super(x, y, width, 20, Text.empty(), (value - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int intValue() {
            return MathHelper.clamp((int) Math.round(min + value * (max - min)), min, max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(key, intValue()));
        }

        @Override
        protected void applyValue() {
            setter.accept(intValue());
        }
    }
}
