package dev.froyln.bobby112.mixin.liteoptimizer;

import dev.froyln.bobby112.cache.ChunkCache;
import dev.froyln.bobby112.cache.CacheRenderDistance;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    @Redirect(method = {"updateChunks", "initRenderer"}, remap = false, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I", remap = true))
    private int bobby112$terrainDistance(GameSettings settings) {
        return CacheRenderDistance.get(settings.renderDistanceChunks);
    }

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
                CacheRenderDistance.get(client.gameSettings.renderDistanceChunks) + 1)
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
        int distance = CacheRenderDistance.get(client.gameSettings.renderDistanceChunks) + 1;
        ChunkProviderClient provider = world.getChunkProvider();
        LongSet desired = RenderChunkLookup.connectedColumns(ChunkCache.positions(world), centerX, centerZ, distance);

        for (long pos : loaded.toLongArray()) {
            int x = ChunkCache.chunkX(pos);
            int z = ChunkCache.chunkZ(pos);
            if (provider.getLoadedChunk(x, z) == null
                    && !desired.contains(pos)) {
                self.bobby112$removeChunk(x, z);
                ChunkCache.invalidateColumn(world, x, z);
            }
        }

        for (long pos : desired) {
            int x = ChunkCache.chunkX(pos);
            int z = ChunkCache.chunkZ(pos);
            if (provider.getLoadedChunk(x, z) == null && !loaded.contains(pos)) {
                self.bobby112$addChunk(x, z);
                ChunkCache.invalidateColumn(world, x, z);
            }
        }
    }
}
