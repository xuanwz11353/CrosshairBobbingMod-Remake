package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("bobHurt")
    void xbob$bobHurt(CameraRenderState state, PoseStack pose);

    @Invoker("bobView")
    void xbob$bobView(CameraRenderState state, PoseStack pose);

    @Accessor("spinningEffectTime")
    float xbob$getSpinningEffectTime();

    @Accessor("spinningEffectSpeed")
    float xbob$getSpinningEffectSpeed();
}
