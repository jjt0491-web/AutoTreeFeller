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

    // ── Escape counters ──────────────────────────────────────────────────
    private int consecutiveJumps = 0;
    private static final int MAX_CONSECUTIVE_JUMPS = 2; // jump max twice, then walk/navigate

    private static final int MAX_HOLD_TICKS = 40;
    private static final double REACH = 4.5;

    private final PlayerLookHelper lookHelper = new PlayerLookHelper();
    private final HumanTimer settleTimer = new HumanTimer(1, 2);
    private final HumanTimer apexTimer = new HumanTimer(8, 13);
    private final HumanTimer throwSettleTimer = new HumanTimer(10, 15);
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
        state = State.SCANNING;
        AutoTreeFeller.LOGGER.info("[ATF] Enabled");
    }

    public boolean isActive() { return state != State.IDLE; }
    public State getState() { return state; }

    // ── main tick ─────────────────────────────────────────────────────────
    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (jumpCooldown > 0) jumpCooldown--;

        // Purge broken blocks from queue every tick
        if (state != State.IDLE && state != State.SCANNING) {
            queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
        }

        // If no tree blocks nearby at all → navigate to next tree
        if (state != State.IDLE && state != State.NAVIGATING
                && state != State.SCANNING) {
            if (!BlockScanner.hasAnyNearby(client.player, 20.0)) {
                releaseAll(client);
                queue.clear();
                BlockScanner.resetNearbyCache();
                goNavigate(client, "No blocks nearby");
                return;
            }
        }

        switch (state) {

            // ── SCANNING ─────────────────────────────────────────────────
            case SCANNING -> {
                queue = BlockScanner.scan(client.player);
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());

                if (queue.isEmpty()) {
                    BlockScanner.resetNearbyCache();
                    goNavigate(client, "Tree complete");
                    return;
                }

                BlockPos next = firstTarget
                    ? BlockScanner.findLowest(client.player, queue)
                    : BlockScanner.findOptimalTarget(client.player, queue, REACH);

                if (next != null) {
                    // Found a reachable target — go break it
                    firstTarget = false;
                    consecutiveJumps = 0;
                    current = next;
                    lookHelper.startLookAt(client.player, current);
                    state = State.TURNING;
                    return;
                }

                // ── Blocks exist but none in reach — decide action ────────
                // Priority: walk > jump (only if blocks above) > navigate

                // 1. Try walking toward nearest block
                BlockPos nearest = BlockScanner.findNearest(client.player, 20.0);
                if (nearest != null) {
                    double xzDist = Math.sqrt(
                        Math.pow(nearest.getX() - client.player.getX(), 2) +
                        Math.pow(nearest.getZ() - client.player.getZ(), 2));

                    if (xzDist > 1.5) {
                        // Blocks are horizontally distant — walk toward them
                        state = State.WALKING;
                        return;
                    }

                    // 2. Blocks are close horizontally but above — jump
                    if (nearest.getY() > client.player.getBlockPos().getY() + 1
                            && consecutiveJumps < MAX_CONSECUTIVE_JUMPS
                            && client.player.isOnGround() && jumpCooldown == 0) {
                        BlockPos jumpTarget = BlockScanner.findOptimalTarget(
                            client.player, queue, 6.0);
                        if (jumpTarget != null) {
                            current = jumpTarget;
                            lookHelper.startLookAt(client.player, current);
                        }
                        state = State.JUMPING;
                        return;
                    }
                }

                // 3. Exhausted walk/jump options — navigate to next tree
                BlockScanner.resetNearbyCache();
                goNavigate(client, "Blocks unreachable, moving on");
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
                    queue.remove(current);
                    queue.removeIf(pos -> client.world.getBlockState(pos).isAir());

                    BlockPos next = BlockScanner.findOptimalTarget(
                        client.player, queue, REACH);

                    if (next != null) {
                        // Seamless: keep attack held, smooth-aim to next
                        current = next;
                        lookHelper.startLookAt(client.player, current);
                        breakHoldTicks = 0;
                        // Stay in BREAKING
                    } else {
                        client.options.attackKey.setPressed(false);
                        breakHoldTicks = 0;

                        if (queue.isEmpty()) {
                            BlockScanner.resetNearbyCache();
                            goNavigate(client, "Tree complete");
                        } else {
                            // Blocks remain but out of reach — re-scan to decide
                            state = State.SCANNING;
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

                    BlockPos reachable = BlockScanner.findOptimalTarget(
                        client.player, queue, REACH);
                    if (reachable != null) {
                        current = reachable;
                        lookHelper.startLookAt(client.player, current);
                        state = State.TURNING;
                        return;
                    }

                    // Not in reach — go back to SCANNING which will decide walk/jump/navigate
                    state = State.SCANNING;
                    return;
                }

                // ── Continuous aim tracking while holding attack ──────────
                boolean aimOk = lookHelper.trackTarget(client.player, current);

                if (!aimOk) {
                    Vec3d eyes = client.player.getEyePos();
                    Vec3d toBlock = Vec3d.ofCenter(current).subtract(eyes).normalize();
                    Vec3d lookVec = client.player.getRotationVec(1.0f);
                    if (lookVec.dotProduct(toBlock) < 0.55) {
                        client.options.attackKey.setPressed(false);
                        breakHoldTicks = 0;
                        lookHelper.startLookAt(client.player, current);
                        state = State.TURNING;
                        return;
                    }
                }

                // Safety cutoff
                if (breakHoldTicks >= MAX_HOLD_TICKS) {
                    client.options.attackKey.setPressed(false);
                    breakHoldTicks = 0;
                    queue.poll();
                    state = State.SCANNING;
                    return;
                }

                if (!isValidTarget(client, current)) {
                    client.options.attackKey.setPressed(false);
                    queue.poll();
                    state = State.SCANNING;
                    return;
                }

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

                if (current != null && current.equals(lastJumpTarget)) {
                    jumpAttempts++;
                    if (jumpAttempts >= 2) {
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
                    goNavigate(client, "Tree complete mid-air");
                    return;
                }

                // Re-evaluate target every 2 ticks
                if (jumpTicks % 2 == 0 || current == null) {
                    BlockPos airTarget = BlockScanner.findOptimalTarget(
                        client.player, queue, 5.5);
                    if (airTarget != null && !airTarget.equals(current)) {
                        current = airTarget;
                    }
                }

                if (current != null) {
                    BlockState apexBs = client.world.getBlockState(current);
                    if (apexBs.isAir()) {
                        consecutiveJumps = 0;
                        queue.remove(current);
                        BlockPos next = BlockScanner.findOptimalTarget(
                            client.player, queue, 5.5);
                        current = next; // may be null
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
                    state = State.SCANNING; // re-scan, don't settle
                }
            }

            // ── WALKING ──────────────────────────────────────────────────
            case WALKING -> {
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    releaseAll(client);
                    BlockScanner.resetNearbyCache();
                    goNavigate(client, "Tree complete while walking");
                    return;
                }

                BlockPos target = BlockScanner.findNearest(client.player, 20.0);
                if (target == null) {
                    releaseAll(client);
                    BlockScanner.resetNearbyCache();
                    goNavigate(client, "Lost target while walking");
                    return;
                }

                double dx = (target.getX() + 0.5) - client.player.getX();
                double dz = (target.getZ() + 0.5) - client.player.getZ();
                double xzDist = Math.sqrt(dx * dx + dz * dz);

                // Close enough — try to break
                if (xzDist < 2.5) {
                    client.options.forwardKey.setPressed(false);
                    client.options.sprintKey.setPressed(false);
                    state = State.SCANNING;
                    return;
                }

                // Face target and walk
                float targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                float currentYaw = client.player.getYaw();
                float yawDelta = targetYaw - currentYaw;
                while (yawDelta > 180) yawDelta -= 360;
                while (yawDelta < -180) yawDelta += 360;
                client.player.setYaw(currentYaw + yawDelta * 0.35f);

                client.options.forwardKey.setPressed(true);
                client.options.sprintKey.setPressed(true);

                // Stuck detection
                BlockPos walkPos = client.player.getBlockPos();
                if (walkPos.equals(lastWalkPos)) {
                    walkStuckTicks++;
                    if (walkStuckTicks > 12) {
                        client.options.jumpKey.setPressed(true);
                    }
                    if (walkStuckTicks > 35) {
                        // Can't walk there — go back to scanning
                        releaseAll(client);
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
                    state = State.SETTLING;
                    return;
                }

                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    BlockScanner.resetNearbyCache();
                    goNavigate(client, "Tree complete");
                    return;
                }

                BlockPos throwTarget = BlockScanner.findOptimalTarget(
                    client.player, queue, 20.0);
                if (throwTarget == null) {
                    BlockScanner.resetNearbyCache();
                    goNavigate(client, "No throw target");
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

            // ── NAVIGATING ───────────────────────────────────────────────
            case NAVIGATING -> {
                navigator.tick(client);
                if (navigator.hasArrived()) {
                    navigator.reset(client);
                    firstTarget = true;
                    current = null;
                    consecutiveJumps = 0;
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

    /** Central method to transition to NAVIGATING — resets state cleanly. */
    private void goNavigate(MinecraftClient client, String reason) {
        releaseAll(client);
        navigator.reset(client);
        navigator.start();
        state = State.NAVIGATING;
        consecutiveJumps = 0;
        AutoTreeFeller.LOGGER.info("[ATF] {} — navigating to next tree", reason);
    }

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
