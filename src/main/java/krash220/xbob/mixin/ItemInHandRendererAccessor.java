package krash220.xbob.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {

    @Accessor("mainHandHeight")
    float xbob$getMainHandHeight();

    @Accessor("oMainHandHeight")
    float xbob$getPrevMainHandHeight();

    @Accessor("mainHandItem")
    ItemStack xbob$getMainHandItem();
}
