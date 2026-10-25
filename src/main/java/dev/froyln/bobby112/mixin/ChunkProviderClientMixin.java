package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.ChunkCache;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderClient.class)
public abstract class ChunkProviderClientMixin {
    @Shadow public abstract Chunk getLoadedChunk(int x, int z);
    @Shadow private net.minecraft.world.World world;

    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void bobby112$preferLiveChunk(int x, int z, CallbackInfoReturnable<Chunk> ci) {
        ChunkCache.liveLoaded(world, x, z);
    }

    @Inject(method = "unloadChunk", at = @At("HEAD"))
    private void bobby112$cacheBeforeUnload(int x, int z, CallbackInfo ci) {
        Chunk chunk = getLoadedChunk(x, z);
        if (chunk != null && world instanceof WorldClient) {
            ChunkCache.store((WorldClient) world, chunk);
        }
    }
}
