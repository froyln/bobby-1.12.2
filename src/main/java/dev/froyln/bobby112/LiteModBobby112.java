package dev.froyln.bobby112;

import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.ExposableOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import java.io.File;
import java.io.FileReader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import dev.froyln.bobby112.cache.ChunkCache;
import dev.froyln.bobby112.cache.CacheRenderDistance;
import dev.froyln.bobby112.cache.RenderChunkLookup;
import net.minecraft.entity.Entity;

@ExposableOptions(filename = "bobby112.json")
public class LiteModBobby112 implements Tickable {
    public static final String VERSION = "@MOD_VERSION@";
    private static final Gson CONFIG_READER = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    @Expose
    public boolean enabled = true;

    @Expose
    public int cacheRenderDistance = 16;

    private File configFile;
    private long configModified;
    private KeyBinding toggleKey;

    @Override
    public String getName() {
        return "Bobby 1.12.2";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void init(File configPath) {
        ChunkCache.initialize(LiteLoader.getGameDirectory());
        configFile = new File(LiteLoader.getCommonConfigFolder(), "bobby112.json");
        reloadConfig();
        toggleKey = new KeyBinding("Toggle Bobby terrain cache", Keyboard.KEY_NONE, "Bobby 1.12.2");
        LiteLoader.getInput().registerKeyBinding(toggleKey);
        System.out.println("[Bobby 1.12.2] " + VERSION + " loaded");
    }

    @Override
    public void onTick(Minecraft client, float partialTicks, boolean inGame, boolean clock) {
        if (clock) {
            reloadConfig();
            while (toggleKey.isPressed()) {
                if (inGame && client.currentScreen == null) {
                    enabled = !enabled;
                    LiteLoader.getInstance().writeConfig(this);
                    client.ingameGUI.setOverlayMessage("Bobby terrain cache: " + (enabled ? "enabled" : "disabled"), false);
                }
            }
            CacheRenderDistance.configure(cacheRenderDistance);
            boolean wasEnabled = ChunkCache.isEnabled();
            ChunkCache.setEnabled(enabled);
            if (wasEnabled != enabled && client.world != null) {
                client.renderGlobal.loadRenderers();
            }
        }
        WorldClient world = client.world;
        Entity camera = RenderChunkLookup.camera(client);
        if (enabled && world != null && camera != null) {
            ChunkCache.scanAround(world, MathHelper.floor(camera.posX) >> 4,
                    MathHelper.floor(camera.posZ) >> 4, CacheRenderDistance.get(client.gameSettings.renderDistanceChunks) + 2);
        }
    }

    private void reloadConfig() {
        if (configFile == null || !configFile.isFile() || configFile.lastModified() <= configModified) {
            return;
        }
        configModified = configFile.lastModified();
        try (FileReader reader = new FileReader(configFile)) {
            LiteModBobby112 config = CONFIG_READER.fromJson(reader, LiteModBobby112.class);
            if (config != null) {
                enabled = config.enabled;
                cacheRenderDistance = config.cacheRenderDistance;
            }
        } catch (Exception e) {
            System.err.println("[Bobby 1.12.2] Could not reload config: " + e.getMessage());
        }
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
    }
}
