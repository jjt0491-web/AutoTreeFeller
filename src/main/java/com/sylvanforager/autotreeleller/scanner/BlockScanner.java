package com.sylvanforager.autotreeleller.scanner;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.*;

public class BlockScanner {
 private static final int HORIZONTAL_RADIUS = 8;
 private static final int HEIGHT_REACH = 25;

 public static Queue<BlockPos> scan(ClientPlayerEntity player) {
 List<BlockPos> blocks = new ArrayList<>();
 BlockPos origin = player.getBlockPos();
 MinecraftClient client = MinecraftClient.getInstance();

 for (int y = -2; y <= HEIGHT_REACH; y++) {
 int radius = HORIZONTAL_RADIUS + (y > 0 ? y / 6 : 0);
 for (int x = -radius; x <= radius; x++) {
 for (int z = -radius; z <= radius; z++) {
 BlockPos check = origin.add(x, y, z);
 if (client.world.getBlockState(check).getBlock()
 == Blocks.STRIPPED_SPRUCE_WOOD) {
 blocks.add(check);
 }
 }
 }
 }

 blocks.sort((a, b) -> {
 boolean aVis = hasLineOfSight(player, a);
 boolean bVis = hasLineOfSight(player, b);
 if (aVis && !bVis) return -1;
 if (!aVis && bVis) return 1;
 if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
 double da = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(a));
 double db = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(b));
 return Double.compare(da, db);
 });

 return new LinkedList<>(blocks);
 }

 public static BlockPos findLowest(ClientPlayerEntity player,
 Queue<BlockPos> queue) {
 return queue.stream()
 .filter(p -> !MinecraftClient.getInstance().world
 .getBlockState(p).isAir())
 .filter(p -> hasLineOfSight(player, p))
 .min(Comparator.comparingInt(BlockPos::getY))
 .orElse(null);
 }

 public static BlockPos findOptimalTarget(ClientPlayerEntity player,
 Queue<BlockPos> queue, double maxDist) {
 List<BlockPos> candidates = new ArrayList<>();
 MinecraftClient client = MinecraftClient.getInstance();

 for (BlockPos pos : queue) {
 if (client.world.getBlockState(pos).isAir()) continue;
 double dist = player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
 if (dist <= maxDist && hasLineOfSight(player, pos)) {
 candidates.add(pos);
 }
 }

 if (candidates.isEmpty()) return findNearest(player, maxDist);

 BlockPos best = null;
 int bestScore = -1;
 for (BlockPos candidate : candidates) {
 int score = 0;
 for (BlockPos other : candidates) {
 if (!other.equals(candidate)
 && candidate.getSquaredDistance(other) <= 16) {
 score++;
 }
 }
 if (score > bestScore) {
 bestScore = score;
 best = candidate;
 }
 }
 return best != null ? best : candidates.get(0);
 }

 public static BlockPos findNearest(ClientPlayerEntity player, double maxDist) {
 BlockPos origin = player.getBlockPos();
 BlockPos nearest = null;
 double nearestDist = Double.MAX_VALUE;
 int r = (int) Math.ceil(maxDist);
 MinecraftClient client = MinecraftClient.getInstance();

 for (int x = -r; x <= r; x++) {
 for (int y = -r; y <= r; y++) {
 for (int z = -r; z <= r; z++) {
 BlockPos check = origin.add(x, y, z);
 if (client.world.getBlockState(check).getBlock()
 != Blocks.STRIPPED_SPRUCE_WOOD) continue;
 double d = player.getEyePos()
 .distanceTo(Vec3d.ofCenter(check));
 if (d > maxDist) continue;
 if (!hasLineOfSight(player, check)) continue;
 if (d < nearestDist) {
 nearestDist = d;
 nearest = check;
 }
 }
 }
 }
 return nearest;
 }

 public static boolean hasAnyNearby(ClientPlayerEntity player, double radius) {
 BlockPos origin = player.getBlockPos();
 int r = (int) Math.ceil(radius);
 MinecraftClient client = MinecraftClient.getInstance();
 for (int x = -r; x <= r; x++) {
 for (int y = -5; y <= 25; y++) {
 for (int z = -r; z <= r; z++) {
 if (client.world.getBlockState(origin.add(x, y, z))
 .getBlock() == Blocks.STRIPPED_SPRUCE_WOOD) {
 return true;
 }
 }
 }
 }
 return false;
 }

 public static boolean hasLineOfSight(ClientPlayerEntity player, BlockPos pos) {
 MinecraftClient client = MinecraftClient.getInstance();
 if (client.world == null) return false;
 BlockHitResult hit = client.world.raycast(new RaycastContext(
 player.getEyePos(),
 Vec3d.ofCenter(pos),
 RaycastContext.ShapeType.COLLIDER,
 RaycastContext.FluidHandling.NONE,
 player));
 return hit.getType() != HitResult.Type.BLOCK
 || hit.getBlockPos().equals(pos);
 }
}