package dev.froyln.bobby112.config;

import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel;
import com.mumfrey.liteloader.modconfig.ConfigPanelHost;
import dev.froyln.bobby112.LiteModBobby112;
import java.util.List;
import net.minecraft.client.gui.GuiButton;

public final class BobbyConfigPanel extends AbstractConfigPanel {
    private LiteModBobby112 mod;
    private boolean enabled;
    private boolean fallback;
    private int distance;
    private GuiButton decrease;
    private GuiButton increase;
    private GuiButton value;

    @Override
    public String getPanelTitle() {
        return "Bobby terrain cache";
    }

    @Override
    protected void addOptions(ConfigPanelHost host) {
        mod = host.getMod();
        enabled = mod.enabled;
        fallback = mod.fallbackEnabled;
        distance = Math.max(2, Math.min(32, mod.cacheRenderDistance));
        buildControls(host);
    }

    private void buildControls(ConfigPanelHost host) {
        int width = Math.max(140, Math.min(220, host.getWidth() - 20));
        addControl(new GuiButton(1, 10, 10, width, 20, "Terrain cache: " + state(enabled)), button -> {
            enabled = !enabled;
            button.displayString = "Terrain cache: " + state(enabled);
        });
        addLabel(2, 10, 42, width, 12, 0xFFFFFF, "Cached terrain distance");
        decrease = addControl(new GuiButton(3, 10, 58, 30, 20, "-"), button -> {
            distance--;
            updateDistance();
        });
        value = addControl(new GuiButton(4, 44, 58, width - 68, 20, ""), null);
        value.enabled = false;
        increase = addControl(new GuiButton(5, width - 20, 58, 30, 20, "+"), button -> {
            distance++;
            updateDistance();
        });
        updateDistance();
        addControl(new GuiButton(6, 10, 92, width, 20, "Fallback world: " + state(fallback)), button -> {
            fallback = !fallback;
            button.displayString = "Fallback world: " + state(fallback);
        });
        List<String> help = mc.fontRenderer.listFormattedStringToWidth(
                "Live distance stays in Minecraft options. Fallback reads a matching saves/bobby-fallback world. "
                + "Terrain is visual only. Changes are saved on close.", width);
        addLabel(7, 10, 126, width, help.size() * 10, 0xBBBBBB, help.toArray(new String[0]));
    }

    private void updateDistance() {
        decrease.enabled = distance > 2;
        increase.enabled = distance < 32;
        value.displayString = distance + " chunks";
    }

    private static String state(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Override
    public void onPanelResize(ConfigPanelHost host) {
        if (mod != null) {
            clearOptions();
            buildControls(host);
        }
    }

    @Override
    public void onPanelHidden() {
        if (mod != null) {
            mod.enabled = enabled;
            mod.cacheRenderDistance = distance;
            mod.fallbackEnabled = fallback;
            LiteLoader.getInstance().writeConfig(mod);
        }
    }
}
