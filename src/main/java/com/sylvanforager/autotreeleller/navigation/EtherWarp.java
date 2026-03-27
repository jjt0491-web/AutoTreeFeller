package com.sylvanforager.autotreeleller.navigation;

import com.sylvanforager.autotreeleller.look.PlayerLookHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class EtherWarp {
    private static final double ETHERWARP_RANGE = 60.0;
    private final PlayerLookHelper lookHelper = new PlayerLookHelper();
    private boolean aiming = false;
    private BlockPos target = null;

    public boolean canEtherwarp(MinecraftClient client, BlockPos destination) {
        if (client.player == null) return false;
        double dist = client.player.getEyePos()
            .distanceTo(Vec3d.ofCenter(destination));
        return dist <= ETHERWARP_RANGE;
    }

    public boolean tick(MinecraftClient client, BlockPos destination) {
        if (client.player == null) return false;

        if (!destination.equals(target)) {
            target = destination;
            lookHelper.startLookAt(client.player, destination);
            aiming = true;
        }

        boolean locked = lookHelper.tick(client.player);
        if (!locked) return false;

        Vec3d eyes = client.player.getEyePos();
        Vec3d toTarget = Vec3d.ofCenter(destination)
            .subtract(eyes).normalize();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        if (lookVec.dotProduct(toTarget) < 0.92) return false;

        // trigger etherwarp — shift + right click
        client.options.sneakKey.setPressed(true);
        client.options.useKey.setPressed(true);
        client.options.useKey.setPressed(false);
        client.options.sneakKey.setPressed(false);

        aiming = false;
        target = null;
        lookHelper.reset();
        return true;
    }

    public void reset() {
        aiming = false;
        target = null;
        lookHelper.reset();
    }
}