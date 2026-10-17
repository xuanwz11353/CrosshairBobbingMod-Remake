package krash220.xbob.game.api;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import org.joml.Matrix4f;

/**
 * Diagnostic logging used to investigate crosshair bobbing problems in modpacks.
 *
 * <p>Everything is written to the normal game log with the {@code xbob} logger and the
 * {@code [xbob-debug]} prefix, so it can be grepped easily.</p>
 *
 * <ul>
 *   <li>{@code -Dxbob.debug=true} enables everything (default: disabled).</li>
 *   <li>{@code -Dxbob.debug.verbose=true} adds per-frame/per-draw detail (default: disabled).</li>
 *   <li>{@code -Dxbob.debug.interval=5} changes the summary interval in seconds (default: 5).</li>
 * </ul>
 */
public final class Debug {

    private static final String TAG = "[xbob-debug] ";

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("xbob.debug", "false"));
    public static final boolean VERBOSE = Boolean.parseBoolean(System.getProperty("xbob.debug.verbose", "false"));
    /** Traces every crosshair/obstruction sprite that is drawn; -Dxbob.debug.sprites=false disables it. */
    public static final boolean SPRITES = !"false".equalsIgnoreCase(System.getProperty("xbob.debug.sprites", "true"));
    public static final long INTERVAL_MS = readInterval();
    /**
     * Multiplies the computed bob offset, e.g. {@code -Dxbob.debug.bobScale=8}.
     *
     * <p>Diagnostic only: with a small offset (a few pixels) it is hard to tell whether a HUD
     * element really does not move or whether it moves too little to notice. An amplified bob
     * makes the difference obvious: whatever moves with it is inside xbob's transform, whatever
     * stays put is drawn outside of it.</p>
     */
    public static final float BOB_SCALE = readFloat("xbob.debug.bobScale", 1.0f);

    private static float readFloat(String key, float fallback) {
        try {
            return Float.parseFloat(System.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Mods known to touch the crosshair/HUD render path, worth reporting when present. */
    private static final String[] WATCHED_MODS = {
        "dynamiccrosshair", "shouldersurfing", "autohud", "immediatelyfast", "exordium", "sodium",
        "embeddium", "rubidium", "iris", "oculus", "betterhud", "bettercrosshair", "crosshair",
        "firstpersonmodel", "notenoughanimations", "cameraoverhaul", "yacl"
    };

    private static final Object LOCK = new Object();

    /** key -> single element array holding the counter value for the current summary window. */
    private static final Map<String, long[]> COUNTERS = new LinkedHashMap<>();
    /** key -> {lastLogTimestamp, suppressedLines} */
    private static final Map<String, long[]> THROTTLE = new LinkedHashMap<>();
    /** key -> last logged value, used to log only on change. */
    private static final Map<String, String> STATE = new LinkedHashMap<>();
    /** key -> latest value, printed in every summary. */
    private static final Map<String, String> TRACKED = new LinkedHashMap<>();

    /**
     * Stack of the pose transforms xbob currently has installed around a draw call.
     * {@code TRUE} means the entry also translated the pose by the bob offset.
     */
    private static final java.util.ArrayDeque<Boolean> TRANSFORMS = new java.util.ArrayDeque<>();

    private static long windowStart;
    private static long ticks;

    private Debug() {
    }

    private static long readInterval() {
        long seconds = 5L;

        try {
            seconds = Long.parseLong(System.getProperty("xbob.debug.interval", "5"));
        } catch (NumberFormatException ignored) {
        }

        return Math.max(1L, seconds) * 1000L;
    }

    public static void info(String format, Object... args) {
        if (!ENABLED) {
            return;
        }

        Logger.info(TAG + format, args);
    }

    public static void verbose(String format, Object... args) {
        if (!ENABLED || !VERBOSE) {
            return;
        }

        Logger.info(TAG + format, args);
    }

    public static void error(String message, Throwable throwable) {
        if (!ENABLED) {
            return;
        }

        Logger.error(TAG + message, throwable);
    }

    /**
     * Marks the start of a pose transform xbob installed around the current draw call.
     *
     * @param applied {@code true} when the pose was also translated by the bob offset
     */
    public static void pushTransform(boolean applied) {
        if (!ENABLED) {
            return;
        }

        if (TRANSFORMS.size() < 32) {
            TRANSFORMS.push(applied);
        }
    }

    /** Marks the end of the pose transform pushed by {@link #pushTransform(boolean)}. */
    public static void popTransform() {
        if (!ENABLED) {
            return;
        }

        if (!TRANSFORMS.isEmpty()) {
            TRANSFORMS.pop();
        }
    }

    /** Number of xbob transforms that are installed right now and carry the bob offset. */
    public static int transformDepth() {
        int depth = 0;

        for (Boolean applied : TRANSFORMS) {
            if (Boolean.TRUE.equals(applied)) {
                depth++;
            }
        }

        return depth;
    }

    /** Reads a value remembered with {@link #track(String, Object)}. */
    public static String tracked(String key) {
        synchronized (LOCK) {
            return TRACKED.get(key);
        }
    }

    /**
     * Traces a crosshair/obstruction sprite draw.
     *
     * <p>Each line reports the translation which is active on the pose stack when the sprite is
     * drawn ({@code poseTranslate}), the bob offset xbob computed for this frame
     * ({@code bob.offset}) and how many bob transforms xbob currently has installed
     * ({@code xbobTransformDepth}). A sprite drawn with {@code xbobTransformDepth=0} while
     * {@code bob.offset} is not zero is drawn <b>outside</b> the bobbing transform - that is the
     * part of the crosshair which stands still while everything else bobs.</p>
     */
    public static void traceSprite(String kind, ResourceLocation sprite, int x, int y, int w, int h, PoseStack pose) {
        if (!ENABLED || !SPRITES || sprite == null) {
            return;
        }

        try {
            String path = sprite.getPath();

            if (!path.contains("crosshair") && !path.contains("obstruct")) {
                return;
            }

            int depth = transformDepth();
            boolean bobbed = depth > 0;
            float translateX = 0.0f;
            float translateY = 0.0f;

            if (pose != null) {
                Matrix4f matrix = pose.last().pose();

                translateX = matrix.m30();
                translateY = matrix.m31();
            }

            String name = sprite.getNamespace() + ":" + path;

            count("sprite." + name + (bobbed ? ".bobbed" : ".static"));

            throttled("sprite." + kind + "." + name,
                "{} {} drawn at ({}, {}) size={}x{} poseTranslate=({}, {}) xbobTransformDepth={} bob.offset={} -> {}",
                kind, name, x, y, w, h, fmt(translateX), fmt(translateY), depth, tracked("bob.offset"),
                bobbed ? "inside xbob's bob transform" : "OUTSIDE xbob's bob transform: this sprite does NOT bob");
        } catch (Throwable t) {
            // diagnostics must never break rendering
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** Counts an event; the totals are printed by {@link #tick()}. */
    public static void count(String key) {
        count(key, 1L);
    }

    public static void count(String key, long amount) {
        if (!ENABLED) {
            return;
        }

        synchronized (LOCK) {
            COUNTERS.computeIfAbsent(key, k -> new long[1])[0] += amount;
        }
    }

    /** Remembers a value for the next summary line (no immediate log output). */
    public static void track(String key, Object value) {
        if (!ENABLED) {
            return;
        }

        synchronized (LOCK) {
            TRACKED.put(key, String.valueOf(value));
        }
    }

    /** Logs only when the value for {@code key} changed. */
    public static void state(String key, String value) {
        if (!ENABLED) {
            return;
        }

        String previous;

        synchronized (LOCK) {
            previous = STATE.put(key, value);
        }

        if (!value.equals(previous)) {
            Logger.info(TAG + "state {}: {} -> {}", key, previous == null ? "<initial>" : previous, value);
        }
    }

    /**
     * Logs at most one line per summary interval for the given key and reports how many
     * similar lines were suppressed in between.
     */
    public static void throttled(String key, String format, Object... args) {
        if (!ENABLED) {
            return;
        }

        long now = System.currentTimeMillis();
        long suppressed;

        synchronized (LOCK) {
            long[] slot = THROTTLE.computeIfAbsent(key, k -> new long[2]);

            if (now - slot[0] < INTERVAL_MS) {
                slot[1]++;
                return;
            }

            suppressed = slot[1];
            slot[0] = now;
            slot[1] = 0L;
        }

        if (suppressed > 0) {
            Logger.info(TAG + "[{}] " + format + " (+{} similar lines suppressed)", prepend(key, args, suppressed));
        } else {
            Logger.info(TAG + "[{}] " + format, prepend(key, args));
        }
    }

    /** Drives the periodic summary; call it once per rendered frame. */
    public static void tick() {
        if (!ENABLED) {
            return;
        }

        ticks++;

        long now = System.currentTimeMillis();

        if (windowStart == 0L) {
            windowStart = now;

            return;
        }

        if (now - windowStart < INTERVAL_MS) {
            return;
        }

        double seconds = (now - windowStart) / 1000.0;
        windowStart = now;

        StringBuilder summary = new StringBuilder(256);
        summary.append("SUMMARY window=").append(seconds).append("s frames=").append(ticks);

        synchronized (LOCK) {
            for (Map.Entry<String, long[]> entry : COUNTERS.entrySet()) {
                summary.append(' ').append(entry.getKey()).append('=').append(entry.getValue()[0]);
                entry.getValue()[0] = 0L;
            }

            summary.append(" ||");

            for (Map.Entry<String, String> entry : TRACKED.entrySet()) {
                summary.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }

        ticks = 0L;

        Logger.info(TAG + summary.toString());
    }

    /** One-off report about the environment; call once during client startup. */
    public static void reportEnvironment() {
        if (!ENABLED) {
            return;
        }

        Logger.info(TAG + "================================================================");
        Logger.info(TAG + "Crosshair Bobbing diagnostics are enabled");
        Logger.info(TAG + "  disable: -Dxbob.debug=false | per-frame detail: -Dxbob.debug.verbose=true | summary: every {}s", INTERVAL_MS / 1000L);
        Logger.info(TAG + "  (these diagnostics are off by default; start the game with -Dxbob.debug=true to turn them on)");
        Logger.info(TAG + "  minecraft={} platform={} client={}", Loader.getVersion(), Loader.getPlatform(), Loader.isClient());
        Logger.info(TAG + "  dynamiccrosshair present={} version={}", DynamicCrosshairCompat.isPresent(), modVersion("dynamiccrosshair"));
        Logger.info(TAG + "  dynamiccrosshair mode={} (layer-pose = DynamicCrosshair draws its own crosshair and xbob transforms the whole GUI layer; per-draw = xbob takes the crosshair draw over. Override with -Dxbob.dynamiccrosshair.layerPose=true|false or the xbob config)", DynamicCrosshairCompat.isForceLayerPose() ? "layer-pose" : "per-draw takeover");
        Logger.info(TAG + "  shouldersurfing present={} version={}", ShoulderSurfingCompat.isPresent(), modVersion(ShoulderSurfingCompat.MOD_ID));
        Logger.info(TAG + "  shouldersurfing own crosshair layer '{}' transformed={} (set -Dxbob.shouldersurfing.postCrosshair=true, or enable 'shouldersurfingCrosshair' in the xbob config, to bob ShoulderSurfing's obstruction marker as well)", ShoulderSurfingCompat.POST_CROSSHAIR_LAYER, ShoulderSurfingCompat.transformPostCrosshair());
        Logger.info(TAG + "  sprite tracing={} (set -Dxbob.debug.sprites=false to disable)", SPRITES);
        Logger.info(TAG + "  dynamiccrosshair sprite-pose mode={} (set -Dxbob.dynamiccrosshair.spritePose=true to move the crosshair sprite itself instead of the libbamboo framebuffer blit)", DynamicCrosshairCompat.isSpritePose());
        Logger.info(TAG + "  bob amplification={}x (set -Dxbob.debug.bobScale=8 to make a non-moving element obvious)", BOB_SCALE);

        for (String modId : WATCHED_MODS) {
            if (!"dynamiccrosshair".equals(modId) && ModList.get().isLoaded(modId)) {
                Logger.info(TAG + "  watched mod present: {} {}", modId, modVersion(modId));
            }
        }

        Logger.info(TAG + "  loaded mods: {}", ModList.get().size());

        List<String> mods = new ArrayList<>();

        for (var info : ModList.get().getMods()) {
            mods.add(info.getModId() + "@" + info.getVersion());
        }

        Collections.sort(mods);

        for (int i = 0; i < mods.size(); i += 12) {
            Logger.info(TAG + "  mod list [{}]: {}", i / 12, mods.subList(i, Math.min(mods.size(), i + 12)));
        }

        Logger.info(TAG + "================================================================");
    }

    /**
     * Dumps the reflection surface that the DynamicCrosshair compatibility layer depends on.
     * The most important line is {@code CrosshairRenderer.wrapRender}: it only exists in
     * DynamicCrosshair 9.x, which is the build the compatibility layer is written for.
     */
    public static void reportDynamicCrosshairProbe() {
        if (!ENABLED) {
            return;
        }

        Logger.info(TAG + "--- DynamicCrosshair reflection probe (version {}) ---", modVersion("dynamiccrosshair"));

        probeClass("mod.crend.dynamiccrosshair.render.CrosshairRenderer",
            "wrapRender", "render", "renderCrosshair", "preRender", "postRender",
            "fixCenteredCrosshairPre", "fixCenteredCrosshairPost");
        probeClass("mod.crend.dynamiccrosshair.component.CrosshairHandler",
            "getDefaultCrosshair", "shouldShowCrosshair");
        probeClass("mod.crend.dynamiccrosshair.config.ConfigHandler",
            "isFixCenteredCrosshair", "isDynamicCrosshairStyle");
        probeClass("mod.crend.libbamboo.render.CustomFramebufferRenderer", "draw");

        if (classOrNull("mod.crend.dynamiccrosshair.DynamicCrosshairMod") == null) {
            Logger.info(TAG + "  class mod.crend.dynamiccrosshair.DynamicCrosshairMod: NOT FOUND");
        } else {
            Logger.info(TAG + "  class mod.crend.dynamiccrosshair.DynamicCrosshairMod: found (holds the static 'config' field)");
        }

        if (classOrNull("mod.crend.autohud.AutoHud") != null) {
            Logger.info(TAG + "  AutoHUD classes are present -> DynamicCrosshair renders through its AutoHUD fade path (extra render layers around the crosshair)");
        }

        Logger.info(TAG + "--- end of DynamicCrosshair probe ---");
    }

    private static void probeClass(String className, String... methodNames) {
        Class<?> clazz = classOrNull(className);

        if (clazz == null) {
            Logger.info(TAG + "  class {}: NOT FOUND", className);
            return;
        }

        Logger.info(TAG + "  class {}: found", className);

        for (String name : methodNames) {
            List<String> found = new ArrayList<>();

            try {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals(name) && !method.isSynthetic()) {
                        found.add(describe(method));
                    }
                }
            } catch (Throwable t) {
                Logger.info(TAG + "    method {}: could not be listed ({})", name, t);
                continue;
            }

            if (found.isEmpty()) {
                Logger.info(TAG + "    method {}: MISSING", name);
            } else {
                for (String signature : found) {
                    Logger.info(TAG + "    method {}: {}", name, signature);
                }
            }
        }
    }

    private static Class<?> classOrNull(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String describe(Method method) {
        StringBuilder builder = new StringBuilder();

        builder.append(Modifier.toString(method.getModifiers())).append(' ');
        builder.append(method.getReturnType().getSimpleName()).append(' ').append(method.getName()).append('(');

        Class<?>[] parameters = method.getParameterTypes();

        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append(parameters[i].getSimpleName());
        }

        return builder.append(')').toString();
    }

    private static String modVersion(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not loaded");
        } catch (Throwable t) {
            return "error: " + t;
        }
    }

    private static Object[] prepend(String key, Object[] args) {
        Object[] out = new Object[args.length + 1];

        out[0] = key;
        System.arraycopy(args, 0, out, 1, args.length);

        return out;
    }

    private static Object[] prepend(String key, Object[] args, Object tail) {
        Object[] out = new Object[args.length + 2];

        out[0] = key;
        System.arraycopy(args, 0, out, 1, args.length);
        out[out.length - 1] = tail;

        return out;
    }
}
