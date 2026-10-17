package krash220.xbob.game.api;

import net.neoforged.fml.ModList;

public class DynamicCrosshairCompat {

    /**
     * Config / system property switch that decides how xbob makes DynamicCrosshair's crosshair bob.
     *
     * <p><b>Default: layer pose (true).</b> xbob leaves DynamicCrosshair's own drawing alone and
     * transforms the whole {@code minecraft:crosshair} GUI layer pose instead. This is verified to
     * work with DynamicCrosshair 9.x + libbamboo 2.25 in a full modpack, because libbamboo's
     * {@code CustomFramebufferRenderer.draw} builds its full screen quad from
     * {@code graphics.pose().last().pose()}, i.e. from the very pose xbob transforms.</p>
     *
     * <p>With {@code false} xbob instead takes DynamicCrosshair's crosshair draw over (cancelling
     * {@code CrosshairRenderer.wrapRender}) and transforms libbamboo's framebuffer blit. That path
     * works with only xbob + DynamicCrosshair installed, but was observed to leave the crosshair
     * static inside a large modpack, so it is now opt-in.</p>
     *
     * <p>Override either way with {@code -Dxbob.dynamiccrosshair.layerPose=true|false}, which wins
     * over the config file and is what the A/B test above used.</p>
     */
    public static final String LAYER_POSE_CONFIG = "dynamicCrosshairLayerPose";

    private static final Boolean LAYER_POSE_OVERRIDE = readOverride("xbob.dynamiccrosshair.layerPose");

    private static Boolean readOverride(String key) {
        String value = System.getProperty(key);

        return value == null ? null : Boolean.valueOf(value);
    }
    /**
     * Alternative transform path for testing: {@code -Dxbob.dynamiccrosshair.spritePose=true}.
     *
     * <p>Normally xbob wraps libbamboo's framebuffer blit ({@code CustomFramebufferRenderer.draw}),
     * i.e. the full screen quad that contains the crosshair. With this flag the transform is applied
     * to the crosshair sprite draw <em>inside</em> that framebuffer instead, and the framebuffer blit
     * is left untransformed. Both paths move the crosshair by the same amount for translations, but
     * only the framebuffer path can rotate/scale the sprite image, so this is a diagnostic fallback:
     * if the crosshair only bobs with this flag, the framebuffer blit is the broken link.</p>
     */
    private static final boolean SPRITE_POSE = Boolean.parseBoolean(System.getProperty("xbob.dynamiccrosshair.spritePose", "false"));

    public static boolean isSpritePose() {
        return SPRITE_POSE;
    }

    private static Boolean present;

    public static boolean isPresent() {
        if (present == null) {
            present = Boolean.valueOf(ModList.get().isLoaded("dynamiccrosshair"));
        }

        return present.booleanValue();
    }

    public static boolean isForceLayerPose() {
        if (LAYER_POSE_OVERRIDE != null) {
            return LAYER_POSE_OVERRIDE.booleanValue();
        }

        try {
            return Config.check(LAYER_POSE_CONFIG);
        } catch (RuntimeException e) {
            // Config.define() has not run yet (e.g. a mixin called this during class load):
            // the layer pose path is the one that is known to work everywhere.
            return true;
        }
    }

    public static boolean shouldHandleCrosshair() {
        boolean result = isPresent() && DynamicCrosshairRenderer.isAvailable() && !isForceLayerPose();

        Debug.track("compat.shouldHandleCrosshair", result);
        Debug.state("compat.shouldHandleCrosshair", String.valueOf(result));

        return result;
    }
}
