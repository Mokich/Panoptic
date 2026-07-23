package net.mokich.panoptic.screen;

import net.minecraft.resources.ResourceLocation;

public final class GmtLogo {

    public static final int RINGS = 3;

    public static final ResourceLocation CORE_TEX =
            ResourceLocation.fromNamespaceAndPath("panoptic", "textures/gui/logo_core.png");

    private static final ResourceLocation[] RING_TEX = {
            ResourceLocation.fromNamespaceAndPath("panoptic", "textures/gui/logo_ring0.png"),
            ResourceLocation.fromNamespaceAndPath("panoptic", "textures/gui/logo_ring1.png"),
            ResourceLocation.fromNamespaceAndPath("panoptic", "textures/gui/logo_ring2.png")
    };

    public static final float SLIT_HALF = 10.0F / 256.0F;
    public static final float CORE_RADIUS = 16.0F / 256.0F;

    private GmtLogo() {
    }

    public static ResourceLocation ringTexture(int i) {
        return RING_TEX[i];
    }
}