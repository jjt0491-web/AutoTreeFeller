package com.sylvanforager.autotreeleller;

import net.fabricmc.api.ClientModInitializer;
import org.lwjgl.glfw.GLFW;
import com.sylvanforager.autotreeleller.config.TreeFellerConfig;
import com.sylvanforager.autotreeleller.scanner.BlockScanner;
import com.sylvanforager.autotreeleller.sequencer.BreakSequencer;
import com.sylvanforager.autotreeleller.breaker.BlockBreaker;
import com.sylvanforager.autotreeleller.reach.ReachChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTreeFeller implements ClientModInitializer {
    public static final String MOD_ID = "auto-tree-feller";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static TreeFellerConfig config;

    // Modules
    public static BlockScanner blockScanner;
    public static BreakSequencer breakSequencer;
    public static BlockBreaker blockBreaker;
    public static ReachChecker reachChecker;

    // Key state for R key
    public static boolean rKeyPressed = false;

    @Override
    public void onInitializeClient() {
        breakSequencer = new BreakSequencer();
        
        LOGGER.info("Auto Tree Feller starting...");

        config = new TreeFellerConfig();
        config.load();

        blockScanner = new BlockScanner();
        blockBreaker = new BlockBreaker();
        reachChecker = new ReachChecker();

        LOGGER.info("Auto Tree Feller initialized");
    }

    public static TreeFellerConfig getConfig() {
        return config;
    }
}