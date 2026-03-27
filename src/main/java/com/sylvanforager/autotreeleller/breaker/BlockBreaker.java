package com.sylvanforager.autotreeleller.breaker;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import com.sylvanforager.autotreeleller.AutoTreeFeller;

public class BlockBreaker {

    public boolean breakBlock(MinecraftClient client, BlockPos pos) {
        if (client.interactionManager == null || client.world == null) return false;

        var state = client.world.getBlockState(pos);
        if (state.isAir()) return false;

        try {
            if (client.player != null) {
                client.player.swingHand(Hand.MAIN_HAND);
            }
            client.interactionManager.attackBlock(pos, Direction.UP);
            return true;
        } catch (Exception e) {
            AutoTreeFeller.LOGGER.error("[BREAK] Failed on {}: {}", pos, e.getMessage());
            return false;
        }
    }

    public boolean isBroken(MinecraftClient client, BlockPos pos) {
        return client.world.getBlockState(pos).isAir();
    }
}