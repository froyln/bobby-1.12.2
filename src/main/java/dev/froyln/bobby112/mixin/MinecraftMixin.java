package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.ChunkCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public WorldClient world;

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void bobby112$captureDepartingWorld(WorldClient nextWorld, String message, CallbackInfo ci) {
        if (world != null && world != nextWorld) {
            ChunkCache.captureRemaining(world);
        }
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void bobby112$finishSaves(CallbackInfo ci) {
        ChunkCache.flushPendingSaves();
    }
}
