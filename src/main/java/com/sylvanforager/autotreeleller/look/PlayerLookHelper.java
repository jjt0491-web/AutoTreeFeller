package com.sylvanforager.autotreeleller.look;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PlayerLookHelper {
    private float startYaw, startPitch;
    private float targetYaw, targetPitch;
    private float progress = 1.0f;
    private float duration;
    private float overshootYaw, overshootPitch;
    private float microJitterX, microJitterY;
    private static final float BASE_DURATION = 3.5f;

    public void startLookAt(PlayerEntity player, BlockPos pos) {
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
        this.targetPitch = Math.max(-90, Math.min(90, this.targetPitch));

        float yawDelta = this.targetYaw - this.startYaw;
        while (yawDelta > 180) yawDelta -= 360;
        while (yawDelta < -180) yawDelta += 360;
        this.targetYaw = this.startYaw + yawDelta;

        float pitchDelta = this.targetPitch - this.startPitch;
        float totalDelta = Math.abs(yawDelta) + Math.abs(pitchDelta);

        // Only snap for truly imperceptible adjustments (<4°)
        if (totalDelta < 4.0f) {
            player.setYaw(this.targetYaw);
            player.setPitch(this.targetPitch);
            this.progress = 1.0f;
            return;
        }

        // Scale start progress and duration by angle size for smoothness
        if (totalDelta < 10.0f) {
            this.progress = 0.5f;
            this.duration = 2.0f + (float)(Math.random() * 0.5);
        } else if (totalDelta < 25.0f) {
            this.progress = 0.3f;
            this.duration = 3.0f + (float)(Math.random() * 1.0);
        } else {
            this.progress = 0.0f;
            this.duration = BASE_DURATION
                + (float)(Math.random() * 1.5)
                + (totalDelta / 100.0f);
        }

        float overshootFactor = (float)(0.03 + Math.random() * 0.07);
        this.overshootYaw = yawDelta * overshootFactor;
        this.overshootPitch = pitchDelta * overshootFactor;
        this.microJitterX = (float)(Math.random() * 0.2 - 0.1);
        this.microJitterY = (float)(Math.random() * 0.1 - 0.05);
    }

    public boolean tick(PlayerEntity player) {
        if (progress >= 1.0f) return true;

        progress = Math.min(1.0f, progress + (1.0f / duration));
        float t = progress;
        float eased = (float)(-(Math.cos(Math.PI * t) - 1) / 2);

        float newYaw, newPitch;
        if (progress < 0.85f) {
            newYaw = startYaw + (targetYaw - startYaw + overshootYaw) * eased;
            newPitch = startPitch + (targetPitch - startPitch + overshootPitch) * eased;
        } else {
            float ct = (progress - 0.85f) / 0.15f;
            newYaw = (targetYaw + overshootYaw) + (-overshootYaw) * ct;
            newPitch = (targetPitch + overshootPitch) + (-overshootPitch) * ct;
        }

        microJitterX = microJitterX * 0.7f
            + (float)(Math.random() * 0.2 - 0.1) * 0.3f;
        microJitterY = microJitterY * 0.7f
            + (float)(Math.random() * 0.1 - 0.05) * 0.3f;
        newYaw += microJitterX;
        newPitch += microJitterY;

        player.setYaw(newYaw);
        player.setPitch(newPitch);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.headYaw = newYaw;
        }
        return progress >= 1.0f;
    }

    /**
     * Continuous micro-correction for aim tracking while breaking.
     * Smoothly nudges aim toward the target each tick without starting
     * a full look-at sequence. Returns true if aim is close enough to hit.
     */
    public boolean trackTarget(PlayerEntity player, BlockPos pos) {
        Vec3d eyes = player.getEyePos();
        double dx = (pos.getX() + 0.5) - eyes.x;
        double dy = (pos.getY() + 0.5) - eyes.y;
        double dz = (pos.getZ() + 0.5) - eyes.z;
        double distHoriz = Math.sqrt(dx * dx + dz * dz);

        float wantYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        float wantPitch = (float)(Math.toDegrees(-Math.atan2(dy, distHoriz)));
        wantPitch = Math.max(-90, Math.min(90, wantPitch));

        float yawDiff = wantYaw - player.getYaw();
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float pitchDiff = wantPitch - player.getPitch();

        // Humanized correction speed with slight variance per tick
        float speed = 0.28f + (float)(Math.random() * 0.08);
        float jX = (float)(Math.random() * 0.12 - 0.06);
        float jY = (float)(Math.random() * 0.06 - 0.03);

        player.setYaw(player.getYaw() + yawDiff * speed + jX);
        player.setPitch(player.getPitch() + pitchDiff * speed + jY);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.headYaw = player.getYaw();
        }

        // Return true if aim is acceptably close
        Vec3d toBlock = Vec3d.ofCenter(pos).subtract(eyes).normalize();
        Vec3d lookVec = player.getRotationVec(1.0f);
        return lookVec.dotProduct(toBlock) >= 0.80;
    }

    public boolean isLocked() { return progress >= 1.0f; }
    public void reset() { progress = 1.0f; }
}
