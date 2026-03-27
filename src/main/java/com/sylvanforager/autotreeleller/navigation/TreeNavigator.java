package com.sylvanforager.autotreeleller.navigation;

import com.sylvanforager.autotreeleller.AutoTreeFeller;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;

public class TreeNavigator {

    public enum NavState {
        IDLE, FINDING_TREE, WALKING_TOWARD, ETHERWARPING,
        PATHFINDING, WALKING_PATH, ARRIVED
    }

    private NavState state = NavState.IDLE;
    private BlockPos nextTree = null;
    private BlockPos etherWarpTarget = null;
    private List<BlockPos> path = null;
    private int pathIndex = 0;
    private int stuckTicks = 0;
    private int walkTicks = 0;
    private static final int MAX_WALK_TICKS = 300; // ~15 seconds
    private BlockPos lastPos = null;
    private boolean searched = false;
    private boolean triedPathfinding = false;
    private boolean triedEtherwarp = false;

    private final EtherWarp etherWarp = new EtherWarp();

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
        walkTicks = 0;
        searched = false;
        triedPathfinding = false;
        triedEtherwarp = false;
        etherWarp.reset();
        releaseKeys(client);
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        switch (state) {

            // ── Find next tree, then walk toward it ──────────────────────
            case FINDING_TREE -> {
                nextTree = TreeClusterFinder.findNextTree(client.player);
                searched = true;
                if (nextTree == null) {
                    AutoTreeFeller.LOGGER.info("[NAV] No next tree found");
                    state = NavState.IDLE;
                    return;
                }
                AutoTreeFeller.LOGGER.info("[NAV] Next tree at {}", nextTree);

                // If the tree is significantly higher, etherwarp to a landing spot first
                int heightDiff = nextTree.getY() - client.player.getBlockPos().getY();
                if (heightDiff > 3) {
                    BlockPos landing = TreeClusterFinder
                        .findEtherwarpLandingSpot(client.player, nextTree);
                    if (landing != null && etherWarp.canEtherwarp(client, landing)) {
                        etherWarpTarget = landing;
                        triedEtherwarp = true;
                        state = NavState.ETHERWARPING;
                        AutoTreeFeller.LOGGER.info(
                            "[NAV] Tree is {} blocks above, etherwarping to landing {}",
                            heightDiff, landing);
                        return;
                    }
                }

                // Primary strategy: just walk toward the tree
                walkTicks = 0;
                stuckTicks = 0;
                lastPos = client.player.getBlockPos();
                state = NavState.WALKING_TOWARD;
            }

            // ── Simple walk toward tree (no pathfinding needed) ──────────
            case WALKING_TOWARD -> {
                walkTicks++;

                // Check if we arrived
                double treeDist = xzDist(client, nextTree);
                if (treeDist <= 4.0) {
                    releaseKeys(client);
                    state = NavState.ARRIVED;
                    AutoTreeFeller.LOGGER.info("[NAV] Arrived at tree (walked)");
                    return;
                }

                // Timeout → try etherwarp or give up
                if (walkTicks > MAX_WALK_TICKS) {
                    releaseKeys(client);
                    if (!triedEtherwarp && etherWarp.canEtherwarp(client, nextTree)) {
                        triedEtherwarp = true;
                        etherWarpTarget = nextTree;
                        state = NavState.ETHERWARPING;
                        AutoTreeFeller.LOGGER.info("[NAV] Walk timeout, trying etherwarp");
                    } else {
                        AutoTreeFeller.LOGGER.warn("[NAV] Walk timeout, giving up");
                        state = NavState.IDLE;
                    }
                    return;
                }

                // Stuck detection
                BlockPos currentPos = client.player.getBlockPos();
                if (currentPos.equals(lastPos)) {
                    stuckTicks++;

                    // Jump over obstacles aggressively — start early, keep jumping
                    if (stuckTicks > 5 && client.player.isOnGround()) {
                        client.options.jumpKey.setPressed(true);
                    }

                    // Really stuck → try pathfinding or etherwarp
                    if (stuckTicks > 30) {
                        releaseKeys(client);
                        stuckTicks = 0;

                        if (!triedPathfinding) {
                            triedPathfinding = true;
                            state = NavState.PATHFINDING;
                            AutoTreeFeller.LOGGER.info("[NAV] Stuck walking, trying pathfinding");
                        } else if (!triedEtherwarp && etherWarp.canEtherwarp(client, nextTree)) {
                            triedEtherwarp = true;
                            etherWarpTarget = nextTree;
                            state = NavState.ETHERWARPING;
                            AutoTreeFeller.LOGGER.info("[NAV] Stuck again, trying etherwarp");
                        } else {
                            AutoTreeFeller.LOGGER.warn("[NAV] All navigation failed, giving up");
                            state = NavState.IDLE;
                        }
                        return;
                    }
                } else {
                    stuckTicks = 0;
                    client.options.jumpKey.setPressed(false);
                }
                lastPos = currentPos;

                // Face the tree and walk (fast turn)
                double dx = (nextTree.getX() + 0.5) - client.player.getX();
                double dz = (nextTree.getZ() + 0.5) - client.player.getZ();
                float targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                float currentYaw = client.player.getYaw();
                float yawDelta = targetYaw - currentYaw;
                while (yawDelta > 180) yawDelta -= 360;
                while (yawDelta < -180) yawDelta += 360;
                client.player.setYaw(currentYaw + yawDelta * 0.6f);

                client.options.forwardKey.setPressed(true);
                client.options.sprintKey.setPressed(true);
            }

            // ── Etherwarp (fallback when walking fails) ──────────────────
            case ETHERWARPING -> {
                boolean done = etherWarp.tick(client, etherWarpTarget);
                if (done) {
                    double dist = xzDist(client, nextTree);
                    if (dist <= 5.0) {
                        AutoTreeFeller.LOGGER.info("[NAV] Etherwarped to tree");
                        state = NavState.ARRIVED;
                    } else {
                        AutoTreeFeller.LOGGER.info(
                            "[NAV] Etherwarp landed {} blocks away, walking rest",
                            String.format("%.1f", dist));
                        // Walk the remaining distance
                        walkTicks = 0;
                        stuckTicks = 0;
                        lastPos = client.player.getBlockPos();
                        state = NavState.WALKING_TOWARD;
                    }
                }
            }

            // ── A* pathfinding (fallback when simple walk gets stuck) ────
            case PATHFINDING -> {
                BlockPos start = client.player.getBlockPos();

                List<BlockPos> walkable = TreeClusterFinder
                    .getWalkableBlocks(client.player, nextTree);

                BlockPos goal = findNearestWalkableGoal(
                    walkable, nextTree, start);
                if (goal == null) goal = nextTree.add(2, 0, 0);

                try {
                    path = PathfindingBridge.computePath(walkable, start, goal);
                } catch (Exception e) {
                    AutoTreeFeller.LOGGER.error("[NAV] Pathfinding error: {}", e.getMessage());
                    path = null;
                }

                if (path == null || path.isEmpty()) {
                    AutoTreeFeller.LOGGER.warn("[NAV] No path found, resuming walk");
                    // Fall back to simple walking
                    walkTicks = 0;
                    stuckTicks = 0;
                    state = NavState.WALKING_TOWARD;
                    return;
                }

                pathIndex = 0;
                stuckTicks = 0;
                walkTicks = 0;
                lastPos = start;
                state = NavState.WALKING_PATH;
                AutoTreeFeller.LOGGER.info("[NAV] Path found, {} steps", path.size());
            }

            // ── Follow A* path waypoints ─────────────────────────────────
            case WALKING_PATH -> {
                walkTicks++;
                if (walkTicks > MAX_WALK_TICKS) {
                    AutoTreeFeller.LOGGER.warn("[NAV] Path walk timeout");
                    releaseKeys(client);
                    state = NavState.WALKING_TOWARD; // fall back to simple walk
                    walkTicks = 0;
                    stuckTicks = 0;
                    return;
                }

                // Check tree proximity
                double treeDist = xzDist(client, nextTree);
                if (treeDist <= 4.0) {
                    releaseKeys(client);
                    state = NavState.ARRIVED;
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
                double wpDist = Math.sqrt(dx * dx + dz * dz);

                if (wpDist < 0.6 && Math.abs(waypoint.getY()
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

                float targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                float currentYaw = client.player.getYaw();
                float yawDelta = targetYaw - currentYaw;
                while (yawDelta > 180) yawDelta -= 360;
                while (yawDelta < -180) yawDelta += 360;
                client.player.setYaw(currentYaw + yawDelta * 0.6f);

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

    private double xzDist(MinecraftClient client, BlockPos target) {
        double dx = client.player.getX() - target.getX();
        double dz = client.player.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private BlockPos findNearestWalkableGoal(List<BlockPos> walkable,
        BlockPos tree, BlockPos playerPos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos w : walkable) {
            double treeDist = Math.sqrt(
                Math.pow(w.getX() - tree.getX(), 2) +
                Math.pow(w.getZ() - tree.getZ(), 2));
            if (treeDist > 4.0 || treeDist < 1.0) continue;
            if (Math.abs(w.getY() - tree.getY()) > 2) continue;
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
