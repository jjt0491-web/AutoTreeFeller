package com.sylvanforager.autotreeleller.navigation;

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

    public static List<BlockPos> getWalkableBlocks(ClientPlayerEntity player,
        BlockPos destination) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<BlockPos> walkable = new ArrayList<>();

        int minX = Math.min(player.getBlockX(), destination.getX()) - 5;
        int maxX = Math.max(player.getBlockX(), destination.getX()) + 5;
        int minZ = Math.min(player.getBlockZ(), destination.getZ()) - 5;
        int maxZ = Math.max(player.getBlockZ(), destination.getZ()) + 5;
        int minY = player.getBlockY() - 5;
        int maxY = player.getBlockY() + 10;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!client.world.getBlockState(p).isAir()) {
                        walkable.add(p);
                    }
                }
            }
        }
        return walkable;
    }
}