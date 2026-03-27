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
 private int preBreakDelay = 0;
 private int jumpAttempts = 0;
 private BlockPos lastJumpTarget = null;

 private static final int MAX_HOLD_TICKS = 40;
 private static final double REACH = 4.5;

 private final PlayerLookHelper lookHelper = new PlayerLookHelper();
 private final HumanTimer settleTimer = new HumanTimer(2, 4);
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

 // stop if no tree nearby (walked away)
 if (state != State.IDLE && state != State.WALKING
 && state != State.THROWING && state != State.SCANNING) {
 if (!BlockScanner.hasAnyNearby(client.player, 20.0)) {
 releaseAll(client);
 return;
 }
 }

 switch (state) {

 // ── SCANNING ─────────────────────────────────────────────────
 case SCANNING -> {
 queue = BlockScanner.scan(client.player);
 queue.removeIf(pos -> client.world.getBlockState(pos).isAir());

 if (queue.isEmpty()) {
 // tree done — navigate to next tree
 navigator.reset(client);
 navigator.start();
 state = State.NAVIGATING;
 AutoTreeFeller.LOGGER.info("[ATF] Tree complete, navigating to next");
 return;
 }

 BlockPos next = firstTarget
 ? BlockScanner.findLowest(client.player, queue)
 : BlockScanner.findOptimalTarget(client.player, queue, REACH);

 if (next == null) {
 // nothing in range — need to jump or walk
 state = State.JUMPING;
 return;
 }

 firstTarget = false;
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
 if (current == null || queue.isEmpty()) {
 state = State.SCANNING;
 return;
 }

 BlockState bs = client.world.getBlockState(current);

 if (bs.isAir()) {
 client.options.attackKey.setPressed(false);
 breakHoldTicks = 0;
 settleTimer.reset();
 state = State.SETTLING;
 return;
 }

 double eyeDist = client.player.getEyePos()
 .distanceTo(Vec3d.ofCenter(current));

 if (eyeDist > REACH) {
 client.options.attackKey.setPressed(false);
 breakHoldTicks = 0;

 // check if any block is reachable first
 BlockPos reachable = BlockScanner.findOptimalTarget(
 client.player, queue, REACH);
 if (reachable != null) {
 current = reachable;
 lookHelper.startLookAt(client.player, current);
 state = State.TURNING;
 return;
 }

 // try walking closer if slightly out of range
 BlockPos nearby = BlockScanner.findNearest(client.player, 7.0);
 if (nearby != null) {
 double xzDist = Math.sqrt(
 Math.pow(nearby.getX() - client.player.getX(), 2) +
 Math.pow(nearby.getZ() - client.player.getZ(), 2));
 if (xzDist > 2.0) {
 state = State.WALKING;
 return;
 }
 }

 // jump if on ground and cooldown expired
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

 // aim check
 Vec3d eyes = client.player.getEyePos();
 Vec3d toBlock = Vec3d.ofCenter(current).subtract(eyes).normalize();
 Vec3d lookVec = client.player.getRotationVec(1.0f);
 if (lookVec.dotProduct(toBlock) < 0.85) {
 client.options.attackKey.setPressed(false);
 breakHoldTicks = 0;
 state = State.TURNING;
 lookHelper.startLookAt(client.player, current);
 return;
 }

 // safety cutoff
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

 // pre-break human hesitation
 if (preBreakDelay > 0) {
 preBreakDelay--;
 return;
 }

 client.options.attackKey.setPressed(true);
 client.player.swingHand(Hand.MAIN_HAND);
 preBreakDelay = (int)(Math.random() * 3);
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
 // Stall detector — skip block if stuck jumping too many times
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

 // pre-aim at jump target immediately
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
 if (queue.isEmpty()) { state = State.IDLE; return; }

 // continuously find best target mid-air
 BlockPos airTarget = BlockScanner.findOptimalTarget(
 client.player, queue, 5.5);
 if (airTarget != null && !airTarget.equals(current)) {
 double switchDist = current != null
 ? Vec3d.ofCenter(airTarget)
 .distanceTo(Vec3d.ofCenter(current))
 : 999;
 if (switchDist > 1.5) {
 current = airTarget;
 lookHelper.startLookAt(client.player, current);
 }
 }

 boolean locked = lookHelper.tick(client.player);

 if (current != null && locked) {
 BlockState bs = client.world.getBlockState(current);
 if (bs.isAir()) {
 // broken — find next immediately
 BlockPos next = BlockScanner.findOptimalTarget(
 client.player, queue, 5.5);
 if (next != null) {
 current = next;
 lookHelper.startLookAt(client.player, current);
 }
 } else {
 double dist = client.player.getEyePos()
 .distanceTo(Vec3d.ofCenter(current));
 Vec3d eyes = client.player.getEyePos();
 Vec3d toBlock = Vec3d.ofCenter(current)
 .subtract(eyes).normalize();
 Vec3d lookVec = client.player.getRotationVec(1.0f);

 if (dist <= 5.5 && lookVec.dotProduct(toBlock) >= 0.85
 && isValidTarget(client, current)) {
 client.options.attackKey.setPressed(true);
 client.player.swingHand(Hand.MAIN_HAND);
 }
 }
 }

 // land or timeout
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
 if (queue.isEmpty()) { state = State.IDLE; return; }

 BlockPos target = BlockScanner.findNearest(client.player, 20.0);
 if (target == null) { state = State.IDLE; return; }

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
 // walked as close as possible — throw axe
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
 }

 // ── THROWING ─────────────────────────────────────────────────
 case THROWING -> {
 queue.removeIf(pos -> client.world.getBlockState(pos).isAir());
 if (queue.isEmpty()) { state = State.IDLE; return; }

 BlockPos throwTarget = BlockScanner.findOptimalTarget(
 client.player, queue, 20.0);
 if (throwTarget == null) { state = State.IDLE; return; }

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

 // simulate right-click to throw axe
 client.options.useKey.setPressed(true);
 client.options.useKey.setPressed(false);

 AutoTreeFeller.LOGGER.info("[ATF] Threw axe at {}", throwTarget);
 justThrew = true;
 throwSettleTimer.reset();
 settleTimer.reset();
 state = State.SETTLING;
 }

 case NAVIGATING -> {
 navigator.tick(client);
 if (navigator.hasArrived()) {
 navigator.reset(client);
 firstTarget = true;
 current = null;
 state = State.SCANNING;
 AutoTreeFeller.LOGGER.info("[ATF] Arrived at next tree, starting break");
 }
 if (navigator.isIdle()) {
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