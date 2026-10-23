package krash220.xbob.game.api.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class GuiBus {

    public static float partialTicks;

    private static final List<BiConsumer<GuiGraphicsExtractor, Float>> PRE = new ArrayList<>();
    private static final List<BiConsumer<GuiGraphicsExtractor, Float>> POST = new ArrayList<>();

    public static void registerRenderCrosshair(BiConsumer<GuiGraphicsExtractor, Float> pre, BiConsumer<GuiGraphicsExtractor, Float> post) {
        PRE.add(pre);
        POST.add(post);
    }

    public static void pre(GuiGraphicsExtractor graphics) {
        for (BiConsumer<GuiGraphicsExtractor, Float> handler : PRE) {
            handler.accept(graphics, partialTicks);
        }
    }

    public static void post(GuiGraphicsExtractor graphics) {
        for (BiConsumer<GuiGraphicsExtractor, Float> handler : POST) {
            handler.accept(graphics, partialTicks);
        }
    }
}
