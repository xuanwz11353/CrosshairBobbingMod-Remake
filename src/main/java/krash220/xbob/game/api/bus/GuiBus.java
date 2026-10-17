package krash220.xbob.game.api.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import krash220.xbob.MainMod;
import krash220.xbob.game.api.Debug;
import krash220.xbob.game.api.ShoulderSurfingCompat;
import krash220.xbob.game.api.math.MatrixStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public class GuiBus {

    public static float partialTicks;

    private static boolean registered = false;

    private static List<BiConsumer<MatrixStack, Float>> pre = new ArrayList<>();
    private static List<BiConsumer<MatrixStack, Float>> post = new ArrayList<>();

    public static void registerRenderCrosshair(BiConsumer<MatrixStack, Float> pre, BiConsumer<MatrixStack, Float> post) {
        if (!registered) {
            NeoForge.EVENT_BUS.addListener(GuiBus::preRender);
            NeoForge.EVENT_BUS.addListener(GuiBus::postRender);
            registered = true;
        }

        GuiBus.pre.add(pre);
        GuiBus.post.add(post);
    }

    public static void preRender(RenderGuiLayerEvent.Pre event) {
        Debug.count("gui.layer.pre.total");

        ResourceLocation name = event.getName();

        if (name.equals(VanillaGuiLayers.CROSSHAIR)) {
            partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);

            Debug.count("gui.layer.crosshair.pre");
            Debug.throttled("gui.layer.crosshair", "RenderGuiLayerEvent.Pre(CROSSHAIR) fired, partialTick={}", partialTicks);

            // reads ShoulderSurfing's own crosshair state once per frame
            ShoulderSurfingCompat.probe();

            MatrixStack mat = new MatrixStack(event.getGuiGraphics().pose());

            GuiBus.doPre(mat, partialTicks);

            return;
        }

        traceLayer("pre", name);

        if (ShoulderSurfingCompat.transformPostCrosshair() && ShoulderSurfingCompat.POST_CROSSHAIR_LAYER.equals(name.toString())) {
            Debug.count("xbob.ss.postCrosshair.push");

            MainMod.preDynamicCrosshair(event.getGuiGraphics().pose());
        }
    }

    public static void postRender(RenderGuiLayerEvent.Post event) {
        Debug.count("gui.layer.post.total");

        ResourceLocation name = event.getName();

        if (name.equals(VanillaGuiLayers.CROSSHAIR)) {
            partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);

            Debug.count("gui.layer.crosshair.post");

            MatrixStack mat = new MatrixStack(event.getGuiGraphics().pose());

            GuiBus.doPost(mat, partialTicks);

            return;
        }

        traceLayer("post", name);

        if (ShoulderSurfingCompat.transformPostCrosshair() && ShoulderSurfingCompat.POST_CROSSHAIR_LAYER.equals(name.toString())) {
            Debug.count("xbob.ss.postCrosshair.pop");

            MainMod.postDynamicCrosshair(event.getGuiGraphics().pose());
        }
    }

    /**
     * Logs every GUI layer whose name looks like a crosshair/obstruction marker.
     *
     * <p>Mods that draw a crosshair of their own do it from their own GUI layer, and those layers
     * are exactly the draws that xbob never touches.</p>
     */
    private static void traceLayer(String phase, ResourceLocation name) {
        String path = name.getPath();

        if (!path.contains("crosshair") && !path.contains("obstruct")) {
            return;
        }

        String full = name.toString();

        Debug.count("gui.layer." + phase + "." + full);
        Debug.throttled("gui.layer." + phase + "." + full, "extra GUI layer observed: '{}' is rendered in phase {} and is not the vanilla CROSSHAIR layer", full, phase);
    }

    public static void doPre(MatrixStack mat, float partialTicks) {
        for (BiConsumer<MatrixStack, Float> handler : pre) {
            handler.accept(mat, partialTicks);
        }
    }

    public static void doPost(MatrixStack mat, float partialTicks) {
        for (BiConsumer<MatrixStack, Float> handler : post) {
            handler.accept(mat, partialTicks);
        }
    }
}
