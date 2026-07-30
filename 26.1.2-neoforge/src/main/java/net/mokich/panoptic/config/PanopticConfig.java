package net.mokich.panoptic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PanopticConfig {
    public static final ModConfigSpec.BooleanValue SINGLEPLAYER_OPEN_ACCESS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("permissions");
        SINGLEPLAYER_OPEN_ACCESS = b
                .comment("singleplayer_open_access let players without OP use all viewing interface in singleplayer/LAN")
                .define("singleplayer_open_access", false);
        b.pop();
        SPEC = b.build();
    }

    private PanopticConfig() {
    }

    public static boolean openAccess() {
        try {
            return SINGLEPLAYER_OPEN_ACCESS.get();
        } catch (Throwable t) {
            return false;
        }
    }
}