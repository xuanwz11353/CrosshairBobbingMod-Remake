package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import krash220.xbob.MainMod;
import krash220.xbob.game.api.Debug;
import krash220.xbob.game.api.DynamicCrosshairCompat;
import krash220.xbob.game.api.DynamicCrosshairRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Diagnostic only - traces every crosshair/obstruction sprite that is drawn.
 *
 * <p>The log line reports the pose translation that is active when the sprite is drawn together
 * with the bob offset xbob computed for that frame, which shows whether the sprite is part of the
 * bobbing transform or drawn outside of it (see {@link Debug#traceSprite}).</p>
 *
 * <p>Disable with {@code -Dxbob.debug.sprites=false}; the tracing itself filters out every sprite
 * whose path does not contain "crosshair" or "obstruct".</p>
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    /** True while this mixin has pushed the bob transform for a DynamicCrosshair sprite (spritePose mode). */
    @Unique
    private boolean xbob$pushedSpritePose;

    @Shadow
    public abstract PoseStack pose();

    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V", at = @At("HEAD"))
    private void xbob$traceBlit(ResourceLocation sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, CallbackInfo ci) {
        Debug.traceSprite("blit", sprite, x, y, width, height, this.pose());

        this.xbob$pushedSpritePose = xbob$pushSpritePose(sprite);
    }

    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V", at = @At("RETURN"))
    private void xbob$untraceBlit(ResourceLocation sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, CallbackInfo ci) {
        xbob$popSpritePose();
    }

    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", at = @At("HEAD"))
    private void xbob$traceBlitSprite(ResourceLocation sprite, int x, int y, int width, int height, CallbackInfo ci) {
        Debug.traceSprite("blitSprite", sprite, x, y, width, height, this.pose());
    }

    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V", at = @At("HEAD"))
    private void xbob$traceBlitSpriteUv(ResourceLocation sprite, int u0, int v0, int u1, int v1, int x, int y, int width, int height, CallbackInfo ci) {
        Debug.traceSprite("blitSprite", sprite, x, y, width, height, this.pose());
    }

    /**
     * spritePose mode only: applies xbob's bob transform around the DynamicCrosshair crosshair sprite
     * itself instead of around libbamboo's framebuffer blit (see {@link DynamicCrosshairCompat#isSpritePose()}).
     *
     * <p>Only the outer sprite draw is transformed: if a hooked overload delegates to another one the
     * nested call would otherwise translate the same sprite twice.</p>
     */
    @Unique
    private boolean xbob$pushSpritePose(ResourceLocation sprite) {
        if (this.xbob$pushedSpritePose || !DynamicCrosshairCompat.isSpritePose() || !DynamicCrosshairRenderer.isArmed() || sprite == null) {
            return false;
        }

        if (!"dynamiccrosshair".equals(sprite.getNamespace()) || !sprite.getPath().startsWith("crosshair/")) {
            return false;
        }

        MainMod.preDynamicCrosshair(this.pose());

        Debug.count("dc.spritePose.push");
        Debug.track("dc.spritePose.sprite", sprite.toString());

        if (Debug.ENABLED) {
            var matrix = this.pose().last().pose();

            Debug.track("dc.spritePose.pose", String.format("(%.3f, %.3f)", matrix.m30(), matrix.m31()));
            Debug.throttled("dc.spritePose", "spritePose mode: crosshair sprite {} is drawn with the bob transform on the pose stack (pose translation ({}, {})) instead of on the framebuffer blit", sprite, matrix.m30(), matrix.m31());
        }

        return true;
    }

    @Unique
    private void xbob$popSpritePose() {
        if (!this.xbob$pushedSpritePose) {
            return;
        }

        this.xbob$pushedSpritePose = false;

        MainMod.postDynamicCrosshair(this.pose());

        Debug.count("dc.spritePose.pop");
    }
}
