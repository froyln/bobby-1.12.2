package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.RenderChunkLookup;
import dev.froyln.bobby112.cache.TintedChunkView;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderChunk.class)
public abstract class RenderChunkMixin {
    @Redirect(method = "rebuildWorldView", at = @At(value = "NEW", target = "net/minecraft/world/ChunkCache"))
    private ChunkCache bobby112$useCachedChunks(World world, BlockPos from, BlockPos to, int subtract) {
        try (RenderChunkLookup.ChunkCacheScope ignored = RenderChunkLookup.beginCacheBuild()) {
            return new TintedChunkView(world, from, to, subtract);
        }
    }
}
