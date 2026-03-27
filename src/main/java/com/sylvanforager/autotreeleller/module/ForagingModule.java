package com.sylvanforager.autotreeleller.module;

import org.lwjgl.glfw.GLFW;

public class ForagingModule {

    public enum TreeType {
        FIG("Fig"),
        MANGROVE("Mangrove"),
        LUSHILAC("Lushilac"),
        LUSHILAC_FIG("Lushilac + Fig");

        public final String display;
        TreeType(String d) { this.display = d; }
    }

    private static TreeType selectedTree  = TreeType.FIG;
    private static int      activationKey = GLFW.GLFW_KEY_F;

    public static TreeType getSelectedTree()          { return selectedTree; }
    public static void     setSelectedTree(TreeType t){ selectedTree = t; }
    public static int      getActivationKey()         { return activationKey; }
    public static void     setActivationKey(int key)  { activationKey = key; }

    /** Only Fig is wired up so far. */
    public static boolean isSupported(TreeType t) {
        return t == TreeType.FIG;
    }
}
