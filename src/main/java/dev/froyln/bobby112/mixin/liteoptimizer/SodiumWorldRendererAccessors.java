package dev.froyln.bobby112.mixin.liteoptimizer;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public interface SodiumWorldRendererAccessors {
    @Accessor("loadedChunkPositions")
    LongSet bobby112$loadedChunks();

    @Accessor("client")
    Minecraft bobby112$client();

    @Accessor("world")
    WorldClient bobby112$world();

    @Invoker("onChunkAdded")
    void bobby112$addChunk(int x, int z);

    @Invoker("onChunkRemoved")
    void bobby112$removeChunk(int x, int z);
}
