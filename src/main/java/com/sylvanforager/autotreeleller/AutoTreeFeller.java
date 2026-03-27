package com.sylvanforager.autotreeleller;

import com.sylvanforager.autotreeleller.sequencer.BreakSequencer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTreeFeller implements ClientModInitializer {
 public static final Logger LOGGER = LoggerFactory.getLogger("auto-tree-feller");
 public static BreakSequencer breakSequencer;
 public static KeyBinding toggleKey;
 private static boolean rKeyPressed = false;

 @Override
 public void onInitializeClient() {
 breakSequencer = new BreakSequencer();

 // Use GLFW polling for 1.21.11
 toggleKey = new KeyBinding(
 "key.auto-tree-feller.toggle",
 InputUtil.Type.KEYSYM,
 GLFW.GLFW_KEY_R,
 KeyBinding.Category.MISC
 );

 LOGGER.info("[AutoTreeFeller] Initialized");
 }

 public static void onClientTick(MinecraftClient client) {
 if (client.player == null || client.world == null) return;

 // Poll toggle key with GLFW
 long windowHandle = client.getWindow().getHandle();
 boolean isRPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

 if (isRPressed && !rKeyPressed) {
 breakSequencer.toggle();
 }
 rKeyPressed = isRPressed;
 toggleKey.setPressed(isRPressed);

 if (breakSequencer.isActive()) {
 breakSequencer.tick(client);
 }
 }
}