package dev.froyln.bobby112.mixin.liteoptimizer;

import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection", remap = false)
public abstract class ClonedChunkSectionMixin {
    @Redirect(method = "init", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;", remap = true))
    private Chunk bobby112$renderOnlyChunk(World world, int x, int z) {
        if (world instanceof WorldClient) {
            WorldClient clientWorld = (WorldClient) world;
            return RenderChunkLookup.getCachedOrLive(world, x, z, clientWorld.getChunkProvider());
        }
        return world.getChunk(x, z);
    }

    @Redirect(method = "init", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBiome(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/biome/Biome;", remap = true))
    private Biome bobby112$cachedBiome(World world, BlockPos pos) {
        return bobby112$renderOnlyChunk(world, pos.getX() >> 4, pos.getZ() >> 4)
                .getBiome(pos, world.getBiomeProvider());
    }
}
