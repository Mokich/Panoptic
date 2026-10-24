package net.mokich.panoptic.api.util;

/**
 * Mouse button number helpers.
 *
 * <p>Minecraft 26.3 switched from GLFW to SDL, and SDL numbers the mouse buttons
 * 1=left, 2=middle, 3=right. Panoptic's screens and bindings still use the old
 * GLFW numbering (0=left, 1=right, 2=middle), so input has to be translated at
 * the point where it enters the mod.</p>
 */
public final class MouseButtons {

    private MouseButtons() {
    }

    public static int legacy(int sdlButton) {
        return switch (sdlButton) {
            case 1 -> 0;
            case 2 -> 2;
            case 3 -> 1;
            case 0 -> 0;
            default -> sdlButton - 1;
        };
    }
}
