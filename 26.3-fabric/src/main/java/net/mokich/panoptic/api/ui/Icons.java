package net.mokich.panoptic.api.ui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class Icons {
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("panoptic", "textures/gui/icons.png");
    private static final int CELL = 16;
    private static final int PAD = 2;
    private static final int TEX_W = 128;
    private static final int TEX_H = 64;

    private Icons() {
    }

    private static void cell(GuiGraphicsExtractor g, int x, int y, int idx, int c) {
        int col = (c >>> 24) == 0 ? (0xFF000000 | c) : c;
        g.blit(RenderPipelines.GUI_TEXTURED, TEX, x - PAD, y - PAD, idx % 8 * CELL, idx / 8 * CELL, CELL, CELL, TEX_W, TEX_H, col);
    }

    public static void searchIcon(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 0, c);
    }

    public static void iconCheck(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 1, c);
    }

    public static void iconCross(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 2, c);
    }

    public static void iconCopy(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 3, c);
    }

    public static void iconArrowRight(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 4, c);
    }

    public static void iconWarn(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 5, c);
    }

    public static void iconRefLink(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 6, c);
    }

    public static void iconScroll(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 7, c);
    }

    public static void iconEye(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 8, c);
    }

    public static void iconEyeOff(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 9, c);
    }

    public static void iconTriRight(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 10, c);
    }

    public static void iconTriLeft(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 11, c);
    }

    public static void iconTriDown(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 12, c);
    }

    public static void iconStar(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 13, c);
    }

    public static void iconBits(GuiGraphicsExtractor g, int x, int y, int w, int[] rows, int c) {
        for (int r = 0; r < rows.length; r++) {
            int bits = rows[r];
            for (int col = 0; col < w; col++) {
                if ((bits & (1 << (w - 1 - col))) != 0) {
                    g.fill(x + col, y + r, x + col + 1, y + r + 1, c);
                }
            }
        }
    }

    public static void iconCursor(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 14, c);
    }

    public static void iconUndo(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 15, c);
    }

    public static void iconPencil(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 16, c);
    }

    public static void iconFilter(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 17, c);
    }

    public static void iconHide(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 18, c);
    }

    public static void iconBack(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 19, c);
    }

    public static void iconGlobe(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 20, c);
    }

    public static void iconArrow(GuiGraphicsExtractor g, int x, int y, int c, boolean up) {
        cell(g, x, y, up ? 21 : 22, c);
    }

    public static void iconWin(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 23, c);
    }

    public static void iconCenter(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 24, c);
    }

    public static void iconClock(GuiGraphicsExtractor g, int x, int y, int c) {
        cell(g, x, y, 25, c);
    }
}