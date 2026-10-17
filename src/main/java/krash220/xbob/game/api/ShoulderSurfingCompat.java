package krash220.xbob.game.api;

import java.lang.reflect.Method;

import net.neoforged.fml.ModList;

/**
 * Diagnostics (and one opt-in workaround) for ShoulderSurfing 5.x.
 *
 * <p>ShoulderSurfing renders its own obstruction marker as a plain 15x15 sprite right at the
 * screen centre, and it does so from its <b>own</b> GUI layer
 * ({@code shouldersurfing:post_crosshair}, registered ABOVE the vanilla crosshair layer). That
 * draw call is not part of the vanilla crosshair code path, so neither the DynamicCrosshair
 * hooks ({@code wrapRender} / {@code CustomFramebufferRenderer.draw}) nor the {@code GuiMixin}
 * attack-indicator redirects ever see it - it stays still while everything else bobs.</p>
 *
 * <p>{@code -Dxbob.shouldersurfing.postCrosshair=true} makes xbob apply its bob transform around
 * that layer as well, which is the fix candidate for the "crosshair stands still in the modpack"
 * report.</p>
 */
public final class ShoulderSurfingCompat {

    public static final String MOD_ID = "shouldersurfing";

    /** GUI layer that carries ShoulderSurfing's obstruction marker (drawn ABOVE the crosshair layer). */
    public static final String POST_CROSSHAIR_LAYER = "shouldersurfing:post_crosshair";

    /** GUI layer ShoulderSurfing renders right BELOW the crosshair layer. */
    public static final String PRE_CROSSHAIR_LAYER = "shouldersurfing:pre_crosshair";

    /** -Dxbob.shouldersurfing.postCrosshair=true */
    public static final String PROPERTY = "xbob.shouldersurfing.postCrosshair";

    /** Key of the same toggle inside the xbob config file / config screen. */
    public static final String CONFIG_KEY = "shouldersurfingCrosshair";

    private static final boolean PROPERTY_ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));

    private static boolean probed;
    private static boolean available;
    private static Object renderer;
    private static Method isCrosshairDynamic;
    private static Method isCrosshairVisible;
    private static Method isObstructionCrosshairVisible;
    private static Method isObstructionIndicatorVisible;

    private ShoulderSurfingCompat() {
    }

    /**
     * Whether xbob should apply its bob transform around ShoulderSurfing's own crosshair layer.
     *
     * <p>Can be switched with the system property {@code -Dxbob.shouldersurfing.postCrosshair=true}
     * or in game through the xbob config screen ({@code config/xbob.json}).</p>
     */
    public static boolean transformPostCrosshair() {
        try {
            return PROPERTY_ENABLED || Config.check(CONFIG_KEY);
        } catch (Throwable t) {
            return PROPERTY_ENABLED;
        }
    }

    public static boolean isPresent() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Reads ShoulderSurfing's own crosshair state once per frame and reports it through
     * {@link Debug}; without this the log cannot tell whether ShoulderSurfing wanted to draw
     * something of its own this frame.
     */
    public static void probe() {
        if (!Debug.ENABLED || !isPresent()) {
            return;
        }

        try {
            if (!probed) {
                init();
            }

            if (!available) {
                return;
            }

            boolean visible = (Boolean) isCrosshairVisible.invoke(renderer);
            boolean dynamic = (Boolean) isCrosshairDynamic.invoke(renderer);
            boolean obstructionCrosshair = (Boolean) isObstructionCrosshairVisible.invoke(renderer);
            boolean obstructionIndicator = (Boolean) isObstructionIndicatorVisible.invoke(renderer);

            Debug.track("ss.crosshairVisible", visible);
            Debug.track("ss.crosshairDynamic", dynamic);
            Debug.track("ss.obstructionCrosshair", obstructionCrosshair);
            Debug.track("ss.obstructionIndicator", obstructionIndicator);
            Debug.track("ss.postCrosshairTransformed", transformPostCrosshair());

            if (obstructionIndicator) {
                Debug.count("ss.obstructionIndicator.shown");
            }

            if (obstructionCrosshair) {
                Debug.count("ss.obstructionCrosshair.shown");
            }

            Debug.throttled("ss.state",
                "ShoulderSurfing crosshair state: crosshairVisible={} crosshairDynamic={} obstructionCrosshairVisible={} obstructionIndicatorVisible={}",
                visible, dynamic, obstructionCrosshair, obstructionIndicator);
        } catch (Throwable t) {
            available = false;

            Debug.error("ShoulderSurfing crosshair probe failed", t);
        }
    }

    private static void init() throws Exception {
        probed = true;

        Class<?> api = Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing");
        Class<?> rendererApi = Class.forName("com.github.exopandora.shouldersurfing.api.client.renderer.ICrosshairRenderer");

        Object instance = api.getMethod("getInstance").invoke(null);
        renderer = api.getMethod("getCrosshairRenderer").invoke(instance);

        isCrosshairDynamic = rendererApi.getMethod("isCrosshairDynamic");
        isCrosshairVisible = rendererApi.getMethod("isCrosshairVisible");
        isObstructionCrosshairVisible = rendererApi.getMethod("isObstructionCrosshairVisible");
        isObstructionIndicatorVisible = rendererApi.getMethod("isObstructionIndicatorVisible");

        available = renderer != null;

        Debug.info("ShoulderSurfing crosshair probe ready: renderer={}", renderer == null ? "<null>" : renderer.getClass().getName());
        Debug.info("ShoulderSurfing renders its obstruction marker in its own GUI layer '{}' -> that sprite is {}", POST_CROSSHAIR_LAYER, transformPostCrosshair() ? "INCLUDED in the bob transform" : "OUTSIDE the bob transform (set -Dxbob.shouldersurfing.postCrosshair=true, or enable 'shouldersurfingCrosshair' in the xbob config, to include it)");
    }
}
