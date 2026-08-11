package net.mokich.panoptic.config;

import net.mokich.panoptic.config.Perms.Feature;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class PanopticConfig {
    public static final ModConfigSpec.BooleanValue SINGLEPLAYER_OPEN_ACCESS;
    public static final ModConfigSpec SPEC;

    private static final Map<Feature, ModConfigSpec.BooleanValue> FEATURES = new EnumMap<>(Feature.class);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("permissions");
        SINGLEPLAYER_OPEN_ACCESS = b
                .comment("singleplayer_open_access let players without OP use all viewing interface in singleplayer/LAN")
                .define("singleplayer_open_access", false);
        b.pop();
        b.push("features");
        feature(b, Feature.INSPECTOR, "inspector");
        feature(b, Feature.SEED_VIEW, "seed_view");
        feature(b, Feature.SEED_STRUCTURES, "seed_structures");
        feature(b, Feature.TRADE, "trade");
        feature(b, Feature.SCREENS, "screens");
        b.pop();
        SPEC = b.build();
    }

    private PanopticConfig() {
    }

    private static void feature(ModConfigSpec.Builder b, Feature f, String key) {
        FEATURES.put(f, b.define(key, false));
    }

    public static boolean openAccess() {
        try {
            return SINGLEPLAYER_OPEN_ACCESS.get();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean featureOpen(Feature f) {
        ModConfigSpec.BooleanValue value = FEATURES.get(f);
        if (value == null) {
            return false;
        }
        try {
            return value.get();
        } catch (Throwable t) {
            return false;
        }
    }
}