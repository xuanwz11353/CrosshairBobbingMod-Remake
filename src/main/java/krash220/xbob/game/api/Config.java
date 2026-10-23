package krash220.xbob.game.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Boolean> DEFAULT = new LinkedHashMap<>();
    private static final Map<String, Boolean> CONFIG = new LinkedHashMap<>();

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "xbob.json");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void load() {
        File config = getConfigFile();

        if (!config.exists()) {
            save();
        }

        try (FileInputStream fis = new FileInputStream(config)) {
            Map map = GSON.fromJson(new InputStreamReader(fis, StandardCharsets.UTF_8), Map.class);

            if (map != null) {
                CONFIG.putAll(map);
            }
        } catch (IOException e) {}
    }

    public static void save() {
        String cfg = GSON.toJson(CONFIG);

        try (FileOutputStream fis = new FileOutputStream(getConfigFile())) {
            fis.write(cfg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {}
    }

    public static void define(String key, Boolean defaultValue) {
        DEFAULT.put(key, defaultValue);
        CONFIG.put(key, defaultValue);
    }

    public static void set(String key, Boolean value) {
        CONFIG.put(key, value);
    }

    public static boolean check(String key) {
        Boolean val = CONFIG.get(key);

        if (val != null) {
            return val.booleanValue();
        } else {
            return DEFAULT.get(key).booleanValue();
        }
    }

    public static void registerGui() {}

    public static class ModMenu implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
            return previous -> new ConfigScreen(previous);
        }
    }

    private static class ConfigScreen extends Screen {

        private static final Component VALUE_ENABLE = Component.translatable("xbob.config.value.enable").withStyle(ChatFormatting.GREEN);
        private static final Component VALUE_DISABLE = Component.translatable("xbob.config.value.disable").withStyle(ChatFormatting.DARK_RED);

        public static Component getValueText(String key) {
            boolean bool = CONFIG.get(key).booleanValue();

            return Component.translatable("%s: %s", Component.translatable("xbob.config." + key), bool ? VALUE_ENABLE : VALUE_DISABLE);
        }

        private final Screen previous;

        private ConfigScreen(Screen previous) {
            super(Component.translatable("xbob.config.title"));

            this.previous = previous;
        }

        @Override
        public void onClose() {
            save();

            Minecraft.getInstance().setScreen(this.previous);
        }

        @Override
        protected void init() {
            int i = this.height / 6 - 12;

            for (String key : DEFAULT.keySet()) {
                this.addRenderableWidget(Button.builder(getValueText(key), btn -> {
                    set(key, !check(key));

                    btn.setMessage(getValueText(key));
                }).bounds(this.width / 2 - 150, i, 300, 20).build());

                i += 24;
            }

            this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose()).bounds(this.width / 2 - 100, this.height - 27, 200, 20).build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // 26.1: the background (blur/panorama) is already rendered by
            // Screen.extractRenderStateWithTooltipAndSubtitles before this method, so drawing it here too
            // throws "Can only blur once per frame".
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            // 26.1 font colours are ARGB, so the alpha channel must be set explicitly.
            graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
        }
    }
}
