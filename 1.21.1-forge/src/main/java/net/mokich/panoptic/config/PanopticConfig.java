package net.mokich.panoptic.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PanopticConfig {
    public static final ForgeConfigSpec.BooleanValue SINGLEPLAYER_OPEN_ACCESS;
    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
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