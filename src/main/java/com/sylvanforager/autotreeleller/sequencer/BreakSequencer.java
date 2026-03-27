package com.sylvanforager.autotreeleller.sequencer;

import com.sylvanforager.autotreeleller.AutoTreeFeller;
import com.sylvanforager.autotreeleller.look.PlayerLookHelper;
import com.sylvanforager.autotreeleller.navigation.TreeNavigator;
import com.sylvanforager.autotreeleller.scanner.BlockScanner;
import com.sylvanforager.autotreeleller.util.HumanTimer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.LinkedList;
import java.util.Queue;

public class BreakSequencer {

    public enum State {
        IDLE, SCANNING, TURNING, BREAKING,
        SETTLING, JUMPING, WAITING_FOR_APEX,
        WALKING, THROWING, NAVIGATING
    }

    private State state = State.IDLE;
    private Queue<BlockPos> queue = new LinkedList<>();
    private BlockPos current = null;
    private boolean firstTarget = true;
    private boolean justThrew = false;
    private int jumpTicks = 0;
    private int jumpCooldown = 0;
    private int breakHoldTicks = 0;
    private int jumpAttempts = 0;
    private BlockPos lastJumpTarget = null;
    private int throwHoldTicks = 0;
    private int walkStuckTicks = 0;
    private BlockPos lastWalkPos = null;

    // ── Jump-loop & scan-failure escape ───────────────────────────────────
    private int consecutiveJumps = 0;
    private int scanFailures = 0;
    private static final int MAX_CONSECUTIVE_JUMPS = 4;
    private static final int MAX_SCAN_FAILURES = 6;

    private static final int MAX_HOLD_TICKS = 40;
    private static final double REACH = 4.5;

    private final PlayerLookHelper lookHelper = new PlayerLookHelper();
    private final HumanTimer settleTimer = new HumanTimer(1, 2);
    private final HumanTimer apexTimer = new HumanTimer(8, 13);
    private final HumanTimer throwSettleTimer = new HumanTimer(12, 18);
    private final TreeNavigator navigator = new TreeNavigator();

    // ── toggle ────────────────────────────────────────────────────────────
    public void toggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (state != State.IDLE) {
            releaseAll(client);
            state = State.IDLE;
            current = null;
            firstTarget = true;
            justThrew = false;
            jumpTicks = 0;
            jumpCooldown = 0;
            jumpAttempts = 0;
            consecutiveJumps = 0;
            scanFailures = 0;
            lastJumpTarget = null;
            breakHoldTicks = 0;
            queue.clear();
            lookHelper.reset();
            navigator.reset(client);
            AutoTreeFeller.LOGGER.info("[ATF] Disabled");
            return;
        }

        if (client.player == null || client.world == null) return;

        if (!BlockScanner.hasAnyNearby(client.player, 20.0)) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c[AutoFeller] No fig tree nearby!"),
                true);
            return;
        }

        firstTarget = true;
        consecutiveJumps = 0;
        scanFailures = 0;
        state = State.SCANNING;
        AutoTreeFeller.LOGGER.info("[ATF] Enabled");
    }

    public boolean isActive() { return state != State.IDLE; }
    public State getState() { return state; }

    // ── main tick ─────────────────────────────────────────────────────────
    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (jumpCooldown > 0) jumpCooldown--;

        // global sweep purge every tick
        if (state != State.IDLE && state != State.SCANNING) {
            queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
        }

        // stop if no tree nearby — transition to navigation
        if (state != State.IDLE && state != State.WALKING
                && state != State.THROWING && state != State.SCANNING
                && state != State.NAVIGATING) {
            if (!BlockScanner.hasAnyNearby(client.player, 20.0)) {
                releaseAll(client);
                queue.clear();
                BlockScanner.resetNearbyCache();
                navigator.reset(client);
                navigator.start();
                state = State.NAVIGATING;
                AutoTreeFeller.LOGGER.info("[ATF] No blocks nearby, navigating to next tree");
                return;
            }
        }

        switch (state) {

            // ── SCANNING ─────────────────────────────────────────────────
            case SCANNING -> {
                queue = BlockScanner.scan(client.player);
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());

                if (queue.isEmpty()) {
                    // Tree done — navigate to next tree
                    BlockScanner.resetNearbyCache();
                    navigator.reset(client);
                    navigator.start();
                    state = State.NAVIGATING;
                    consecutiveJumps = 0;
                    scanFailures = 0;
                    AutoTreeFeller.LOGGER.info("[ATF] Tree complete, navigating to next");
                    return;
                }

                BlockPos next = firstTarget
                    ? BlockScanner.findLowest(client.player, queue)
                    : BlockScanner.findOptimalTarget(client.player, queue, REACH);

                if (next == null) {
                    scanFailures++;

                    // Escape: too many failed jumps/scans → throw or navigate
                    if (consecutiveJumps >= MAX_CONSECUTIVE_JUMPS
                            || scanFailures >= MAX_SCAN_FAILURES) {
                        BlockPos throwTarget = BlockScanner.findOptimalTarget(
                            client.player, queue, 20.0);
                        if (throwTarget != null) {
                            current = throwTarget;
                            lookHelper.startLookAt(client.player, current);
                            state = State.THROWING;
                            AutoTreeFeller.LOGGER.info(
                                "[ATF] Can't reach blocks after {} jumps, throwing",
                                consecutiveJumps);
                        } else {
                            BlockScanner.resetNearbyCache();
                            navigator.reset(client);
                            navigator.start();
                            state = State.NAVIGATING;
                            AutoTreeFeller.LOGGER.info(
                                "[ATF] Unreachable blocks, navigating to next tree");
                        }
                        consecutiveJumps = 0;
                        scanFailures = 0;
                        return;
                    }

                    state = State.JUMPING;
                    return;
                }

                // Found a reachable target
                firstTarget = false;
                scanFailures = 0;
                current = next;
                lookHelper.startLookAt(client.player, current);
                state = State.TURNING;
            }

            // ── TURNING ──────────────────────────────────────────────────
            case TURNING -> {
                boolean locked = lookHelper.tick(client.player);
                if (locked) {
                    if (isValidTarget(client, current)) {
                        client.options.attackKey.setPressed(true);
                        client.player.swingHand(Hand.MAIN_HAND);
                        breakHoldTicks = 1;
                    }
                    state = State.BREAKING;
                }
            }

            // ── BREAKING ─────────────────────────────────────────────────
            case BREAKING -> {
                if (current == null) {
                    releaseAll(client);
                    state = State.SCANNING;
                    return;
                }

                BlockState bs = client.world.getBlockState(current);

                // ── Block broken — seamless transition to next ────────────
                if (bs.isAir()) {
                    consecutiveJumps = 0;
                    scanFailures = 0;
                    queue.remove(current);

                    // Immediately find the next reachable block
                    queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                    BlockPos next = BlockScanner.findOptimalTarget(
                        client.player, queue, REACH);

                    if (next != null) {
                        // Seamless: keep attack held, smooth-aim to next
                        current = next;
                        lookHelper.startLookAt(client.player, current);
                        breakHoldTicks = 0;
                        // Stay in BREAKING — no pause between blocks
                    } else {
                        // Nothing in reach right now
                        client.options.attackKey.setPressed(false);
                        breakHoldTicks = 0;

                        if (queue.isEmpty()) {
                            BlockScanner.resetNearbyCache();
                            navigator.reset(client);
                            navigator.start();
                            state = State.NAVIGATING;
                            AutoTreeFeller.LOGGER.info(
                                "[ATF] Tree complete, navigating to next");
                        } else {
                            // Blocks exist but out of reach — quick settle then re-scan
                            settleTimer.reset();
                            state = State.SETTLING;
                        }
                    }
                    return;
                }

                // ── Out of reach ──────────────────────────────────────────
                double eyeDist = client.player.getEyePos()
                    .distanceTo(Vec3d.ofCenter(current));

                if (eyeDist > REACH) {
                    client.options.attackKey.setPressed(false);
                    breakHoldTicks = 0;

                    // Try to find something in reach
                    BlockPos reachable = BlockScanner.findOptimalTarget(
                        client.player, queue, REACH);
                    if (reachable != null) {
                        current = reachable;
                        lookHelper.startLookAt(client.player, current);
                        state = State.TURNING;
                        return;
                    }

                    // Try walking closer
                    BlockPos nearby = BlockScanner.findNearest(client.player, 9.0);
                    if (nearby != null) {
                        double xzDist = Math.sqrt(
                            Math.pow(nearby.getX() - client.player.getX(), 2) +
                            Math.pow(nearby.getZ() - client.player.getZ(), 2));
                        if (xzDist > 1.0) {
                            state = State.WALKING;
                            return;
                        }
                    }

                    // Jump if on ground
                    if (client.player.isOnGround() && jumpCooldown == 0) {
                        BlockPos jumpTarget = BlockScanner.findOptimalTarget(
                            client.player, queue, 6.0);
                        if (jumpTarget != null) {
                            current = jumpTarget;
                            lookHelper.startLookAt(client.player, current);
                        }
                        state = State.JUMPING;
                    } else if (!client.player.isOnGround()) {
                        state = State.WAITING_FOR_APEX;
                    }
                    return;
                }

                // ── Continuous aim tracking while holding attack ──────────
                boolean aimOk = lookHelper.trackTarget(client.player, current);

                if (!aimOk) {
                    // Check if aim drifted way off (e.g. player got pushed)
                    Vec3d eyes = client.player.getEyePos();
                    Vec3d toBlock = Vec3d.ofCenter(current).subtract(eyes).normalize();
                    Vec3d lookVec = client.player.getRotationVec(1.0f);
                    if (lookVec.dotProduct(toBlock) < 0.55) {
                        // Way off — full re-aim
                        client.options.attackKey.setPressed(false);
                        breakHoldTicks = 0;
                        lookHelper.startLookAt(client.player, current);
                        state = State.TURNING;
                        return;
                    }
                    // Moderately off — trackTarget will converge, keep attacking
                }

                // Safety cutoff
                if (breakHoldTicks >= MAX_HOLD_TICKS) {
                    client.options.attackKey.setPressed(false);
                    breakHoldTicks = 0;
                    queue.poll();
                    settleTimer.reset();
                    state = State.SETTLING;
                    return;
                }

                if (!isValidTarget(client, current)) {
                    client.options.attackKey.setPressed(false);
                    queue.poll();
                    state = State.SCANNING;
                    return;
                }

                // Continuous attack — no random stutter delays
                client.options.attackKey.setPressed(true);
                client.player.swingHand(Hand.MAIN_HAND);
                breakHoldTicks++;
            }

            // ── SETTLING ─────────────────────────────────────────────────
            case SETTLING -> {
                releaseAll(client);
                boolean done = justThrew ? throwSettleTimer.tick() : settleTimer.tick();
                if (done) {
                    justThrew = false;
                    state = State.SCANNING;
                }
            }

            // ── JUMPING ──────────────────────────────────────────────────
            case JUMPING -> {
                consecutiveJumps++;

                // Stall detector — same block repeatedly
                if (current != null && current.equals(lastJumpTarget)) {
                    jumpAttempts++;
                    if (jumpAttempts >= 3) {
                        jumpAttempts = 0;
                        lastJumpTarget = null;
                        queue.removeIf(pos -> pos.equals(current));
                        state = State.SCANNING;
                        return;
                    }
                } else {
                    jumpAttempts = 0;
                    lastJumpTarget = current;
                }

                client.options.jumpKey.setPressed(true);
                jumpTicks = 0;
                jumpCooldown = 8;
                apexTimer.reset();

                BlockPos jumpTarget = BlockScanner.findOptimalTarget(
                    client.player, queue, 6.0);
                if (jumpTarget != null) {
                    current = jumpTarget;
                    lookHelper.startLookAt(client.player, current);
                }
                state = State.WAITING_FOR_APEX;
            }

            // ── WAITING_FOR_APEX ─────────────────────────────────────────
            case WAITING_FOR_APEX -> {
                client.options.jumpKey.setPressed(false);
                jumpTicks++;

                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    client.options.attackKey.setPressed(false);
                    BlockScanner.resetNearbyCache();
                    navigator.reset(client);
                    navigator.start();
                    state = State.NAVIGATING;
                    return;
                }

                // Every few ticks, re-evaluate the best target mid-air
                // (player position changes rapidly, so re-pick often)
                if (jumpTicks % 2 == 0 || current == null) {
                    BlockPos airTarget = BlockScanner.findOptimalTarget(
                        client.player, queue, 5.5);
                    if (airTarget != null) {
                        if (!airTarget.equals(current)) {
                            current = airTarget;
                        }
                    }
                }

                // Use trackTarget for smooth continuous aim (not slow eased lookAt)
                if (current != null) {
                    BlockState apexBs = client.world.getBlockState(current);
                    if (apexBs.isAir()) {
                        // Block broken mid-air — immediately retarget
                        consecutiveJumps = 0;
                        queue.remove(current);
                        BlockPos next = BlockScanner.findOptimalTarget(
                            client.player, queue, 5.5);
                        if (next != null) {
                            current = next;
                        } else {
                            current = null;
                        }
                    }
                }

                if (current != null) {
                    boolean aimOk = lookHelper.trackTarget(client.player, current);
                    double dist = client.player.getEyePos()
                        .distanceTo(Vec3d.ofCenter(current));

                    if (aimOk && dist <= 5.5 && isValidTarget(client, current)) {
                        client.options.attackKey.setPressed(true);
                        client.player.swingHand(Hand.MAIN_HAND);
                    } else {
                        client.options.attackKey.setPressed(false);
                    }
                } else {
                    client.options.attackKey.setPressed(false);
                }

                // Land or timeout
                if ((client.player.isOnGround() && jumpTicks > 5)
                        || jumpTicks >= 22) {
                    client.options.attackKey.setPressed(false);
                    jumpTicks = 0;
                    settleTimer.reset();
                    state = State.SETTLING;
                }
            }

            // ── WALKING ──────────────────────────────────────────────────
            case WALKING -> {
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    client.options.forwardKey.setPressed(false);
                    client.options.sprintKey.setPressed(false);
                    BlockScanner.resetNearbyCache();
                    navigator.reset(client);
                    navigator.start();
                    state = State.NAVIGATING;
                    return;
                }

                BlockPos target = BlockScanner.findNearest(client.player, 20.0);
                if (target == null) {
                    client.options.forwardKey.setPressed(false);
                    client.options.sprintKey.setPressed(false);
                    BlockScanner.resetNearbyCache();
                    navigator.reset(client);
                    navigator.start();
                    state = State.NAVIGATING;
                    return;
                }

                double dx = (target.getX() + 0.5) - client.player.getX();
                double dz = (target.getZ() + 0.5) - client.player.getZ();
                double xzDist = Math.sqrt(dx * dx + dz * dz);

                if (xzDist < 2.5) {
                    client.options.forwardKey.setPressed(false);
                    client.options.sprintKey.setPressed(false);

                    BlockPos reachable = BlockScanner.findOptimalTarget(
                        client.player, queue, REACH);
                    if (reachable != null) {
                        state = State.SCANNING;
                    } else {
                        current = BlockScanner.findOptimalTarget(
                            client.player, queue, 20.0);
                        if (current != null) {
                            lookHelper.startLookAt(client.player, current);
                        }
                        state = State.THROWING;
                    }
                    return;
                }

                float targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                float currentYaw = client.player.getYaw();
                float yawDelta = targetYaw - currentYaw;
                while (yawDelta > 180) yawDelta -= 360;
                while (yawDelta < -180) yawDelta += 360;
                client.player.setYaw(currentYaw + yawDelta * 0.3f);

                client.options.forwardKey.setPressed(true);
                client.options.sprintKey.setPressed(true);

                // Stuck detection
                BlockPos walkPos = client.player.getBlockPos();
                if (walkPos.equals(lastWalkPos)) {
                    walkStuckTicks++;
                    if (walkStuckTicks > 15) {
                        client.options.jumpKey.setPressed(true);
                    }
                    if (walkStuckTicks > 40) {
                        client.options.forwardKey.setPressed(false);
                        client.options.sprintKey.setPressed(false);
                        client.options.jumpKey.setPressed(false);
                        walkStuckTicks = 0;
                        state = State.SCANNING;
                    }
                } else {
                    walkStuckTicks = 0;
                    client.options.jumpKey.setPressed(false);
                }
                lastWalkPos = walkPos;
            }

            // ── THROWING ─────────────────────────────────────────────────
            case THROWING -> {
                if (throwHoldTicks > 0) {
                    throwHoldTicks++;
                    if (throwHoldTicks <= 3) {
                        client.options.useKey.setPressed(true);
                        return;
                    }
                    client.options.useKey.setPressed(false);
                    throwHoldTicks = 0;
                    AutoTreeFeller.LOGGER.info("[ATF] Threw axe at {}", current);
                    justThrew = true;
                    throwSettleTimer.reset();
                    settleTimer.reset();
                    state = State.SETTLING;
                    return;
                }

                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    BlockScanner.resetNearbyCache();
                    navigator.reset(client);
                    navigator.start();
                    state = State.NAVIGATING;
                    return;
                }

                BlockPos throwTarget = BlockScanner.findOptimalTarget(
                    client.player, queue, 20.0);
                if (throwTarget == null) {
                    BlockScanner.resetNearbyCache();
                    navigator.reset(client);
                    navigator.start();
                    state = State.NAVIGATING;
                    return;
                }

                if (!throwTarget.equals(current)) {
                    current = throwTarget;
                    lookHelper.startLookAt(client.player, current);
                }

                boolean locked = lookHelper.tick(client.player);
                if (!locked) return;

                Vec3d eyes = client.player.getEyePos();
                Vec3d toBlock = Vec3d.ofCenter(throwTarget)
                    .subtract(eyes).normalize();
                Vec3d lookVec = client.player.getRotationVec(1.0f);
                if (lookVec.dotProduct(toBlock) < 0.85) return;

                client.options.useKey.setPressed(true);
                throwHoldTicks = 1;
            }

            case NAVIGATING -> {
                navigator.tick(client);
                if (navigator.hasArrived()) {
                    navigator.reset(client);
                    firstTarget = true;
                    current = null;
                    consecutiveJumps = 0;
                    scanFailures = 0;
                    state = State.SCANNING;
                    AutoTreeFeller.LOGGER.info(
                        "[ATF] Arrived at next tree, starting break");
                    return;
                }
                if (navigator.isIdle() && navigator.hasSearched()) {
                    state = State.IDLE;
                    AutoTreeFeller.LOGGER.info("[ATF] No more trees found");
                }
            }

            case IDLE -> {}
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────
    private boolean isValidTarget(MinecraftClient client, BlockPos pos) {
        if (pos == null || client.world == null) return false;
        return client.world.getBlockState(pos).getBlock()
            == Blocks.STRIPPED_SPRUCE_WOOD;
    }

    private void releaseAll(MinecraftClient client) {
        client.options.attackKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.useKey.setPressed(false);
    }
}
