package krash220.xbob.game.api;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import krash220.xbob.game.api.bus.GuiBus;
import krash220.xbob.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Render {

    public static int getScaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static int getScaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public static boolean isDebugCrosshair() {
        Minecraft mc = Minecraft.getInstance();

        return mc.options.hideGui || mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);
    }

    /**
     * Builds the same matrix the game uses to project the world onto the screen,
     * so that the crosshair can follow the vanilla view transformation:
     * projection * hurt tilt * view bobbing * distortion (+ CameraOverhaul).
     */
    public static void buildCameraMatrix(Matrix4f matrix, float partialTicks, boolean bobbing) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderState state = mc.gameRenderer.getGameRenderState();
        CameraRenderState camera = state.levelRenderState.cameraRenderState;
        OptionsRenderState options = state.optionsRenderState;

        // 26.1 draws the HUD (and thus the crosshair) with a dedicated perspective projection built from
        // the camera's HUD FOV, so use the very same parameters to map view offsets onto the screen.
        float aspect = (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();

        matrix.set(new Matrix4f().perspective((float) Math.toRadians(camera.hudFov), aspect, 0.05f, 100.0f));

        if (bobbing) {
            GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
            PoseStack bob = new PoseStack();

            accessor.xbob$bobHurt(camera, bob);
            if (options.bobView) {
                accessor.xbob$bobView(camera, bob);
            }

            matrix.mul(bob.last().pose());

            distortion(matrix, partialTicks, options);
            modCamera(matrix);
        }
    }

    public static void distortion(Matrix4f matrix, float partialTicks, OptionsRenderState options) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            return;
        }

        float portal = Mth.lerp(partialTicks, player.oPortalEffectIntensity, player.portalEffectIntensity);
        float nausea = player.getEffectBlendFactor(MobEffects.NAUSEA, partialTicks);
        float intensity = Math.max(portal, nausea) * (options.screenEffectScale * options.screenEffectScale);

        if (intensity <= 0.0f) {
            return;
        }

        float skew = 5.0f / (intensity * intensity + 5.0f) - intensity * 0.04f;

        skew *= skew;

        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        Vector3f axis = new Vector3f(0.0f, Mth.SQRT_OF_TWO / 2.0f, Mth.SQRT_OF_TWO / 2.0f);
        float angle = (accessor.xbob$getSpinningEffectTime() + partialTicks * accessor.xbob$getSpinningEffectSpeed()) * ((float) Math.PI / 180.0f);

        matrix.rotate(angle, axis);
        matrix.scale(1.0f / skew, 1.0f, 1.0f);
        matrix.rotate(-angle, axis);
    }

    private static Boolean cameraOverhaul = null;

    public static void modCamera(Matrix4f matrix) {
        if (cameraOverhaul == null) {
            try {
                Class.forName("mirsario.cameraoverhaul.core.callbacks.ModifyCameraTransformCallback");
                cameraOverhaul = Boolean.TRUE;
            } catch (ClassNotFoundException e) {
                cameraOverhaul = Boolean.FALSE;
            }
        }

        if (cameraOverhaul.booleanValue()) {
            try {
                var callbackClass = Class.forName("mirsario.cameraoverhaul.core.callbacks.ModifyCameraTransformCallback");
                var event = callbackClass.getField("EVENT").get(null);
                var invoker = event.getClass().getMethod("Invoker").invoke(event);
                var modifyMethod = invoker.getClass().getMethod("ModifyCameraTransform", Object.class, Object.class);
                var transformClass = Class.forName("mirsario.cameraoverhaul.core.structures.Transform");
                var transform = modifyMethod.invoke(invoker, null, transformClass.getDeclaredConstructor().newInstance());
                var eulerRot = transformClass.getField("eulerRot").get(transform);
                var xField = eulerRot.getClass().getField("x");
                var yField = eulerRot.getClass().getField("y");
                var zField = eulerRot.getClass().getField("z");

                matrix.rotate((float) Math.toRadians(zField.getDouble(eulerRot)), 0.0f, 0.0f, 1.0f);
                matrix.rotate((float) Math.toRadians(xField.getDouble(eulerRot)), 1.0f, 0.0f, 0.0f);
                matrix.rotate((float) Math.toRadians(yField.getDouble(eulerRot)), 0.0f, 1.0f, 0.0f);
            } catch (Exception e) {
                cameraOverhaul = Boolean.FALSE;
            }
        }
    }

    public static float getCenterDepth() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.getCameraEntity();
        float partialTicks = GuiBus.partialTicks;

        if (entity != null && mc.level != null) {
            Vec3 from = entity.getEyePosition(partialTicks);
            Vec3 to = from.add(entity.getViewVector(partialTicks).scale(1000.0));
            BlockHitResult result = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));

            if (result.getType() != HitResult.Type.MISS) {
                return (float) result.getLocation().distanceTo(from);
            }
        }

        return 1000.0f;
    }
}
