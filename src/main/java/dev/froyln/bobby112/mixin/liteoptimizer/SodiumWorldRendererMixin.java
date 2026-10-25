package dev.froyln.bobby112.mixin.liteoptimizer;

import dev.froyln.bobby112.cache.ChunkCache;
import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.entity.Entity;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererMixin {
    @Inject(method = "onChunkAdded", at = @At("HEAD"), cancellable = true)
    private void bobby112$keepExistingColumn(int x, int z, CallbackInfo ci) {
        SodiumWorldRendererAccessors self = (SodiumWorldRendererAccessors) this;
        if (ChunkCache.isEnabled() && self.bobby112$loadedChunks().contains(ChunkPos.asLong(x, z))) {
            ci.cancel();
        }
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"), cancellable = true)
    private void bobby112$keepCachedMesh(int x, int z, CallbackInfo ci) {
        SodiumWorldRendererAccessors self = (SodiumWorldRendererAccessors) this;
        Minecraft client = self.bobby112$client();
        WorldClient world = self.bobby112$world();
        Entity camera = RenderChunkLookup.camera(client);
        if (world != null && world == client.world && camera != null
                && ChunkCache.withinDistance(ChunkPos.asLong(x, z),
                MathHelper.floor(camera.posX) >> 4, MathHelper.floor(camera.posZ) >> 4,
                client.gameSettings.renderDistanceChunks + 1)
                && ChunkCache.get(world, x, z) != null) {
            ci.cancel();
        }
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void bobby112$syncCachedColumns(CallbackInfo ci) {
        SodiumWorldRendererAccessors self = (SodiumWorldRendererAccessors) this;
        WorldClient world = self.bobby112$world();
        Minecraft client = self.bobby112$client();
        Entity camera = RenderChunkLookup.camera(client);
        if (world == null || camera == null) {
            return;
        }

        LongSet loaded = self.bobby112$loadedChunks();
        int centerX = MathHelper.floor(camera.posX) >> 4;
        int centerZ = MathHelper.floor(camera.posZ) >> 4;
        int distance = client.gameSettings.renderDistanceChunks + 1;
        ChunkProviderClient provider = world.getChunkProvider();

        for (long pos : loaded.toLongArray()) {
            int x = ChunkCache.chunkX(pos);
            int z = ChunkCache.chunkZ(pos);
            if (provider.getLoadedChunk(x, z) == null
                    && (!ChunkCache.withinDistance(pos, centerX, centerZ, distance)
                    || ChunkCache.get(world, x, z) == null)) {
                self.bobby112$removeChunk(x, z);
                ChunkCache.invalidateColumn(world, x, z);
            }
        }

        for (long pos : ChunkCache.positions(world)) {
            int x = ChunkCache.chunkX(pos);
            int z = ChunkCache.chunkZ(pos);
            if (ChunkCache.withinDistance(pos, centerX, centerZ, distance)
                    && provider.getLoadedChunk(x, z) == null && !loaded.contains(pos)) {
                if (ChunkCache.get(world, x, z) != null) {
                    self.bobby112$addChunk(x, z);
                    ChunkCache.invalidateColumn(world, x, z);
                }
            }
        }
    }
}
