package com.sylvanforager.autotreeleller.render;

import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.List;

/**
 * Renders the walking path (line + nodes) and etherwarp target/candidates as
 * world-space overlays using the 1.21.11 Gizmo API.
 *
 * Data is written by TreeNavigator / TreeClusterFinder each tick;
 * drawGizmos() is called from WorldRendererMixin.onCollectGizmos() once per frame.
 */
public class OverlayRenderer {

    // ── Shared state (written by navigation logic, read by renderer) ──────
    /** Current A* walking path; null when not path-walking. */
    public static volatile List<BlockPos> walkPath = null;
    /** Index of the next waypoint being walked toward. */
    public static volatile int walkPathIndex = 0;
    /** Selected etherwarp landing block (magenta). */
    public static volatile BlockPos etherwarpSelected = null;
    /** All valid etherwarp candidate blocks found this scan (cyan). */
    public static volatile List<BlockPos> etherwarpCandidates = null;

    // ARGB colors
    private static final int COLOR_NODE_DONE     = 0x804DDB4D; // dim green
    private static final int COLOR_NODE_CURRENT  = 0xFFFFFF26; // bright yellow
    private static final int COLOR_NODE_UPCOMING = 0x8033B3FF; // blue
    private static final int COLOR_LINE          = 0x99CCCCCC; // light grey
    private static final int COLOR_EW_CANDIDATE  = 0x8000E5FF; // cyan
    private static final int COLOR_EW_SELECTED   = 0xFFFF00FF; // magenta

    /**
     * Called inside WorldRendererMixin.onCollectGizmos() while a GizmoCollector
     * is active on the current thread. All GizmoDrawing calls go to that collector.
     */
    public static void drawGizmos() {
        List<BlockPos> path       = walkPath;
        int            pathIdx    = walkPathIndex;
        BlockPos       ewSelected = etherwarpSelected;
        List<BlockPos> ewCands    = etherwarpCandidates;

        boolean hasPath    = path != null && !path.isEmpty();
        boolean hasEw      = ewSelected != null
            || (ewCands != null && !ewCands.isEmpty());

        if (!hasPath && !hasEw) return;

        // ── Walk path ─────────────────────────────────────────────────────
        if (hasPath) {
            for (int i = 0; i < path.size(); i++) {
                BlockPos node = path.get(i);
                boolean done    = i < pathIdx;
                boolean current = i == pathIdx;

                int nodeColor;
                if      (done)    nodeColor = COLOR_NODE_DONE;
                else if (current) nodeColor = COLOR_NODE_CURRENT;
                else              nodeColor = COLOR_NODE_UPCOMING;

                // Node: outline box
                GizmoDrawing.box(node, DrawStyle.stroked(nodeColor, 2.5f));

                // Line to next node (skip for completed nodes for cleanliness)
                if (!done && i + 1 < path.size()) {
                    Vec3d from = Vec3d.ofCenter(node);
                    Vec3d to   = Vec3d.ofCenter(path.get(i + 1));
                    GizmoDrawing.line(from, to, COLOR_LINE, 1.5f);
                }
            }
        }

        // ── Etherwarp candidates (cyan) ───────────────────────────────────
        if (ewCands != null) {
            for (BlockPos c : ewCands) {
                if (c.equals(ewSelected)) continue; // drawn separately
                GizmoDrawing.box(c, DrawStyle.stroked(COLOR_EW_CANDIDATE, 2.0f));
            }
        }

        // ── Selected etherwarp target (magenta, filled + stroked) ─────────
        if (ewSelected != null) {
            GizmoDrawing.box(
                ewSelected,
                DrawStyle.filledAndStroked(COLOR_EW_SELECTED, 3.0f, 0x33FF00FF)
            );
        }
    }

    /** Clear all overlay data (called when macro is toggled off). */
    public static void clear() {
        walkPath            = null;
        walkPathIndex       = 0;
        etherwarpSelected   = null;
        etherwarpCandidates = null;
    }
}
