package com.sylvanforager.autotreeleller.navigation;

import com.sylvanforager.autotreeleller.AutoTreeFeller;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

public class TreeNavigator {

    public enum NavState {
        IDLE, FINDING_TREE, ETHERWARPING, PATHFINDING, WALKING_PATH, ARRIVED
    }

    private NavState state = NavState.IDLE;
    private BlockPos nextTree = null;
    private BlockPos etherWarpTarget = null;
    private List<BlockPos> path = null;
    private int pathIndex = 0;
    private int stuckTicks = 0;
    private int totalWalkTicks = 0;
    private static final int MAX_WALK_TICKS = 200; // ~10 seconds
    private BlockPos lastPos = null;
    private boolean searched = false;

    private final EtherWarp etherWarp = new EtherWarp();
    private boolean etherwarpFailed = false;

    public boolean isIdle() { return state == NavState.IDLE; }
    public boolean hasSearched() { return searched; }
    public boolean hasArrived() { return state == NavState.ARRIVED; }

    public void start() {
        state = NavState.FINDING_TREE;
    }

    public void reset(MinecraftClient client) {
        state = NavState.IDLE;
        nextTree = null;
        path = null;
        pathIndex = 0;
        stuckTicks = 0;
        totalWalkTicks = 0;
        searched = false;
        etherwarpFailed = false;
        etherWarp.reset();
        releaseKeys(client);
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        switch (state) {

            case FINDING_TREE -> {
                nextTree = TreeClusterFinder.findNextTree(client.player);
                searched = true;
                if (nextTree == null) {
                    AutoTreeFeller.LOGGER.info("[NAV] No next tree found");
                    state = NavState.IDLE;
                    return;
                }
                AutoTreeFeller.LOGGER.info("[NAV] Next tree at {}", nextTree);

                if (!etherwarpFailed && etherWarp.canEtherwarp(client, nextTree)) {
                    etherWarpTarget = nextTree;
                    state = NavState.ETHERWARPING;
                } else {
                    state = NavState.PATHFINDING;
                }
            }

            case ETHERWARPING -> {
                boolean done = etherWarp.tick(client, etherWarpTarget);
                if (done) {
                    double dist = Math.sqrt(
                        Math.pow(client.player.getX() - nextTree.getX(), 2) +
                        Math.pow(client.player.getY() - nextTree.getY(), 2) +
                        Math.pow(client.player.getZ() - nextTree.getZ(), 2));
                    if (dist <= 5.0) {
                        AutoTreeFeller.LOGGER.info("[NAV] Etherwarped to tree");
                        state = NavState.ARRIVED;
                    } else {
                        AutoTreeFeller.LOGGER.info("[NAV] Etherwarp didn't reach tree (dist={}), falling back to pathfinding", String.format("%.1f", dist));
                        etherwarpFailed = true;
                        state = NavState.PATHFINDING;
                    }
                }
            }

            case PATHFINDING -> {
                BlockPos start = client.player.getBlockPos();

                List<BlockPos> walkable = TreeClusterFinder
                    .getWalkableBlocks(client.player, nextTree);

                // find the nearest walkable position around the tree
                BlockPos goal = findNearestWalkableGoal(
                    walkable, nextTree, start);
                if (goal == null) goal = nextTree.add(2, 0, 0);

                path = PathfindingBridge.computePath(walkable, start, goal);

                if (path == null || path.isEmpty()) {
                    if (etherwarpFailed) {
                        AutoTreeFeller.LOGGER.warn("[NAV] No path and etherwarp failed, giving up");
                        state = NavState.IDLE;
                        return;
                    }
                    AutoTreeFeller.LOGGER.warn("[NAV] No path found, trying etherwarp");
                    state = NavState.ETHERWARPING;
                    etherWarpTarget = nextTree;
                    return;
                }

                pathIndex = 0;
                stuckTicks = 0;
                lastPos = start;
                state = NavState.WALKING_PATH;
                AutoTreeFeller.LOGGER.info("[NAV] Path found, {} steps", path.size());
            }

            case WALKING_PATH -> {
                totalWalkTicks++;
                if (totalWalkTicks > MAX_WALK_TICKS) {
                    AutoTreeFeller.LOGGER.warn("[NAV] Walk timeout, giving up");
                    releaseKeys(client);
                    state = NavState.IDLE;
                    return;
                }

                if (path == null || pathIndex >= path.size()) {
                    releaseKeys(client);
                    state = NavState.ARRIVED;
                    return;
                }

                BlockPos waypoint = path.get(pathIndex);
                double dx = (waypoint.getX() + 0.5) - client.player.getX();
                double dz = (waypoint.getZ() + 0.5) - client.player.getZ();
                double xzDist = Math.sqrt(dx * dx + dz * dz);

                if (xzDist < 0.6 && Math.abs(waypoint.getY()
                    - client.player.getBlockY()) <= 1) {
                    pathIndex++;
                    stuckTicks = 0;
                    return;
                }

                BlockPos currentPos = client.player.getBlockPos();
                if (currentPos.equals(lastPos)) {
                    stuckTicks++;
                    if (stuckTicks > 20) {
                        client.options.jumpKey.setPressed(true);
                        stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                    client.options.jumpKey.setPressed(false);
                }
                lastPos = currentPos;

                double treeDist = Math.sqrt(
                    Math.pow(client.player.getX() - nextTree.getX(), 2) +
                    Math.pow(client.player.getY() - nextTree.getY(), 2) +
                    Math.pow(client.player.getZ() - nextTree.getZ(), 2));
                if (treeDist <= 3.0) {
                    releaseKeys(client);
                    state = NavState.ARRIVED;
                    return;
                }

                float targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                float currentYaw = client.player.getYaw();
                float yawDelta = targetYaw - currentYaw;
                while (yawDelta > 180) yawDelta -= 360;
                while (yawDelta < -180) yawDelta += 360;
                client.player.setYaw(currentYaw + yawDelta * 0.4f);

                if (waypoint.getY() > client.player.getBlockY()
                    && client.player.isOnGround()) {
                    client.options.jumpKey.setPressed(true);
                }

                client.options.forwardKey.setPressed(true);
                client.options.sprintKey.setPressed(true);
            }

            case ARRIVED, IDLE -> {
                releaseKeys(client);
            }
        }
    }

    private BlockPos findNearestWalkableGoal(List<BlockPos> walkable,
        BlockPos tree, BlockPos playerPos) {
        // find a walkable spot within 3 blocks of the tree, closest to player
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos w : walkable) {
            // check this is a floor block with air above it
            double treeDist = Math.sqrt(
                Math.pow(w.getX() - tree.getX(), 2) +
                Math.pow(w.getZ() - tree.getZ(), 2));
            if (treeDist > 4.0 || treeDist < 1.0) continue;
            if (Math.abs(w.getY() - tree.getY()) > 2) continue;
            // the walkable position is on top of this block
            BlockPos standing = w.up();
            double playerDist = Math.sqrt(
                Math.pow(standing.getX() - playerPos.getX(), 2) +
                Math.pow(standing.getZ() - playerPos.getZ(), 2));
            if (playerDist < bestDist) {
                bestDist = playerDist;
                best = standing;
            }
        }
        return best;
    }

    private void releaseKeys(MinecraftClient client) {
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.sneakKey.setPressed(false);
        client.options.useKey.setPressed(false);
    }
}