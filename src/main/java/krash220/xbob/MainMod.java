package krash220.xbob;

import java.util.function.Function;

import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import krash220.xbob.game.api.Config;
import krash220.xbob.game.api.DynamicCrosshairCompat;
import krash220.xbob.game.api.Loader;
import krash220.xbob.game.api.Logger;
import krash220.xbob.game.api.Player;
import krash220.xbob.game.api.Player.ItemType;
import krash220.xbob.game.api.Render;
import krash220.xbob.game.api.bus.GuiBus;
import krash220.xbob.game.api.bus.PlayerBus;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MainMod {

    private static final float MIN_ATTACK_SCALE = 1.4f;
    private static final float MAX_ATTACK_SCALE = 2.0f;
    private static final float MIN_ATTACK_DEGREES = 15f;
    private static final float MAX_ATTACK_DEGREES = 25f;
    private static final int ATTACK_ANIM_LEN = 200;

    private static final int CRIT_ANIM_LEN = 300;
    private static final float CRIT_SCALE = 2.0f;

    private static final String CONFIG_BOB = "bob";
    private static final String CONFIG_SNEAK = "sneak";
    private static final String CONFIG_ITEM = "item";
    private static final String CONFIG_AIM = "aim";
    private static final String CONFIG_SPEAR = "spear";
    private static final String CONFIG_XBOW = "xbow";
    private static final String CONFIG_EAT = "eat";
    private static final String CONFIG_ATK = "atk";

    private final Matrix4f matrix = new Matrix4f();
    private final Vector4f projected = new Vector4f();
    private final Vector4f rotated = new Vector4f();

    private boolean isCrit;
    private int critDir = 1;
    private long attackAnim;
    private float attackAnimDegree;
    private float attackAnimScale;
    private float attackDamage;

    // Diagnostic instrumentation (read by the client game test; harmless at runtime).
    public static volatile boolean debugApplied;
    public static volatile float debugOffsetX;
    public static volatile float debugOffsetY;
    public static volatile float debugAngle;
    public static volatile float debugScale = 1.0f;

    public MainMod() {
        Logger.info("Hello, Crosshair Bobbing!");
        Logger.info("Platform: {}, Minecraft: {}, isClient: {}", Loader.getPlatform(), Loader.getVersion(), Loader.isClient());

        GuiBus.registerRenderCrosshair(this::preRenderCrossHair, this::postRenderCrossHair);
        PlayerBus.registerAttack(this::onAttack);

        Config.define(CONFIG_BOB, true);
        Config.define(CONFIG_SNEAK, true);
        Config.define(CONFIG_ITEM, true);
        Config.define(CONFIG_AIM, true);
        Config.define(CONFIG_SPEAR, true);
        Config.define(CONFIG_XBOW, true);
        Config.define(CONFIG_EAT, true);
        Config.define(CONFIG_ATK, true);
        Config.load();
        Config.registerGui();

        Logger.info("DynamicCrosshair detected: {}", DynamicCrosshairCompat.isPresent());
    }

    public void preRenderCrossHair(GuiGraphicsExtractor graphics, float partialTicks) {
        Matrix3x2fStack pose = graphics.pose();

        pose.pushMatrix();
        DynamicCrosshairCompat.setTransforming(true);
        debugApplied = false;

        if (!Render.isDebugCrosshair()) {
            this.matrix.identity();

            // bobbing, damage tilt, portal/nausea distortion and CameraOverhaul
            Render.buildCameraMatrix(this.matrix, partialTicks, Config.check(CONFIG_BOB));

            float item = 1.0f - Player.changeItemProgress(partialTicks);
            float swing = Player.swingProgress(partialTicks);

            long now = System.currentTimeMillis();

            if (!this.isCrit && now < this.attackAnim + ATTACK_ANIM_LEN || this.isCrit && now < this.attackAnim + CRIT_ANIM_LEN || !Config.check(CONFIG_ATK)) {
                swing = 1.0f;
            }

            if (Config.check(CONFIG_ITEM)) {
                this.matrix.translate(item * 0.08f, item * -0.3f, 0.0f);
            }

            float dist = Render.getCenterDepth();
            this.matrix.transform(this.projected.set(0f, 0f, -dist, 1f));
            this.matrix.transform(this.rotated.set(0f, 1f, -dist, 1f));

            float offsetX = this.projected.x / this.projected.w;
            float offsetY = this.projected.y / this.projected.w;
            float rotOffX = this.rotated.x / this.rotated.w - this.projected.x / this.projected.w;
            float rotOffY = this.rotated.y / this.rotated.w - this.projected.y / this.projected.w;

            float angle = (float) Math.toDegrees(Math.atan2(rotOffX, rotOffY));
            float scale = 1.0f + interpolation(Math::sin, swing, 0.5 * Math.PI, Math.PI, false) * 0.2f;
            float scaleBow = 1.0f;

            if (Config.check(CONFIG_SPEAR)) {
                angle += interpolation(Math::sin, Player.spinProgress(partialTicks), 0, 0.5 * Math.PI, false) * -720f;
            }

            if (Config.check(CONFIG_SNEAK)) {
                angle += -Player.sneakProgress(partialTicks) * 70f;
            }

            ItemType using = Player.getUsingType();
            float usingProgress = Player.usingItemProgress(partialTicks);

            if (using == ItemType.EATING && Config.check(CONFIG_EAT)) {
                if (usingProgress < 0.2) {
                    angle += interpolation(Math::sin, Math.min(1.0, usingProgress * 10.0), 0.0, 0.5 * Math.PI, false) * 135.0;
                } else if (usingProgress >= 0.2 && usingProgress < 0.9) {
                    angle += interpolation(Math::sin, (usingProgress - 0.2) / 0.7, 0.0, 6.0 * Math.PI, false) * -30.0 + 45.0;
                } else if (usingProgress >= 0.9) {
                    angle += interpolation(Math::sin, Math.min(1.0, (usingProgress - 0.9) * 10.0), 0.0, 0.5 * Math.PI, false) * -135.0 + 45.0;
                }
            } else if (using == ItemType.CROSSBOW && Config.check(CONFIG_XBOW)) {
                if (usingProgress < 0.9) {
                    angle += usingProgress / 0.9 * 360.0;
                } else if (usingProgress >= 0.9) {
                    angle += interpolation(Math::sin, Math.min(1.0, (usingProgress - 0.9) * 10.0), 0, 0.5 * Math.PI, false) * -180.0 + 180.0;
                }
            }

            if (using == ItemType.CROSSBOW && Config.check(CONFIG_XBOW)) {
                if (usingProgress < 0.9) {
                    scale *= interpolation(MainMod::fract, 1.0 - usingProgress / 0.9, 0, 5, false) * 0.8 + 1.0;
                }
            } else if ((using == ItemType.BOW || using == ItemType.SPEAR) && Config.check(CONFIG_AIM)) {
                scale *= interpolation(Math::sin, usingProgress, 0, 0.5 * Math.PI, false) * 0.5 + 1.0;
                scaleBow *= interpolation(Math::sin, usingProgress, 0, 0.5 * Math.PI, false) * -0.6 + 1.0;
            }

            if (Config.check(CONFIG_ATK)) {
                if (this.isCrit && now < this.attackAnim + CRIT_ANIM_LEN) {
                    float x = lerpTime(this.attackAnim, this.attackAnim + CRIT_ANIM_LEN, now);

                    angle += interpolation(Math::sin, x, 0, 0.5 * Math.PI, false) * 360.0f * critDir;
                    scale *= interpolation(Math::sin, x, 0, Math.PI, false) * (CRIT_SCALE - 1.0f) + 1.0f;
                } else if (now < this.attackAnim + ATTACK_ANIM_LEN) {
                    float x = lerpTime(this.attackAnim, this.attackAnim + ATTACK_ANIM_LEN, now);

                    if (this.attackDamage > 0.01) {
                        angle += interpolation(Math::sin, x, 0.5 * Math.PI, Math.PI, false) * this.attackDamage * this.attackAnimDegree;
                        scale *= interpolation(Math::sin, x, 0.5 * Math.PI, Math.PI, false) * this.attackDamage * (this.attackAnimScale - 1.0f) + 1.0f;
                    } else {
                        offsetX += interpolation(Math::sin, x, 0, 4.0 * Math.PI, false) * 0.01;
                    }
                }
            }

            offsetX *= Render.getScaledWidth() * 0.5f;
            offsetY *= Render.getScaledHeight() * 0.5f;

            float centerX = Render.getScaledWidth() * 0.5f;
            float centerY = Render.getScaledHeight() * 0.5f;

            pose.translate(centerX, centerY);
            pose.translate(offsetX, -offsetY);
            pose.rotate((float) Math.toRadians(angle));
            pose.scale(scale, scale * scaleBow);
            pose.translate(-centerX, -centerY);

            debugOffsetX = offsetX;
            debugOffsetY = offsetY;
            debugAngle = angle;
            debugScale = scale;
            debugApplied = true;
        }
    }

    public void postRenderCrossHair(GuiGraphicsExtractor graphics, float partialTicks) {
        DynamicCrosshairCompat.setTransforming(false);

        graphics.pose().popMatrix();
    }

    public void onAttack(float damage) {
        long now = System.currentTimeMillis();
        boolean crit = damage > 1.0f;

        if (crit || this.isCrit && now > this.attackAnim + CRIT_ANIM_LEN || !this.isCrit) {
            this.attackDamage = Math.min(1.0f, damage);
            this.attackAnim = now;

            float r = (float) Math.random();

            this.attackAnimDegree = r * (MAX_ATTACK_DEGREES - MIN_ATTACK_DEGREES) + MIN_ATTACK_DEGREES;
            this.attackAnimScale = r * (MAX_ATTACK_SCALE - MIN_ATTACK_SCALE) + MIN_ATTACK_SCALE;

            this.isCrit = crit;
            this.critDir *= -1;
        }
    }

    private float interpolation(Function<Double, Double> func, double x, double begin, double end, boolean beginZeroY) {
        x = Math.max(Math.min(1f, x), 0f);

        float y = func.apply((double) ((end - begin) * x + begin)).floatValue();

        if (beginZeroY) {
            y -= func.apply((double) begin).floatValue();
        }

        return y;
    }

    private float lerp(float a, float b, float x) {
        return (1 - x) * a + x * b;
    }

    private float lerpTime(long begin, long end, long now) {
        return (float) (now - begin) / (float) (end - begin);
    }

    private static double fract(double v) {
        return v - Math.floor(v);
    }
}
