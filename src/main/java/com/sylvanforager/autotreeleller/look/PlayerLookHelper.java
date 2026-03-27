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
 private static final float BASE_DURATION = 4.0f;

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

 if (totalDelta < 12.0f) {
 player.setYaw(this.targetYaw);
 player.setPitch(this.targetPitch);
 this.progress = 1.0f;
 return;
 }

 this.progress = totalDelta < 25.0f ? 0.3f : 0.0f;
 this.duration = BASE_DURATION
 + (float)(Math.random() * 2.0)
 + (totalDelta / 90.0f);

 float overshootFactor = (float)(0.05 + Math.random() * 0.10);
 this.overshootYaw = yawDelta * overshootFactor;
 this.overshootPitch = pitchDelta * overshootFactor;
 this.microJitterX = (float)(Math.random() * 0.3 - 0.15);
 this.microJitterY = (float)(Math.random() * 0.15 - 0.075);
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
 + (float)(Math.random() * 0.3 - 0.15) * 0.3f;
 microJitterY = microJitterY * 0.7f
 + (float)(Math.random() * 0.15 - 0.075) * 0.3f;
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

 public boolean isLocked() { return progress >= 1.0f; }
 public void reset() { progress = 1.0f; }
}