package dev.froyln.bobby112.mixin;

import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow protected IChunkProvider chunkProvider;

    @Redirect(method = "getChunk(II)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk bobby112$renderOnlyChunk(IChunkProvider provider, int x, int z) {
        return RenderChunkLookup.get((World) (Object) this, x, z, provider);
    }
}
