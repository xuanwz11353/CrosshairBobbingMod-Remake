package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import krash220.xbob.game.api.bus.PlayerBus;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void xbob$afterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PlayerBus.onBreakBlock();
        }
    }
}
