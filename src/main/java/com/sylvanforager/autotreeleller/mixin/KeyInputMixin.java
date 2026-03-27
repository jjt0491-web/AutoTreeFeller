package com.sylvanforager.autotreeleller.mixin;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.sylvanforager.autotreeleller.AutoTreeFeller;

@Mixin(MinecraftClient.class)
public class KeyInputMixin {
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        
        if (client.getWindow() == null) return;
        
        // Skip if in a menu/screen
        if (client.currentScreen != null) return;
        
        long handle = client.getWindow().getHandle();
        
        // R key to toggle - use GLFW polling directly
        int rState = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_R);
        if (rState == GLFW.GLFW_PRESS && !AutoTreeFeller.rKeyPressed) {
            AutoTreeFeller.rKeyPressed = true;
            if (AutoTreeFeller.breakSequencer != null) {
                handleToggle(client);
            }
        } else if (rState == GLFW.GLFW_RELEASE) {
            AutoTreeFeller.rKeyPressed = false;
        }
        
        // Each tick, run sequencer if active - use new state machine tick
        if (AutoTreeFeller.breakSequencer != null && AutoTreeFeller.breakSequencer.isActive()) {
            AutoTreeFeller.breakSequencer.tick(client);
        }
    }
    
    private void handleToggle(MinecraftClient client) {
        if (AutoTreeFeller.breakSequencer == null) return;
        
        // Use the new toggle method
        AutoTreeFeller.breakSequencer.toggle();
        
        if (AutoTreeFeller.breakSequencer.isActive()) {
            AutoTreeFeller.LOGGER.info("[KEY] AutoTreeFeller Enabled");
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("§aAutoTreeFeller Enabled"), true);
            }
        } else {
            AutoTreeFeller.LOGGER.info("[KEY] AutoTreeFeller Disabled");
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("§cAutoTreeFeller Disabled"), true);
            }
        }
    }
}