package dev.froyln.bobby112.config;

import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.modconfig.AbstractConfigPanel;
import com.mumfrey.liteloader.modconfig.ConfigPanelHost;
import dev.froyln.bobby112.LiteModBobby112;
import dev.froyln.bobby112.cache.CacheProfile;
import dev.froyln.bobby112.cache.IntegratedServerDistance;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public final class BobbyConfigPanel extends AbstractConfigPanel {
    private LiteModBobby112 mod;
    private boolean enabled;
    private boolean fallback;
    private int distance;
    private int serverDistance;
    private GuiButton serverValue;
    private GuiButton serverDecrease;
    private GuiButton serverIncrease;
    private GuiButton decrease;
    private GuiButton increase;
    private GuiButton value;
    private GuiTextField profile;
    private String profileDraft;

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
        profileDraft = mod.cacheProfile;
        serverDistance = IntegratedServerDistance.normalize(mod.integratedServerDistance);
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
        addLabel(8, 10, 126, width, 12, 0xFFFFFF, "Cache profile (next connection)");
        boolean focused = profile != null && profile.isFocused();
        // LiteLoader's text-field helper retains old fields when options are rebuilt.
        profile = new GuiTextField(9, mc.fontRenderer, 12, 146, width - 4, 18);
        profile.setMaxStringLength(64);
        profile.setValidator(CacheProfile::isValid);
        profile.setText(profileDraft);
        profile.setFocused(focused);
        addLabel(10, 10, 178, width, 12, 0xFFFFFF, "Singleplayer live distance");
        serverDecrease = addControl(new GuiButton(11, 10, 194, 30, 20, "-"), button -> {
            serverDistance = serverDistance <= 3 ? 0 : serverDistance - 1;
            updateServerDistance();
        });
        serverValue = addControl(new GuiButton(12, 44, 194, width - 68, 20, ""), null);
        serverValue.enabled = false;
        serverIncrease = addControl(new GuiButton(13, width - 20, 194, 30, 20, "+"), button -> {
            serverDistance = serverDistance == 0 ? 3 : serverDistance + 1;
            updateServerDistance();
        });
        updateServerDistance();
        List<String> help = mc.fontRenderer.listFormattedStringToWidth(
                "Blank profile keeps your existing cache. Names: letters, numbers, spaces, . _ -. "
                + "Reconnect to apply a new profile. Auto live distance follows Minecraft options. "
                + "Overriding changes singleplayer loading and simulation, including LAN guests. "
                + "Fallback reads matching saves/bobby-fallback. Terrain is visual only; saved on close.", width);
        addLabel(7, 10, 228, width, help.size() * 10, 0xBBBBBB, help.toArray(new String[0]));
    }

    private void updateServerDistance() {
        serverDecrease.enabled = serverDistance != 0;
        serverIncrease.enabled = serverDistance < 32;
        serverValue.displayString = serverDistance == 0 ? "Auto" : serverDistance + " chunks";
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
            profileDraft = profile.getText();
            clearOptions();
            buildControls(host);
        }
    }

    @Override
    public void drawPanel(ConfigPanelHost host, int mouseX, int mouseY, float partialTicks) {
        super.drawPanel(host, mouseX, mouseY, partialTicks);
        if (profile != null) profile.drawTextBox();
    }

    @Override
    public void onTick(ConfigPanelHost host) {
        super.onTick(host);
        if (profile != null) profile.updateCursorCounter();
    }

    @Override
    public void mousePressed(ConfigPanelHost host, int mouseX, int mouseY, int button) {
        super.mousePressed(host, mouseX, mouseY, button);
        if (profile != null) profile.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void keyPressed(ConfigPanelHost host, char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) super.keyPressed(host, character, key);
        else if (profile != null) profile.textboxKeyTyped(character, key);
    }

    @Override
    public void onPanelHidden() {
        if (mod != null) {
            mod.enabled = enabled;
            mod.cacheRenderDistance = distance;
            mod.fallbackEnabled = fallback;
            mod.integratedServerDistance = serverDistance;
            mod.setCacheProfile(profile.getText());
            LiteLoader.getInstance().writeConfig(mod);
        }
    }
}
