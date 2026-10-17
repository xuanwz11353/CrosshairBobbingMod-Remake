package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import krash220.xbob.MainMod;
import krash220.xbob.game.api.Debug;
import krash220.xbob.game.api.DynamicCrosshairCompat;
import krash220.xbob.game.api.DynamicCrosshairRenderer;
import net.minecraft.client.gui.GuiGraphics;

@Pseudo
@Mixin(targets = "mod.crend.libbamboo.render.CustomFramebufferRenderer", remap = false)
public class CustomFramebufferRendererMixin {

    @Inject(method = "draw", at = @At("HEAD"), require = 0, remap = false)
    private static void xbob$beforeDraw(GuiGraphics graphics, CallbackInfo ci) {
        boolean armedNow = DynamicCrosshairRenderer.isArmed();
        boolean spritePose = DynamicCrosshairCompat.isSpritePose();

        Debug.count(armedNow ? "dc.framebuffer.armed" : "dc.framebuffer.unarmed");
        Debug.throttled("dc.framebuffer.draw", "CustomFramebufferRenderer.draw(): armed={} -> {}", armedNow, armedNow ? (spritePose ? "spritePose mode: transform skipped here, it is applied to the crosshair sprite inside this framebuffer" : "bob transform APPLIED to the DynamicCrosshair crosshair") : "no transform (this draw call will not bob)");

        if (!armedNow) {
            return;
        }

        if (spritePose) {
            Debug.count("dc.framebuffer.spritePose.skip");

            return;
        }

        MainMod.preDynamicCrosshair(graphics.pose());

        if (Debug.ENABLED) {
            var matrix = graphics.pose().last().pose();

            Debug.count("dc.framebuffer.transformed");
            Debug.track("dc.framebuffer.pose", String.format("(%.3f, %.3f)", matrix.m30(), matrix.m31()));
            Debug.throttled("dc.framebuffer.pose", "libbamboo reads pose translation ({}, {}) - this exact matrix is baked into the framebuffer quad, so this is how far the crosshair image is moved on screen", matrix.m30(), matrix.m31());
        }
    }

    @Inject(method = "draw", at = @At("RETURN"), require = 0, remap = false)
    private static void xbob$afterDraw(GuiGraphics graphics, CallbackInfo ci) {
        if (DynamicCrosshairRenderer.isArmed() && !DynamicCrosshairCompat.isSpritePose()) {
            Debug.count("dc.framebuffer.pop");

            MainMod.postDynamicCrosshair(graphics.pose());
        }
    }
}
