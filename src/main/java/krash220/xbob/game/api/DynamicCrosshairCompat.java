package krash220.xbob.game.api;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Compatibility layer for DynamicCrosshair (mod.crend.dynamiccrosshair).
 *
 * <p>DynamicCrosshair replaces the vanilla crosshair sprite, but on 26.1 it still draws through the
 * vanilla {@code GuiGraphicsExtractor.blitSprite} call inside {@code Gui.extractCrosshair}. Because
 * {@code GuiGraphicsExtractor} snapshots the 2D pose for every blit, DynamicCrosshair simply inherits
 * the pose that this mod pushes, so the bobbing animation applies to its custom crosshair styles as
 * well.</p>
 *
 * <p>The only thing that could fight the transform is DynamicCrosshair's optional
 * "fix centered crosshair" feature, which resets the pose translation to an absolute value
 * ({@code pose.setTranslation(dx, dy)}), discarding this mod's translation/rotation/scale.
 * While this mod is transforming the crosshair, that reset is replaced by an offset relative to the
 * current pose (see {@link #applyRelativeCenter}), so both features stay aligned.</p>
 */
public class DynamicCrosshairCompat {

    private static Boolean present;
    private static boolean transforming;
    private static final java.util.concurrent.atomic.AtomicInteger FIX_HOOK_HITS = new java.util.concurrent.atomic.AtomicInteger();

    public static final int VANILLA_CROSSHAIR_SIZE = 15;

    public static boolean isPresent() {
        if (present == null) {
            present = Boolean.valueOf(FabricLoader.getInstance().isModLoaded("dynamiccrosshair"));
        }

        return present.booleanValue();
    }

    public static boolean isTransforming() {
        return transforming;
    }

    public static void setTransforming(boolean value) {
        transforming = value;
    }

    /** Called when DynamicCrosshair's "fix centered crosshair" reset was replaced by a relative offset. */
    public static void onFixHook() {
        FIX_HOOK_HITS.incrementAndGet();
    }

    public static int getFixHookHits() {
        return FIX_HOOK_HITS.get();
    }

    /**
     * Equivalent of {@code CrosshairRenderer.fixCenteredCrosshairPre}, except that the offset is
     * applied on top of the pose this mod pushed instead of replacing its translation.
     */
    public static void applyRelativeCenter(GuiGraphicsExtractor graphics, int x, int y) {
        float dx = (graphics.guiWidth() - VANILLA_CROSSHAIR_SIZE) / 2.0f - x;
        float dy = (graphics.guiHeight() - VANILLA_CROSSHAIR_SIZE) / 2.0f - y;

        graphics.pose().pushMatrix();
        graphics.pose().translate(dx, dy);
    }
}
