package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.ChunkCache;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void bobby112$finishSaves(CallbackInfo ci) {
        ChunkCache.flushPendingSaves();
    }
}
