package com.sylvanforager.autotreeleller.reach;

import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import com.sylvanforager.autotreeleller.AutoTreeFeller;

public class ReachChecker {

    private static final double VANILLA_REACH = 4.5;
    private static final double JUMP_BOOST_PER_LEVEL = 1.5;

    public boolean isReachable(PlayerEntity player, BlockPos pos) {
        double reach = getReachDistance(player);
        double dist = distanceTo(player, pos);
        return dist <= reach;
    }

    public double getReachDistance(PlayerEntity player) {
        if (player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            int amplifier = player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier();
            return VANILLA_REACH + (amplifier + 1) * JUMP_BOOST_PER_LEVEL;
        }
        return VANILLA_REACH;
    }

    private double distanceTo(PlayerEntity player, BlockPos pos) {
        return Math.sqrt(
            Math.pow(pos.getX() - player.getX(), 2) +
            Math.pow(pos.getY() - player.getY(), 2) +
            Math.pow(pos.getZ() - player.getZ(), 2)
        );
    }
}