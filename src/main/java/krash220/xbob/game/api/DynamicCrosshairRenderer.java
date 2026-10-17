package krash220.xbob.game.api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import krash220.xbob.MainMod;
import net.minecraft.client.gui.GuiGraphics;

public class DynamicCrosshairRenderer {

    private static boolean initialized;
    private static boolean available;
    private static boolean armed;

    private static Method preRender;
    private static Method postRender;
    private static Method fixCenteredPre;
    private static Method fixCenteredPost;
    private static Method render;
    private static Method renderCrosshairWithStyle;
    private static Method getDefaultCrosshair;
    private static Method shouldShowCrosshair;
    private static Field forceShowCrosshair;
    private static Field config;
    private static Method isFixCenteredCrosshair;
    private static Method isDynamicCrosshairStyle;
    private static Field autoHudCompat;

    private interface Lookup<T> {

        T get() throws ReflectiveOperationException;
    }

    private static <T> T lookup(String description, Lookup<T> action) throws ReflectiveOperationException {
        T value = action.get();

        Debug.info("DC probe: {} -> OK", description);

        return value;
    }

    private static Field optionalField(Class<?> owner, String name) {
        try {
            Field field = owner.getField(name);

            Debug.info("DC probe: optional field {}.{} -> OK", owner.getSimpleName(), name);

            return field;
        } catch (ReflectiveOperationException e) {
            Debug.info("DC probe: optional field {}.{} -> not present", owner.getSimpleName(), name);

            return null;
        }
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        if (!DynamicCrosshairCompat.isPresent()) {
            available = false;

            Debug.info("DynamicCrosshair is not loaded -> the DynamicCrosshair compatibility layer stays disabled");

            return;
        }

        Debug.reportDynamicCrosshairProbe();

        try {
            Class<?> renderer = Class.forName("mod.crend.dynamiccrosshair.render.CrosshairRenderer");
            Class<?> handler = Class.forName("mod.crend.dynamiccrosshair.component.CrosshairHandler");
            Class<?> mod = Class.forName("mod.crend.dynamiccrosshair.DynamicCrosshairMod");
            Class<?> configHandler = Class.forName("mod.crend.dynamiccrosshair.config.ConfigHandler");
            Class<?> style = Class.forName("mod.crend.dynamiccrosshair.style.CrosshairStyle");
            Class<?> framebuffer = Class.forName("mod.crend.libbamboo.render.CustomFramebufferRenderer");

            renderer.getMethod("wrapRender", GuiGraphics.class, int.class, int.class, Runnable.class, Runnable.class);
            Debug.info("DC probe: wrapRender(GuiGraphics, int, int, Runnable, Runnable) -> OK");

            framebuffer.getMethod("draw", GuiGraphics.class);
            Debug.info("DC probe: CustomFramebufferRenderer.draw(GuiGraphics) -> OK");

            preRender = lookup("CrosshairRenderer.preRender()", () -> renderer.getMethod("preRender"));
            postRender = lookup("CrosshairRenderer.postRender()", () -> renderer.getMethod("postRender"));
            fixCenteredPre = lookup("CrosshairRenderer.fixCenteredCrosshairPre(GuiGraphics, int, int)", () -> renderer.getMethod("fixCenteredCrosshairPre", GuiGraphics.class, int.class, int.class));
            fixCenteredPost = lookup("CrosshairRenderer.fixCenteredCrosshairPost(GuiGraphics)", () -> renderer.getMethod("fixCenteredCrosshairPost", GuiGraphics.class));
            render = lookup("CrosshairRenderer.render(GuiGraphics, int, int)", () -> renderer.getMethod("render", GuiGraphics.class, int.class, int.class));
            renderCrosshairWithStyle = lookup("CrosshairRenderer.renderCrosshair(GuiGraphics, CrosshairStyle, int, int)", () -> renderer.getMethod("renderCrosshair", GuiGraphics.class, style, int.class, int.class));
            getDefaultCrosshair = lookup("CrosshairHandler.getDefaultCrosshair()", () -> handler.getMethod("getDefaultCrosshair"));
            shouldShowCrosshair = lookup("CrosshairHandler.shouldShowCrosshair()", () -> handler.getMethod("shouldShowCrosshair"));
            forceShowCrosshair = lookup("CrosshairHandler.forceShowCrosshair", () -> handler.getField("forceShowCrosshair"));
            config = lookup("DynamicCrosshairMod.config", () -> mod.getField("config"));
            isFixCenteredCrosshair = lookup("ConfigHandler.isFixCenteredCrosshair()", () -> configHandler.getMethod("isFixCenteredCrosshair"));
            isDynamicCrosshairStyle = lookup("ConfigHandler.isDynamicCrosshairStyle()", () -> configHandler.getMethod("isDynamicCrosshairStyle"));

            // Optional: true while DynamicCrosshair renders through its AutoHUD fade path.
            autoHudCompat = optionalField(renderer, "autoHudCompat");

            available = true;
        } catch (ReflectiveOperationException e) {
            available = false;

            Debug.error("DynamicCrosshair compatibility layer DISABLED: reflective lookup failed (" + e + ")", e);
        } catch (RuntimeException | LinkageError e) {
            available = false;

            Debug.error("DynamicCrosshair compatibility layer DISABLED: unexpected error (" + e + ")", e);
        }

        Debug.info("DynamicCrosshair compatibility layer available={}", available);
        Debug.track("compat.available", available);
        Debug.state("compat.available", String.valueOf(available));
    }

    public static boolean isAvailable() {
        init();

        return available;
    }

    public static boolean isArmed() {
        return armed;
    }

    public static void render(GuiGraphics graphics, int x, int y) {
        if (!available) {
            Debug.count("dc.render.unavailable");
            Debug.throttled("dc.render.unavailable", "the wrapRender takeover ran but the DynamicCrosshair compatibility layer is unavailable -> DynamicCrosshair draws the crosshair without the xbob transform");

            return;
        }

        Debug.count("dc.render.call");
        Debug.verbose("DynamicCrosshair render takeover at ({}, {})", x, y);

        try {
            boolean force = forceShowCrosshair.getBoolean(null);
            boolean show = ((Boolean) shouldShowCrosshair.invoke(null)).booleanValue();

            Debug.track("dc.forceShowCrosshair", force);
            Debug.track("dc.shouldShowCrosshair", show);

            if (autoHudCompat != null) {
                boolean autoHud = autoHudCompat.getBoolean(null);

                Debug.track("dc.autoHudCompat", autoHud);
                Debug.state("dc.autoHudCompat", String.valueOf(autoHud));
            }

            if (!force && !show) {
                Debug.count("dc.render.suppressed");
                Debug.throttled("dc.render.suppressed", "DynamicCrosshair reports forceShowCrosshair={} shouldShowCrosshair={} -> no crosshair is drawn at all this frame", force, show);

                return;
            }

            preRender.invoke(null);

            Object cfg = config.get(null);
            boolean fixCentered = ((Boolean) isFixCenteredCrosshair.invoke(cfg)).booleanValue();
            boolean dynamicStyle = ((Boolean) isDynamicCrosshairStyle.invoke(cfg)).booleanValue();

            Debug.track("dc.fixCenteredCrosshair", fixCentered);
            Debug.track("dc.dynamicCrosshairStyle", dynamicStyle);

            if (fixCentered) {
                fixCenteredPre.invoke(null, graphics, x, y);
            }

            if (dynamicStyle) {
                Debug.count("dc.render.dynamicStyle");
                Debug.track("dc.lastPath", "dynamicStyle");

                armed = true;
                Debug.track("dc.armed", true);

                try {
                    render.invoke(null, graphics, x, y);
                } finally {
                    armed = false;
                    Debug.track("dc.armed", false);
                }
            } else {
                Debug.count("dc.render.defaultStyle");
                Debug.track("dc.lastPath", "defaultStyle");
                Debug.throttled("dc.render.defaultStyle", "dynamicCrosshairStyle=false -> xbob draws the vanilla crosshair style itself with the bob transform");

                MainMod.preDynamicCrosshair(graphics.pose());

                try {
                    renderCrosshairWithStyle.invoke(null, graphics, getDefaultCrosshair.invoke(null), x, y);
                } finally {
                    MainMod.postDynamicCrosshair(graphics.pose());
                }

                graphics.flush();
            }

            if (fixCentered) {
                fixCenteredPost.invoke(null, graphics);
            }

            postRender.invoke(null);
        } catch (ReflectiveOperationException e) {
            Debug.count("dc.render.error");
            Debug.error("DynamicCrosshair render threw an exception (it used to be swallowed silently): " + e, e);
        }
    }
}
