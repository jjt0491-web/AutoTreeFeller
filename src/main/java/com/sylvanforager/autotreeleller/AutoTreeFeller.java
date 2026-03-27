package com.sylvanforager.autotreeleller;

import com.sylvanforager.autotreeleller.gui.SylvanHubScreen;
import com.sylvanforager.autotreeleller.module.ForagingModule;
import com.sylvanforager.autotreeleller.sequencer.BreakSequencer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTreeFeller implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("auto-tree-feller");
    public static BreakSequencer breakSequencer;

    /** R opens the SylvanHub GUI. */
    private static final int GUI_KEY = GLFW.GLFW_KEY_R;

    private static boolean guiKeyWasDown  = false;
    private static boolean macroKeyWasDown = false;

    @Override
    public void onInitializeClient() {
        breakSequencer = new BreakSequencer();
        LOGGER.info("[AutoTreeFeller] Initialized — R to open GUI");
    }

    public static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        long win = client.getWindow().getHandle();

        // ── GUI open key (INSERT) ─────────────────────────────────────────
        boolean guiDown = GLFW.glfwGetKey(win, GUI_KEY) == GLFW.GLFW_PRESS;
        if (guiDown && !guiKeyWasDown && client.currentScreen == null) {
            client.setScreen(new SylvanHubScreen());
        }
        guiKeyWasDown = guiDown;

        // ── Macro toggle key (configurable) ──────────────────────────────
        // Only the Fig tree type is functional right now.
        if (ForagingModule.getSelectedTree() != ForagingModule.TreeType.FIG) {
            if (breakSequencer.isActive()) {
                breakSequencer.toggle(); // force off if non-Fig selected
            }
            macroKeyWasDown = GLFW.glfwGetKey(win, ForagingModule.getActivationKey()) == GLFW.GLFW_PRESS;
            return;
        }

        // Don't poll macro key while GUI is open or being typed into
        if (client.currentScreen != null) {
            macroKeyWasDown = false;
            return;
        }

        boolean macroDown = GLFW.glfwGetKey(win, ForagingModule.getActivationKey()) == GLFW.GLFW_PRESS;
        if (macroDown && !macroKeyWasDown) {
            breakSequencer.toggle();
        }
        macroKeyWasDown = macroDown;

        if (breakSequencer.isActive()) {
            breakSequencer.tick(client);
        }
    }
}
