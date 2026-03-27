package com.sylvanforager.autotreeleller.sequencer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import com.sylvanforager.autotreeleller.AutoTreeFeller;
import com.sylvanforager.autotreeleller.look.PlayerLookHelper;
import com.sylvanforager.autotreeleller.scanner.BlockScanner;
import java.util.Queue;
import java.util.LinkedList;

public class BreakSequencer {

    public enum State {
        IDLE, SCANNING, TURNING, BREAKING, SETTLING, JUMPING, WAITING_FOR_APEX, WALKING, THROWING
    }

    private State state = State.IDLE;
    private Queue<BlockPos> queue = new LinkedList<>();
    private BlockPos current = null;
    private int settleTicks = 0;
    private int jumpTicks = 0;
    private int breakHoldTicks = 0;
    private int jumpCooldown = 0;
    private boolean justThrew = false;
    private static final int SETTLE_DURATION = 2;
    private static final int SETTLE_AFTER_THROW = 15;
    private static final int MAX_HOLD_TICKS = 40;
    private final PlayerLookHelper lookHelper = new PlayerLookHelper();

    private boolean isValidTarget(MinecraftClient client, BlockPos pos) {
        if (pos == null) return false;
        return client.world.getBlockState(pos).getBlock() == Blocks.STRIPPED_SPRUCE_WOOD;
    }

    private void checkQueueAndRescan(MinecraftClient client) {
        queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
        if (queue.isEmpty()) {
            Queue<BlockPos> rescan = BlockScanner.scan(client.player);
            rescan.removeIf(pos -> client.world.getBlockState(pos).isAir());
            if (rescan.isEmpty()) {
                state = State.IDLE;
                AutoTreeFeller.LOGGER.info("[SEQUENCER] Tree complete");
            } else {
                queue = rescan;
                state = State.SCANNING;
            }
        } else {
            state = State.SCANNING;
        }
    }

    private void releaseMovementKeys(MinecraftClient client) {
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.useKey.setPressed(false);
    }

    public void toggle() {
        if (state != State.IDLE) {
            state = State.IDLE;
            queue.clear();
            current = null;
            breakHoldTicks = 0;
            jumpTicks = 0;
            settleTicks = 0;
            jumpCooldown = 0;
            justThrew = false;
            MinecraftClient.getInstance().options.attackKey.setPressed(false);
            releaseMovementKeys(MinecraftClient.getInstance());
            lookHelper.reset();
            AutoTreeFeller.LOGGER.info("[SEQUENCER] Disabled");
            return;
        }
        state = State.SCANNING;
        AutoTreeFeller.LOGGER.info("[SEQUENCER] Enabled");
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public State getState() {
        return state;
    }

    public BlockPos getCurrentTarget() {
        return current;
    }

    public int getRemainingBlocks() {
        return queue.size();
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        if (jumpCooldown > 0) jumpCooldown--;

        if (state != State.IDLE && state != State.SCANNING && state != State.WALKING) {
            queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
            if (queue.isEmpty() && state != State.SETTLING) {
                checkQueueAndRescan(client);
                return;
            }
        }

        switch (state) {
            case SCANNING -> {
                releaseMovementKeys(client);
                queue = BlockScanner.scan(client.player);
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    checkQueueAndRescan(client);
                    return;
                }
                BlockPos next = current == null
                    ? BlockScanner.findLowest(client.player, queue)
                    : BlockScanner.findOptimalTarget(client.player, queue, 4.5);
                if (next == null) {
                    state = State.JUMPING;
                    return;
                }
                current = next;
                lookHelper.startLookAt(client.player, current);
                state = State.TURNING;
            }

            case TURNING -> {
                releaseMovementKeys(client);
                boolean locked = lookHelper.tick(client.player);
                if (locked) {
                    if (isValidTarget(client, current)) {
                        client.options.attackKey.setPressed(true);
                        client.player.swingHand(Hand.MAIN_HAND);
                        breakHoldTicks = 1;
                        state = State.BREAKING;
                    } else {
                        client.options.attackKey.setPressed(false);
                        queue.poll();
                        state = State.SCANNING;
                    }
                }
            }

            case BREAKING -> {
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    checkQueueAndRescan(client);
                    return;
                }
                BlockState blockState = client.world.getBlockState(current);
                if (blockState.isAir()) {
                    client.options.attackKey.setPressed(false);
                    breakHoldTicks = 0;
                    settleTicks = 0;
                    releaseMovementKeys(client);
                    state = State.SETTLING;
                    return;
                }

                double eyeDist = client.player.getEyePos().distanceTo(Vec3d.ofCenter(current));

                if (eyeDist > 4.5) {
                    client.options.attackKey.setPressed(false);
                    breakHoldTicks = 0;
                    releaseMovementKeys(client);

                    queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                    BlockPos anyReachable = BlockScanner.findOptimalTarget(client.player, queue, 4.5);
                    if (anyReachable != null) {
                        current = anyReachable;
                        lookHelper.startLookAt(client.player, current);
                        state = State.TURNING;
                        return;
                    }

                    BlockPos nearbyUnreachable = BlockScanner.findNearest(client.player, 6.0);
                    if (nearbyUnreachable != null) {
                        double xzDist = Math.sqrt(
                            Math.pow(nearbyUnreachable.getX() - client.player.getX(), 2) +
                            Math.pow(nearbyUnreachable.getZ() - client.player.getZ(), 2));
                        if (xzDist > 1.5) {
                            state = State.WALKING;
                            return;
                        }
                    }

                    if (eyeDist <= 5.5 && client.player.isOnGround()) {
                        double dx = (current.getX() + 0.5) - client.player.getX();
                        double dz = (current.getZ() + 0.5) - client.player.getZ();
                        float targetYaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
                        float currentYaw = client.player.getYaw();
                        float yawDelta = targetYaw - currentYaw;
                        while (yawDelta > 180) yawDelta -= 360;
                        while (yawDelta < -180) yawDelta += 360;
                        client.player.setYaw(currentYaw + yawDelta * 0.4f);
                        client.options.forwardKey.setPressed(true);
                        client.options.jumpKey.setPressed(true);
                        return;
                    }

                    if (eyeDist > 5.5) {
                        if (client.player.isOnGround()) {
                            if (jumpCooldown == 0) {
                                BlockPos jumpTarget = BlockScanner.findOptimalTarget(client.player, queue, 6.0);
                                if (jumpTarget != null) {
                                    current = jumpTarget;
                                    lookHelper.startLookAt(client.player, current);
                                }
                                jumpCooldown = 8;
                                state = State.JUMPING;
                                return;
                            }
                            state = State.WALKING;
                            return;
                        }
                    } else if (!client.player.isOnGround()) {
                        state = State.WAITING_FOR_APEX;
                    }
                    return;
                }

                client.options.forwardKey.setPressed(false);
                client.options.jumpKey.setPressed(false);

                if (breakHoldTicks >= MAX_HOLD_TICKS) {
                    client.options.attackKey.setPressed(false);
                    breakHoldTicks = 0;
                    queue.poll();
                    settleTicks = 0;
                    releaseMovementKeys(client);
                    state = State.SETTLING;
                    return;
                }

                if (isValidTarget(client, current)) {
                    client.options.attackKey.setPressed(true);
                    breakHoldTicks++;
                } else {
                    client.options.attackKey.setPressed(false);
                    queue.poll();
                    state = State.SCANNING;
                }
            }

            case SETTLING -> {
                releaseMovementKeys(client);
                client.options.attackKey.setPressed(false);
                int settleTarget = justThrew ? SETTLE_AFTER_THROW : SETTLE_DURATION;
                if (++settleTicks >= settleTarget) {
                    justThrew = false;
                    settleTicks = 0;
                    state = State.SCANNING;
                }
            }

            case JUMPING -> {
                releaseMovementKeys(client);
                client.options.jumpKey.setPressed(true);
                jumpTicks = 0;
                state = State.WAITING_FOR_APEX;
            }

            case WAITING_FOR_APEX -> {
                client.options.jumpKey.setPressed(false);
                if (jumpCooldown > 0) jumpCooldown--;
                jumpTicks++;

                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    checkQueueAndRescan(client);
                    return;
                }

                boolean locked = lookHelper.tick(client.player);

                if (current != null) {
                    double eyeDist = client.player.getEyePos().distanceTo(Vec3d.ofCenter(current));
                    BlockState bs = client.world.getBlockState(current);

                    if (bs.isAir()) {
                        BlockPos next = BlockScanner.findOptimalTarget(client.player, queue, 5.5);
                        if (next != null) {
                            current = next;
                            lookHelper.startLookAt(client.player, current);
                        }
                        return;
                    }

                    if (locked && eyeDist <= 5.0) {
                        Vec3d eyes = client.player.getEyePos();
                        Vec3d toBlock = Vec3d.ofCenter(current).subtract(eyes).normalize();
                        Vec3d lookVec = client.player.getRotationVec(1.0f);
                        if (lookVec.dotProduct(toBlock) >= 0.92 && isValidTarget(client, current)) {
                            client.options.attackKey.setPressed(true);
                            client.player.swingHand(Hand.MAIN_HAND);

                            queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                            BlockPos next = BlockScanner.findOptimalTarget(client.player, queue, 5.5);
                            if (next != null && !next.equals(current)) {
                                current = next;
                                lookHelper.startLookAt(client.player, current);
                            }
                        }
                    }
                }

                if (client.player.isOnGround() && jumpTicks > 5) {
                    client.options.attackKey.setPressed(false);
                    jumpTicks = 0;
                    settleTicks = 0;
                    releaseMovementKeys(client);
                    state = State.SETTLING;
                }

                if (jumpTicks >= 20) {
                    client.options.attackKey.setPressed(false);
                    jumpTicks = 0;
                    releaseMovementKeys(client);
                    state = State.SETTLING;
                }
            }

            case WALKING -> {
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    releaseMovementKeys(client);
                    checkQueueAndRescan(client);
                    return;
                }

                BlockPos closest = BlockScanner.findNearest(client.player, 20.0);
                if (closest == null) {
                    releaseMovementKeys(client);
                    state = State.IDLE;
                    return;
                }

                double dx = (closest.getX() + 0.5) - client.player.getX();
                double dz = (closest.getZ() + 0.5) - client.player.getZ();
                double xzDist = Math.sqrt(dx * dx + dz * dz);

                if (xzDist < 2.5) {
                    releaseMovementKeys(client);
                    BlockPos reachable = BlockScanner.findOptimalTarget(client.player, queue, 4.5);
                    if (reachable != null) {
                        state = State.SCANNING;
                    } else {
                        current = BlockScanner.findOptimalTarget(client.player, queue, 20.0);
                        if (current != null) lookHelper.startLookAt(client.player, current);
                        justThrew = false;
                        AutoTreeFeller.LOGGER.info("[SEQUENCER] Entering THROWING state, queue size: {}", queue.size());
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
            }

            case THROWING -> {
                queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
                if (queue.isEmpty()) {
                    checkQueueAndRescan(client);
                    return;
                }

                BlockPos throwTarget = BlockScanner.findOptimalTarget(client.player, queue, 20.0);
                if (throwTarget == null) {
                    checkQueueAndRescan(client);
                    return;
                }

                if (!throwTarget.equals(current)) {
                    current = throwTarget;
                    lookHelper.startLookAt(client.player, current);
                }

                boolean locked = lookHelper.tick(client.player);
                if (!locked) return;

                Vec3d eyes = client.player.getEyePos();
                Vec3d toBlock = Vec3d.ofCenter(throwTarget).subtract(eyes).normalize();
                Vec3d lookVec = client.player.getRotationVec(1.0f);
                if (lookVec.dotProduct(toBlock) < 0.92) return;

                // simulate right-click hold via key input
                client.options.useKey.setPressed(true);
                client.options.useKey.setPressed(false);

                AutoTreeFeller.LOGGER.info("[THROW] Threw axe at {}", throwTarget);
                justThrew = true;
                settleTicks = 0;
                state = State.SETTLING;
            }

            case IDLE -> {
                // do nothing
            }
        }
    }
}