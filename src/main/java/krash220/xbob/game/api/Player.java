package krash220.xbob.game.api;

import krash220.xbob.mixin.CameraAccessor;
import krash220.xbob.mixin.ItemInHandRendererAccessor;
import krash220.xbob.mixin.LivingEntityAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

public class Player {

    private static Pose lastPose;
    private static float lastHandHeight;
    private static int lastSlot = -1;

    public static float spinProgress(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null && mc.player.isAutoSpinAttack()) {
            return Mth.clamp((20 - ((LivingEntityAccessor) mc.player).xbob$getAutoSpinAttackTicks() + partialTicks) / 20.0f, 0.0f, 1.0f);
        }

        return 0.0f;
    }

    public static float sneakProgress(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Entity entity = camera.entity();

        if (entity != null) {
            Pose pose = entity.getPose();

            if (pose != Pose.STANDING) {
                lastPose = pose;
            }

            if (entity.getPose() == Pose.STANDING && lastPose == Pose.CROUCHING || entity.getPose() == Pose.CROUCHING) {
                float standing = entity.getEyeHeight(Pose.STANDING);
                float crouching = entity.getEyeHeight(Pose.CROUCHING);

                float eyeHeight = ((CameraAccessor) camera).xbob$getEyeHeight();
                float eyeHeightOld = ((CameraAccessor) camera).xbob$getEyeHeightOld();

                return (Mth.lerp(partialTicks, eyeHeightOld, eyeHeight) - standing) / (crouching - standing);
            }
        }

        return 0.0f;
    }

    public static float swingProgress(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        LocalPlayer player = mc.player;

        if (player != null && camera.entity() == player && player.swinging) {
            return player.getAttackAnim(partialTicks);
        }

        return 1.0f;
    }

    private static boolean isUsing(LocalPlayer player) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        return camera.entity() == player && player.isUsingItem();
    }

    public static float usingItemProgress(float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && isUsing(player)) {
            ItemStack item = player.getUseItem();
            ItemUseAnimation animation = item.getUseAnimation();
            float f = item.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTicks + 1.0f);

            if (animation == ItemUseAnimation.BOW) {
                return Math.min(f / 20f, 1.0f);
            } else if (animation == ItemUseAnimation.SPEAR || animation == ItemUseAnimation.TRIDENT) {
                return Math.min(f / 10f, 1.0f);
            } else {
                return f / item.getUseDuration(player);
            }
        }

        return 1.0f;
    }

    public static float getItemShake(float partialTicks) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && isUsing(player)) {
            ItemStack item = player.getUseItem();
            float f = item.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTicks + 1.0f);
            float f0 = 0.1f;

            if (item.getUseAnimation() == ItemUseAnimation.CROSSBOW && !CrossbowItem.isCharged(item) && player.getUseItemRemainingTicks() > 0) {
                f0 = Math.min(f / CrossbowItem.getChargeDuration(item, player), 1.0f);
            } else if (item.getUseAnimation() == ItemUseAnimation.BOW) {
                f0 = f / 20.0f;
                f0 = (f0 * f0 + f0 * 2.0f) / 3.0f;
                f0 = Math.min(f0, 1.0f);
            } else if (item.getUseAnimation() == ItemUseAnimation.SPEAR) {
                f0 = Math.min(f / 10.0f, 1.0f);
            } else {
                return 0.0f;
            }

            return Mth.sin((f - 0.1f) * 1.3f) * (f0 - 0.1f);
        }

        return 0.0f;
    }

    public static ItemType getUsingType() {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && isUsing(player)) {
            ItemStack item = player.getUseItem();

            switch (item.getUseAnimation()) {
            case EAT:
            case DRINK:
                return ItemType.EATING;
            case BOW:
                return ItemType.BOW;
            case SPEAR:
            case TRIDENT:
                return ItemType.SPEAR;
            default:
                break;
            }

            if (item.getItem() == Items.CROSSBOW && !CrossbowItem.isCharged(item)) {
                return ItemType.CROSSBOW;
            }
        }

        return ItemType.NONE;
    }

    public static float changeItemProgress(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        ItemInHandRenderer renderer = mc.gameRenderer.itemInHandRenderer;
        ItemInHandRendererAccessor accessor = (ItemInHandRendererAccessor) renderer;
        Camera camera = mc.gameRenderer.getMainCamera();
        LocalPlayer player = mc.player;

        if (player != null && camera.entity() == player && !player.isPassenger() && !player.isUsingItem() && !player.swinging) {
            float height = (accessor.xbob$getMainHandHeight() - accessor.xbob$getPrevMainHandHeight()) * partialTicks + accessor.xbob$getPrevMainHandHeight();

            if (player.getInventory().getSelectedItem().getItem() == accessor.xbob$getMainHandItem().getItem() && lastSlot == player.getInventory().getSelectedSlot()) {
                lastHandHeight = Math.max(lastHandHeight, height);

                return lastHandHeight;
            } else {
                lastSlot = player.getInventory().getSelectedSlot();
                lastHandHeight = 0.0f;

                return height;
            }
        }

        lastHandHeight = 1.0f;
        return 1.0f;
    }

    public enum ItemType {
        NONE,
        EATING,
        BOW,
        SPEAR,
        CROSSBOW
    }
}
