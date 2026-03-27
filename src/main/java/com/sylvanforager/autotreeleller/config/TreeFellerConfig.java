package com.sylvanforager.autotreeleller.config;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.util.Properties;

public class TreeFellerConfig {
    // Config values
    public int scanRadius = 5;
    public int lookSettleTicks = 2;
    public float reachThreshold = 4.5f;
    public int maxQueueSize = 64;

    private static final String CONFIG_FILE = "auto-tree-feller.properties";

    public void load() {
        try {
            File file = new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_FILE);
            if (file.exists()) {
                Properties props = new Properties();
                FileInputStream fis = new FileInputStream(file);
                props.load(fis);
                fis.close();

                scanRadius = Integer.parseInt(props.getProperty("scanRadius", "5"));
                lookSettleTicks = Integer.parseInt(props.getProperty("lookSettleTicks", "2"));
                reachThreshold = Float.parseFloat(props.getProperty("reachThreshold", "4.5"));
                maxQueueSize = Integer.parseInt(props.getProperty("maxQueueSize", "64"));
            }
        } catch (Exception e) {
            // Use defaults
        }
    }

    public void save() {
        try {
            File file = new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_FILE);
            Properties props = new Properties();
            props.setProperty("scanRadius", String.valueOf(scanRadius));
            props.setProperty("lookSettleTicks", String.valueOf(lookSettleTicks));
            props.setProperty("reachThreshold", String.valueOf(reachThreshold));
            props.setProperty("maxQueueSize", String.valueOf(maxQueueSize));

            FileOutputStream fos = new FileOutputStream(file);
            props.store(fos, "Auto Tree Feller Config");
            fos.close();
        } catch (Exception e) {
            // Ignore
        }
    }
}