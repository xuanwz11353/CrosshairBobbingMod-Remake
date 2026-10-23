package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import krash220.xbob.game.api.bus.PlayerBus;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@Mixin(Entity.class)
public class EntityHurtMixin {

    @Inject(method = "hurtOrSimulate", at = @At("HEAD"))
    private void xbob$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LivingEntity living) {
            PlayerBus.onAttack(living, source, amount);
        }
    }
}
