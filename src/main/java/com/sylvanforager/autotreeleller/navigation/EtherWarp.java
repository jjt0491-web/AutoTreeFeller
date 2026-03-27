package com.sylvanforager.autotreeleller.navigation;

import com.sylvanforager.autotreeleller.look.PlayerLookHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class EtherWarp {
    private static final double ETHERWARP_RANGE = 60.0;
    private final PlayerLookHelper lookHelper = new PlayerLookHelper();
    private BlockPos target = null;

    private enum Phase { AIMING, SNEAK_HOLD, USE_PRESS, COOLDOWN, DONE }
    private Phase phase = Phase.DONE;
    private int holdTicks = 0;

    public boolean canEtherwarp(MinecraftClient client, BlockPos destination) {
        if (client.player == null) return false;
        double dist = client.player.getEyePos()
            .distanceTo(Vec3d.ofCenter(destination));
        return dist <= ETHERWARP_RANGE;
    }

    public boolean tick(MinecraftClient client, BlockPos destination) {
        if (client.player == null) return false;

        if (!destination.equals(target) || phase == Phase.DONE) {
            target = destination;
            lookHelper.startLookAt(client.player, destination);
            phase = Phase.AIMING;
            holdTicks = 0;
        }

        switch (phase) {
            case AIMING -> {
                boolean locked = lookHelper.tick(client.player);
                if (!locked) return false;

                Vec3d eyes = client.player.getEyePos();
                Vec3d toTarget = Vec3d.ofCenter(destination)
                    .subtract(eyes).normalize();
                Vec3d lookVec = client.player.getRotationVec(1.0f);
                if (lookVec.dotProduct(toTarget) < 0.92) return false;

                // start holding sneak
                client.options.sneakKey.setPressed(true);
                holdTicks = 0;
                phase = Phase.SNEAK_HOLD;
            }
            case SNEAK_HOLD -> {
                // hold sneak for 2 ticks before pressing use
                client.options.sneakKey.setPressed(true);
                holdTicks++;
                if (holdTicks >= 2) {
                    client.options.useKey.setPressed(true);
                    holdTicks = 0;
                    phase = Phase.USE_PRESS;
                }
            }
            case USE_PRESS -> {
                // hold both for 2 ticks
                client.options.sneakKey.setPressed(true);
                client.options.useKey.setPressed(true);
                holdTicks++;
                if (holdTicks >= 2) {
                    client.options.useKey.setPressed(false);
                    client.options.sneakKey.setPressed(false);
                    holdTicks = 0;
                    phase = Phase.COOLDOWN;
                }
            }
            case COOLDOWN -> {
                // wait a few ticks for the teleport to register
                holdTicks++;
                if (holdTicks >= 5) {
                    phase = Phase.DONE;
                    target = null;
                    lookHelper.reset();
                    return true;
                }
            }
            case DONE -> { return true; }
        }
        return false;
    }

    public void reset() {
        phase = Phase.DONE;
        holdTicks = 0;
        target = null;
        lookHelper.reset();
    }
}
