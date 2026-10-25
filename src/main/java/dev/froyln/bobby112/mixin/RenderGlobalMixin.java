package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.ChunkCache;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    @Inject(method = "setWorldAndLoadRenderers", at = @At("RETURN"))
    private void bobby112$resetCacheForWorld(WorldClient world, CallbackInfo ci) {
        ChunkCache.begin(world);
    }
}
