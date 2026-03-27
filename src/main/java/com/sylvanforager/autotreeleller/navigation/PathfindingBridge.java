package com.sylvanforager.autotreeleller.navigation;

import net.minecraft.util.math.BlockPos;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public class PathfindingBridge {
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String ext = os.contains("win") ? "dll"
                : os.contains("mac") ? "dylib" : "so";
            String prefix = os.contains("win") ? "" : "lib";
            String name = prefix + "atf_pathfinding." + ext;
            InputStream in = PathfindingBridge.class
                .getResourceAsStream("/natives/" + name);
            Path tmp = Files.createTempFile("atf_pathfinding", "." + ext);
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            System.load(tmp.toAbsolutePath().toString());
            loaded = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Rust pathfinding lib", e);
        }
    }

    public static native int[] findPath(int[] walkableBlocks,
        int[] start, int[] goal);

    public static List<BlockPos> computePath(List<BlockPos> walkable,
        BlockPos start, BlockPos goal) {
        load();
        int[] walkArr = new int[walkable.size() * 3];
        for (int i = 0; i < walkable.size(); i++) {
            walkArr[i*3] = walkable.get(i).getX();
            walkArr[i*3+1] = walkable.get(i).getY();
            walkArr[i*3+2] = walkable.get(i).getZ();
        }
        int[] s = {start.getX(), start.getY(), start.getZ()};
        int[] g = {goal.getX(), goal.getY(), goal.getZ()};
        int[] raw = findPath(walkArr, s, g);
        List<BlockPos> path = new ArrayList<>();
        for (int i = 0; i + 2 < raw.length; i += 3) {
            path.add(new BlockPos(raw[i], raw[i+1], raw[i+2]));
        }
        return path;
    }
}