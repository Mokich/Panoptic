package net.mokich.panoptic.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PanopticConfig {
    private static Boolean cached;

    private PanopticConfig() {
    }

    public static boolean openAccess() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    private static boolean load() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("panoptic.txt");
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p)) {
                    String t = line.trim();
                    if (t.startsWith("singleplayer_open_access")) {
                        return t.endsWith("true");
                    }
                }
            } else {
                Files.writeString(p, "singleplayer_open_access=false");
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}