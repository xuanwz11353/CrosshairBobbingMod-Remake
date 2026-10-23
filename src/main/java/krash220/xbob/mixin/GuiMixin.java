package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import krash220.xbob.game.api.bus.GuiBus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"))
    private void xbob$beforeExtractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        GuiBus.partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        GuiBus.pre(graphics);
    }

    @Inject(method = "extractCrosshair", at = @At("TAIL"))
    private void xbob$afterExtractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        GuiBus.post(graphics);
    }
}
