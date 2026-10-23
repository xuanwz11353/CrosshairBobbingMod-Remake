package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import krash220.xbob.game.api.DynamicCrosshairCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Keeps DynamicCrosshair's optional "fix centered crosshair" feature compatible with the crosshair
 * pose this mod pushes: DynamicCrosshair resets the pose translation to an absolute value, which
 * would throw away the bobbing transform, so the reset is re-implemented as a relative offset.
 *
 * <p>Without "fix centered crosshair" (the DynamicCrosshair default) nothing here runs and
 * DynamicCrosshair simply inherits the bobbing pose.</p>
 */
@Pseudo
@Mixin(targets = "mod.crend.dynamiccrosshair.render.CrosshairRenderer", remap = false)
public class DynamicCrosshairCompatMixin {

    @Inject(method = "fixCenteredCrosshairPre", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void xbob$composeFixCenteredPre(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        if (!DynamicCrosshairCompat.isTransforming()) {
            return;
        }

        DynamicCrosshairCompat.onFixHook();
        DynamicCrosshairCompat.applyRelativeCenter(graphics, x, y);
        ci.cancel();
    }
}
