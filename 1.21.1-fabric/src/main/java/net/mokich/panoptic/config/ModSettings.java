package net.mokich.panoptic.config;

import net.mokich.panoptic.api.ui.GuiStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class ModSettings {
    public static final double[] ZOOM_STEPS = {
            0.00390625, 0.0078125, 0.015625, 0.03125, 0.0625, 0.125, 0.25, 0.5,
            1.0, 2.0, 4.0, 8.0, 16.0, 32.0
    };

    private static final Map<String, Double> VALUES = new LinkedHashMap<>();
    private static boolean loaded;

    public static final String MAP_MIN_ZOOM = "map_min_zoom";
    public static final String MAP_MAX_ZOOM = "map_max_zoom";
    public static final String STRUCT_MIN_ZOOM = "struct_min_zoom";
    public static final String LOG_MAX_LINES = "log_max_lines";
    public static final String WHEEL_HOLD_MS = "wheel_hold_ms";
    public static final String WHEEL_PARTICLES = "wheel_particles";
    public static final String UI_SOUNDS = "ui_sounds";
    public static final String THEME_R = "theme_r";
    public static final String THEME_G = "theme_g";
    public static final String THEME_B = "theme_b";

    private ModSettings() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("panoptic").resolve("settings.txt");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        defaults();
        try {
            Path p = file();
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p)) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    if (!VALUES.containsKey(key)) {
                        continue;
                    }
                    try {
                        VALUES.put(key, Double.parseDouble(line.substring(eq + 1).trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        applyThemeNow();
    }

    private static void defaults() {
        VALUES.put(MAP_MIN_ZOOM, 0.015625);
        VALUES.put(MAP_MAX_ZOOM, 8.0);
        VALUES.put(STRUCT_MIN_ZOOM, 0.0625);
        VALUES.put(LOG_MAX_LINES, 20000.0);
        VALUES.put(WHEEL_HOLD_MS, 60.0);
        VALUES.put(WHEEL_PARTICLES, 1.0);
        VALUES.put(UI_SOUNDS, 1.0);
        VALUES.put(THEME_R, 232.0);
        VALUES.put(THEME_G, 192.0);
        VALUES.put(THEME_B, 108.0);
    }

    public static void applyThemeNow() {
        GuiStyle.applyTheme(getInt(THEME_R), getInt(THEME_G), getInt(THEME_B));
    }
    public static void resetAll() {
        ensureLoaded();
        defaults();
        save();
        applyThemeNow();
    }

    private static void save() {
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Double> e : VALUES.entrySet()) {
                lines.add(e.getKey() + "=" + e.getValue());
            }
            Files.createDirectories(file().getParent());
            Files.write(file(), lines);
        } catch (Exception ignored) {
        }
    }

    public static double get(String key) {
        ensureLoaded();
        return VALUES.getOrDefault(key, 0.0);
    }

    public static int getInt(String key) {
        return (int) Math.round(get(key));
    }
    public static boolean getBool(String key) {
        return get(key) > 0.5;
    }

    public static void set(String key, double value) {
        ensureLoaded();
        VALUES.put(key, value);
        save();
    }

    public static void toggle(String key) {
        set(key, getBool(key) ? 0.0 : 1.0);
    }
    public static double mapMinZoom() {
        return get(MAP_MIN_ZOOM);
    }
    public static double mapMaxZoom() {
        return Math.max(get(MAP_MAX_ZOOM), get(MAP_MIN_ZOOM) * 2);
    }

    public static void stepZoom(String key, int dir) {
        double cur = get(key);
        int idx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < ZOOM_STEPS.length; i++) {
            double d = Math.abs(ZOOM_STEPS[i] - cur);
            if (d < best) {
                best = d;
                idx = i;
            }
        }
        int next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, idx + dir));
        set(key, ZOOM_STEPS[next]);
    }

    public static String zoomLabel(double zoom) {
        if (zoom >= 1.0) {
            return (int) zoom + "x";
        }
        return "1/" + (int) Math.round(1.0 / zoom) + "x";
    }
}