package dev.froyln.bobby112.mixin.liteoptimizer;

import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class WorldSliceMixin {
    @Redirect(method = "prepare", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;", remap = true))
    private static Chunk bobby112$renderOnlyChunk(World world, int x, int z) {
        if (world instanceof WorldClient) {
            WorldClient clientWorld = (WorldClient) world;
            return RenderChunkLookup.getCachedOrLive(world, x, z, clientWorld.getChunkProvider());
        }
        return world.getChunk(x, z);
    }
}
