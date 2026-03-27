package com.sylvanforager.autotreeleller.look;

import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import com.sylvanforager.autotreeleller.AutoTreeFeller;

/**
 * Bezier curve mouse movement - simulates human-like aiming.
 * Instance-based to preserve state between ticks.
 */
public class PlayerLookHelper {
    
    private float startYaw, startPitch;
    private float targetYaw, targetPitch;
    private float midYawOffset, midPitchOffset;
    private float progress = 1.0f; // 1.0 = done
    
    // Progress per tick - 0.25f completes in ~4 ticks for smooth response
    private static final float SPEED = 0.25f;
    
    /**
     * Start looking at a block - initializes Bezier curve.
     */
    public void startLookAt(PlayerEntity player, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        double targetX = pos.getX() + 0.5;
        double targetY = pos.getY() + 0.5;
        double targetZ = pos.getZ() + 0.5;
        
        Vec3d eyes = player.getEyePos();
        double dx = targetX - eyes.x;
        double dy = targetY - eyes.y;
        double dz = targetZ - eyes.z;
        double distHoriz = Math.sqrt(dx * dx + dz * dz);
        
        this.startYaw = player.getYaw();
        this.startPitch = player.getPitch();
        this.targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        this.targetPitch = (float)(Math.toDegrees(-Math.atan2(dy, distHoriz)));
        
        // Normalize target yaw to be close to current
        float yawDelta = this.targetYaw - this.startYaw;
        while (yawDelta > 180) yawDelta -= 360;
        while (yawDelta < -180) yawDelta += 360;
        this.targetYaw = this.startYaw + yawDelta;
        
        // Clamp pitch
        this.targetPitch = Math.max(-90, Math.min(90, this.targetPitch));

        // Calculate yaw range and pitch delta for midpoint randomization
        float yawRange = Math.abs(yawDelta);
        float pitchDelta = this.targetPitch - this.startPitch;

        // Remove midpoint randomness entirely for short moves, only apply for large ones
        float totalDelta = Math.abs(yawDelta) + Math.abs(pitchDelta);
        if (totalDelta < 30.0f) {
            // short move — no curve offset, just clean lerp
            this.midYawOffset = 0;
            this.midPitchOffset = 0;
        } else {
            // large move — subtle human arc
            this.midYawOffset = (float)(Math.random() * yawRange * 0.08 - yawRange * 0.04);
            this.midPitchOffset = (float)(Math.random() * Math.abs(pitchDelta) * 0.08 - Math.abs(pitchDelta) * 0.04);
        }

        // Skip curve for small angle changes (< 15 degrees total)
        if (totalDelta < 15.0f) {
            this.progress = 1.0f; // already locked, skip curve
        } else if (totalDelta < 30.0f) {
            // if already roughly facing this direction, start mid-curve for smooth transition
            if (Math.abs(yawDelta) < 20f && Math.abs(pitchDelta) < 20f) {
                this.progress = 0.3f; // already close — start mid-curve
            } else {
                this.progress = 0.0f;
            }
        } else {
            this.progress = 0.0f;
        }
        
        AutoTreeFeller.LOGGER.info("[LOOK] Started: start=(%.1f,%.1f) target=(%.1f,%.1f)", 
            startYaw, startPitch, targetYaw, targetPitch);
    }
    
    /**
     * Call every tick - advances along the Bezier curve.
     * @return true when locked on (curve complete)
     */
    public boolean tick(PlayerEntity player) {
        if (progress >= 1.0f) return true;
        
        progress = Math.min(1.0f, progress + SPEED);
        float t = progress;
        
        // Quadratic Bezier: B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
        float midYaw = (startYaw + targetYaw) / 2f + midYawOffset;
        float midPitch = (startPitch + targetPitch) / 2f + midPitchOffset;
        
        float newYaw = (1-t)*(1-t)*startYaw + 2*(1-t)*t*midYaw + t*t*targetYaw;
        float newPitch = (1-t)*(1-t)*startPitch + 2*(1-t)*t*midPitch + t*t*targetPitch;
        
        // Apply rotation
        player.setYaw(newYaw);
        player.setPitch(newPitch);
        
        // Also update client-side player for camera
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.setYaw(newYaw);
            client.player.setPitch(newPitch);
            client.player.headYaw = newYaw;
        }
        
        boolean locked = progress >= 1.0f;
        if (locked) {
            AutoTreeFeller.LOGGER.info("[LOOK] Locked on: (%.1f,%.1f)", newYaw, newPitch);
        }
        
        return locked;
    }
    
    /**
     * @return true if curve is complete
     */
    public boolean isLocked() {
        return progress >= 1.0f;
    }
    
    /**
     * Reset the look helper
     */
    public void reset() {
        progress = 1.0f;
    }
}