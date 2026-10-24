package net.mokich.panoptic.config;

import net.fabricmc.loader.api.FabricLoader;
import net.mokich.panoptic.config.Perms.Feature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class PanopticConfig {
    private static final Map<Feature, String> KEYS = new EnumMap<>(Feature.class);
    private static final Map<String, Boolean> VALUES = new HashMap<>();

    private static boolean loaded;

    static {
        KEYS.put(Feature.INSPECTOR, "inspector");
        KEYS.put(Feature.SEED_VIEW, "seed_view");
        KEYS.put(Feature.SEED_STRUCTURES, "seed_structures");
        KEYS.put(Feature.TRADE, "trade");
        KEYS.put(Feature.SCREENS, "screens");
    }

    private PanopticConfig() {
    }

    public static boolean openAccess() {
        return flag("singleplayer_open_access");
    }

    public static boolean featureOpen(Feature f) {
        String key = KEYS.get(f);
        return key != null && flag(key);
    }

    private static boolean flag(String key) {
        load();
        Boolean value = VALUES.get(key);
        return value != null && value;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("panoptic.txt");
            if (Files.exists(path)) {
                for (String line : Files.readAllLines(path)) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        VALUES.put(line.substring(0, eq).trim(),
                                line.substring(eq + 1).trim().equalsIgnoreCase("true"));
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            append(sb, "singleplayer_open_access");
            for (Feature f : Feature.values()) {
                String key = KEYS.get(f);
                if (key != null) {
                    append(sb, key);
                }
            }
            Files.writeString(path, sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void append(StringBuilder sb, String key) {
        Boolean value = VALUES.get(key);
        boolean flag = value != null && value;
        VALUES.put(key, flag);
        sb.append(key).append('=').append(flag).append('\n');
    }
}