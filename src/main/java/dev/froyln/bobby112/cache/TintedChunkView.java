package dev.froyln.bobby112.cache;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class TintedChunkView extends net.minecraft.world.ChunkCache {
    private final boolean tinted;
    private final float gamma;

    public TintedChunkView(World world, BlockPos from, BlockPos to, int subtract) {
        super(world, from, to, subtract);
        tinted = CachedTerrainTint.applies(world, (from.getX() + 1) >> 4, (from.getZ() + 1) >> 4);
        gamma = net.minecraft.client.Minecraft.getMinecraft().gameSettings.gammaSetting;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        int light = super.getCombinedLight(pos, lightValue);
        if (!tinted) return light;
        return CachedTerrainTint.shade(light, gamma);
    }
}
