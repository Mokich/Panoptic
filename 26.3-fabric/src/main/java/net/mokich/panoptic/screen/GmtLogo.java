package net.mokich.panoptic.screen;

import net.minecraft.resources.Identifier;

public final class GmtLogo {

    public static final int RINGS = 3;

    public static final Identifier CORE_TEX =
            Identifier.fromNamespaceAndPath("panoptic", "textures/gui/logo_core.png");

    private static final Identifier[] RING_TEX = {
            Identifier.fromNamespaceAndPath("panoptic", "textures/gui/logo_ring0.png"),
            Identifier.fromNamespaceAndPath("panoptic", "textures/gui/logo_ring1.png"),
            Identifier.fromNamespaceAndPath("panoptic", "textures/gui/logo_ring2.png")
    };

    public static final float SLIT_HALF = 10.0F / 256.0F;
    public static final float CORE_RADIUS = 16.0F / 256.0F;

    private GmtLogo() {
    }

    public static Identifier ringTexture(int i) {
        return RING_TEX[i];
    }
}