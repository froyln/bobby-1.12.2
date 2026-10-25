package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkRenderWorker.class)
public abstract class ChunkRenderWorkerMixin {
    @Redirect(method = "processTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderWorker;isChunkExisting(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/World;)Z"))
    private boolean bobby112$allowCachedBoundary(ChunkRenderWorker worker, BlockPos neighbor, World world, ChunkCompileTaskGenerator task) {
        if (world instanceof WorldClient) {
            BlockPos center = task.getRenderChunk().getPosition();
            return RenderChunkLookup.hasRenderNeighbor(world, ((WorldClient) world).getChunkProvider(),
                    center.getX() >> 4, center.getZ() >> 4, neighbor.getX() >> 4, neighbor.getZ() >> 4);
        }
        return !world.getChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4).isEmpty();
    }
}
