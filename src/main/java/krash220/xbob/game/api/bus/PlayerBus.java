package krash220.xbob.game.api.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class PlayerBus {

    private static final List<Consumer<Float>> ATTACK = new ArrayList<>();

    public static void registerAttack(Consumer<Float> attack) {
        ATTACK.add(attack);
    }

    public static void onAttack(LivingEntity entity, DamageSource source, float damage) {
        if (entity.level() != null && entity.level().isClientSide()) {
            if (!entity.isDeadOrDying()) {
                if (source.getEntity() == Minecraft.getInstance().player) {
                    damage = (source.getDirectEntity() == source.getEntity() ? damage : 1.0f) * (entity.isBlocking() ? 0.0f : 1.0f);

                    for (Consumer<Float> handler : ATTACK) {
                        handler.accept(damage);
                    }
                }
            }
        }
    }

    public static void onBreakBlock() {
        for (Consumer<Float> handler : ATTACK) {
            handler.accept(0.6f);
        }
    }
}
