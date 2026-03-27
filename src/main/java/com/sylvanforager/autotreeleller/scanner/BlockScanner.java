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
import java.util.Map;
import java.util.HashMap;

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

 // pre-compute LOS to avoid raycasts inside comparator
 Map<BlockPos, Boolean> losCache = new HashMap<>();
 Vec3d eyePos = player.getEyePos();
 for (BlockPos b : blocks) {
 losCache.put(b, hasLineOfSight(player, b));
 }

 blocks.sort((a, b) -> {
 boolean aVis = losCache.getOrDefault(a, false);
 boolean bVis = losCache.getOrDefault(b, false);
 if (aVis && !bVis) return -1;
 if (!aVis && bVis) return 1;
 if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
 double da = eyePos.squaredDistanceTo(Vec3d.ofCenter(a));
 double db = eyePos.squaredDistanceTo(Vec3d.ofCenter(b));
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

 if (candidates.isEmpty()) return null;
 if (candidates.size() == 1) return candidates.get(0);

 // O(n) scoring: prefer lowest Y, closest distance, with neighbor density bonus
 Vec3d eyePos = player.getEyePos();
 BlockPos best = null;
 double bestScore = Double.MAX_VALUE;
 for (BlockPos candidate : candidates) {
 double dist = eyePos.squaredDistanceTo(Vec3d.ofCenter(candidate));
 // lower Y is better (break bottom-up), closer is better
 double score = dist + candidate.getY() * 3.0;
 if (score < bestScore) {
 bestScore = score;
 best = candidate;
 }
 }
 return best;
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

 private static boolean cachedHasNearby = false;
 private static int nearbyCheckCooldown = 0;

 public static void resetNearbyCache() {
 nearbyCheckCooldown = 0;
 }

 public static boolean hasAnyNearby(ClientPlayerEntity player, double radius) {
 // only re-scan every 10 ticks (~0.5s) to avoid 52K block checks per tick
 if (nearbyCheckCooldown > 0) {
 nearbyCheckCooldown--;
 return cachedHasNearby;
 }
 nearbyCheckCooldown = 10;

 BlockPos origin = player.getBlockPos();
 int r = (int) Math.ceil(radius);
 MinecraftClient client = MinecraftClient.getInstance();
 for (int x = -r; x <= r; x++) {
 for (int y = -5; y <= 25; y++) {
 for (int z = -r; z <= r; z++) {
 if (client.world.getBlockState(origin.add(x, y, z))
 .getBlock() == Blocks.STRIPPED_SPRUCE_WOOD) {
 cachedHasNearby = true;
 return true;
 }
 }
 }
 }
 cachedHasNearby = false;
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