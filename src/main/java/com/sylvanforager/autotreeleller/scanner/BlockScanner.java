package com.sylvanforager.autotreeleller.scanner;

import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
import com.sylvanforager.autotreeleller.AutoTreeFeller;
import com.sylvanforager.autotreeleller.config.TreeFellerConfig;
import java.util.*;

public class BlockScanner {

    /**
     * Static convenience method for BreakSequencer.
     */
    public static Queue<BlockPos> scan(PlayerEntity player) {
        return new BlockScanner().scan(player, 5); // default radius 5
    }

    /**
     * Simple visibility check - skip blocks likely occluded by leaves.
     */
    private static boolean isLikelyVisible(PlayerEntity player, BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return true;

        // If player is below the block, check if there's a block above
        if (player.getY() < pos.getY()) {
            BlockPos above = pos.up();
            if (!client.world.getBlockState(above).isAir()) {
                return false; // likely occluded by leaves
            }
        }
        return true;
    }

    /**
     * Finds the nearest in-reach block from current position without a full rescan.
     */
    public static BlockPos findNearest(PlayerEntity player, double maxDist) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;

        BlockPos playerPos = player.getBlockPos();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(maxDist);

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos check = playerPos.add(x, y, z);
                    // use eye position for distance
                    double d = player.getEyePos().distanceTo(Vec3d.ofCenter(check));
                    if (d > maxDist) continue;
                    if (client.world.getBlockState(check).getBlock()
                        != Blocks.STRIPPED_SPRUCE_WOOD) continue;

                    if (!isLikelyVisible(player, check)) continue;

                    if (d < nearestDist) {
                        nearestDist = d;
                        nearest = check;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Find the block that has the most other queued blocks within 4 blocks of it.
     * This is the "hotspot" — swinging here hits the most blocks via sweep.
     */
    public static BlockPos findOptimalTarget(PlayerEntity player,
            Queue<BlockPos> queue, double maxDist) {
        BlockPos best = null;
        int bestScore = -1;
        List<BlockPos> queueList = new ArrayList<>(queue);

        for (BlockPos candidate : queueList) {
            // use eye position for distance
            double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(candidate));
            if (dist > maxDist) continue;
            // must have line of sight
            if (!isLikelyVisible(player, candidate)) continue;
            // score = how many other queued blocks are within 4 blocks of this one
            int score = 0;
            for (BlockPos other : queueList) {
                if (!other.equals(candidate) &&
                        candidate.getSquaredDistance(other) <= 16) { // 4 blocks radius
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        // fallback to nearest if nothing scored
        if (best == null) best = findNearest(player, maxDist);
        return best;
    }

    /**
     * Find the absolute lowest block in the queue.
     */
    public static BlockPos findLowest(PlayerEntity player, Queue<BlockPos> queue) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        return queue.stream()
            .filter(pos -> !client.world.getBlockState(pos).isAir())
            .filter(pos -> isLikelyVisible(player, pos))
            .min(Comparator.comparingInt(BlockPos::getY))
            .orElse(null);
    }

    public Queue<BlockPos> scan(PlayerEntity player, int radius) {
        TreeFellerConfig config = AutoTreeFeller.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null) return new ArrayDeque<>();

        BlockPos playerPos = player.getBlockPos();
        List<BlockPos> blocks = new ArrayList<>();

        int horizontalRadius = 8; // was 5, wider to catch full tree spread
        int heightReach = 25; // was 20, taller for big trees

        for (int y = -2; y <= heightReach; y++) {
            int r = horizontalRadius + (y > 0 ? y / 6 : 0);
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos check = playerPos.add(x, y, z);
                    if (client.world.getBlockState(check).getBlock() == Blocks.STRIPPED_SPRUCE_WOOD) {
                        blocks.add(check);
                    }
                }
            }
        }

        // Primary sort: Y ascending (lowest first)
        // Secondary sort: distance to player (closest at same Y level first)
        blocks.sort((a, b) -> {
            if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
            double da = a.getSquaredDistance(playerPos);
            double db = b.getSquaredDistance(playerPos);
            return Double.compare(da, db);
        });

        // Then apply visibility filter while preserving Y-order
        List<BlockPos> filtered = new ArrayList<>();
        for (BlockPos bp : blocks) {
            if (isLikelyVisible(player, bp)) {
                filtered.add(bp);
            }
        }
        blocks = filtered;

        int maxSize = config.maxQueueSize;
        if (blocks.size() > maxSize) {
            blocks = blocks.subList(0, maxSize);
        }

        AutoTreeFeller.LOGGER.info("[SCAN] Found {} stripped_spruce_wood blocks", blocks.size());

        return new ArrayDeque<>(blocks);
    }
}