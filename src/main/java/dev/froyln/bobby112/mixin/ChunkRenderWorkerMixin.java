package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkRenderWorker.class)
public abstract class ChunkRenderWorkerMixin {
    @Redirect(method = "isChunkExisting", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk bobby112$renderNeighbor(World world, int x, int z) {
        if (world instanceof WorldClient) {
            return RenderChunkLookup.getCachedOrLive(world, x, z, ((WorldClient) world).getChunkProvider());
        }
        return world.getChunk(x, z);
    }
}
