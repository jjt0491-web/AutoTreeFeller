package com.sylvanforager.autotreeleller.navigation;

import com.sylvanforager.autotreeleller.render.OverlayRenderer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public class TreeClusterFinder {
    private static final int SCAN_RADIUS = 60;
    private static final int MAX_TREE_HEIGHT = 25;

    public static BlockPos findNextTree(ClientPlayerEntity player) {
        BlockPos origin = player.getBlockPos();
        MinecraftClient client = MinecraftClient.getInstance();

        Map<BlockPos, Integer> columnHeights = new HashMap<>();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                int count = 0;
                int lowestY = Integer.MAX_VALUE;
                for (int y = -5; y <= MAX_TREE_HEIGHT + 5; y++) {
                    BlockPos check = origin.add(x, y, z);
                    if (client.world.getBlockState(check).getBlock()
                        == Blocks.STRIPPED_SPRUCE_WOOD) {
                        count++;
                        if (check.getY() < lowestY) lowestY = check.getY();
                    }
                }
                if (count > 0 && count <= MAX_TREE_HEIGHT) {
                    BlockPos base = origin.add(x, lowestY - origin.getY(), z);
                    columnHeights.put(base, count);
                }
            }
        }

        if (columnHeights.isEmpty()) return null;

        List<BlockPos> centers = clusterCentroids(new ArrayList<>(columnHeights.keySet()));

        return centers.stream()
            .filter(pos -> {
                double xzDist = Math.sqrt(
                    Math.pow(pos.getX() - origin.getX(), 2) +
                    Math.pow(pos.getZ() - origin.getZ(), 2));
                return xzDist > 8.0;
            })
            .min(Comparator.comparingDouble(pos ->
                Math.sqrt(Math.pow(pos.getX() - player.getX(), 2) +
                          Math.pow(pos.getY() - player.getY(), 2) +
                          Math.pow(pos.getZ() - player.getZ(), 2))))
            .orElse(null);
    }

    private static List<BlockPos> clusterCentroids(List<BlockPos> positions) {
        List<List<BlockPos>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[positions.size()];

        for (int i = 0; i < positions.size(); i++) {
            if (visited[i]) continue;
            List<BlockPos> cluster = new ArrayList<>();
            Queue<Integer> queue = new LinkedList<>();
            queue.add(i);
            visited[i] = true;
            while (!queue.isEmpty()) {
                int idx = queue.poll();
                cluster.add(positions.get(idx));
                for (int j = 0; j < positions.size(); j++) {
                    if (!visited[j] && positions.get(idx)
                        .getSquaredDistance(positions.get(j)) <= 9) {
                        visited[j] = true;
                        queue.add(j);
                    }
                }
            }
            clusters.add(cluster);
        }

        List<BlockPos> centroids = new ArrayList<>();
        for (List<BlockPos> cluster : clusters) {
            int sx = 0, sy = 0, sz = 0;
            for (BlockPos p : cluster) { sx += p.getX(); sy += p.getY(); sz += p.getZ(); }
            centroids.add(new BlockPos(sx/cluster.size(), sy/cluster.size(), sz/cluster.size()));
        }
        return centroids;
    }

    /**
     * Find a solid block with 2 air above near the tree — suitable for etherwarp landing.
     * Scores candidates by proximity to tree and distance from player.
     * Adds slight random variation for human-like behavior.
     * Logs all candidates found.
     */
    public static BlockPos findEtherwarpLandingSpot(ClientPlayerEntity player,
        BlockPos tree) {
        MinecraftClient client = MinecraftClient.getInstance();
        com.sylvanforager.autotreeleller.AutoTreeFeller.LOGGER.info(
            "[ETHERWARP] Searching landing spot near tree {} (player at {})",
            tree, player.getBlockPos());

        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();

        // Wide search: ±8 XZ, ±5 Y around tree centroid
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = -5; y <= 5; y++) {
                    BlockPos check = tree.add(x, y, z);
                    // Block must be solid
                    if (client.world.getBlockState(check).isAir()) continue;
                    // Must have 2 air above (player standing space)
                    if (!client.world.getBlockState(check.up()).isAir()) continue;
                    if (!client.world.getBlockState(check.up(2)).isAir()) continue;

                    double treeDist = Math.sqrt(
                        Math.pow(check.getX() - tree.getX(), 2) +
                        Math.pow(check.getZ() - tree.getZ(), 2));
                    // Allow 0-6 blocks from tree center (was 1-5, too narrow)
                    if (treeDist > 6.0) continue;

                    candidates.add(check);
                }
            }
        }

        com.sylvanforager.autotreeleller.AutoTreeFeller.LOGGER.info(
            "[ETHERWARP] Found {} candidate landing spots", candidates.size());

        // Expose candidates to overlay renderer (cyan highlights)
        OverlayRenderer.etherwarpCandidates = new java.util.ArrayList<>(candidates);

        if (candidates.isEmpty()) return null;

        // Score: lower is better. Base = treeDist*0.4 + playerDist*0.6
        // Add small random jitter (±0.3) for human variation
        java.util.Random rng = new java.util.Random();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos c : candidates) {
            double treeDist = Math.sqrt(
                Math.pow(c.getX() - tree.getX(), 2) +
                Math.pow(c.getZ() - tree.getZ(), 2));
            double playerDist = Math.sqrt(
                Math.pow(c.getX() - player.getX(), 2) +
                Math.pow(c.getZ() - player.getZ(), 2));
            double score = treeDist * 0.4 + playerDist * 0.6 + rng.nextDouble() * 0.6;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }

        com.sylvanforager.autotreeleller.AutoTreeFeller.LOGGER.info(
            "[ETHERWARP] Selected landing spot {} (score={})", best,
            String.format("%.2f", bestScore));
        return best;
    }

    public static List<BlockPos> getWalkableBlocks(ClientPlayerEntity player,
        BlockPos destination) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<BlockPos> walkable = new ArrayList<>();

        int minX = Math.min(player.getBlockX(), destination.getX()) - 10;
        int maxX = Math.max(player.getBlockX(), destination.getX()) + 10;
        int minZ = Math.min(player.getBlockZ(), destination.getZ()) - 10;
        int maxZ = Math.max(player.getBlockZ(), destination.getZ()) + 10;
        int minY = Math.min(player.getBlockY(), destination.getY()) - 5;
        int maxY = Math.max(player.getBlockY(), destination.getY()) + 10;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!client.world.getBlockState(p).isAir()
                        && client.world.getBlockState(p.up()).isAir()) {
                        walkable.add(p);
                    }
                }
            }
        }
        return walkable;
    }
}
