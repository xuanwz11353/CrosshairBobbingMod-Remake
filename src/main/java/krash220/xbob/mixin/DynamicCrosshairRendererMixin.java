package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import krash220.xbob.game.api.Debug;
import krash220.xbob.game.api.DynamicCrosshairCompat;
import krash220.xbob.game.api.DynamicCrosshairRenderer;
import net.minecraft.client.gui.GuiGraphics;

@Pseudo
@Mixin(targets = "mod.crend.dynamiccrosshair.render.CrosshairRenderer", remap = false)
public class DynamicCrosshairRendererMixin {

    @Inject(method = "wrapRender", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void xbob$takeover(GuiGraphics graphics, int x, int y, Runnable pre, Runnable post, CallbackInfo ci) {
        Debug.count("dc.wrapRender.hit");
        Debug.verbose("DynamicCrosshair CrosshairRenderer.wrapRender() reached at ({}, {})", x, y);

        if (!DynamicCrosshairCompat.shouldHandleCrosshair()) {
            Debug.count("dc.wrapRender.passthrough");
            Debug.throttled("dc.wrapRender.passthrough", "wrapRender was reached but shouldHandleCrosshair()=false -> DynamicCrosshair renders the crosshair itself, so xbob cannot transform it (crosshair will not bob)");

            return;
        }

        Debug.count("dc.wrapRender.takeover");
        Debug.verbose("DynamicCrosshair wrapRender cancelled, xbob takes over the crosshair rendering");

        ci.cancel();

        DynamicCrosshairRenderer.render(graphics, x, y);
    }
}
