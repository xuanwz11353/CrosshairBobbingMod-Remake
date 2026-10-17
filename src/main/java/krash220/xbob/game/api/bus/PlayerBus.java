package krash220.xbob.game.api.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import krash220.xbob.game.api.Debug;
import krash220.xbob.mixin.LivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class PlayerBus {

    private static List<Consumer<Float>> attack = new ArrayList<>();

    public static void registerAttack(Consumer<Float> attack) {
        PlayerBus.attack.add(attack);
    }

    @SuppressWarnings("resource")
    public static void onAttack(LivingEntity entity, DamageSource source, float damage) {
        if (entity.level().isClientSide) {
            if (!entity.isInvulnerableTo(source) && !entity.isDeadOrDying()) {
                if (source.getEntity() == Minecraft.getInstance().player) {
                    damage = (source.getDirectEntity() == source.getEntity() ? damage : 1.0f) * (((LivingEntityAccessor) entity).callIsDamageSourceBlocked(source) ?  0 : 1);

                    Debug.count("xbob.attack.detected");
                    Debug.track("lastAttackDamage", damage);

                    for (Consumer<Float> handler : attack) {
                        handler.accept(damage);
                    }
                }
            }
        }
    }

    public static void onBreakBlock() {
        for (Consumer<Float> handler : attack) {
            handler.accept(0.6f);
        }
    }
}