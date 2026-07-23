package net.mokich.panoptic.api.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class Icons {
    private static final ResourceLocation TEX = new ResourceLocation("panoptic", "textures/gui/icons.png");
    private static final int CELL = 16;
    private static final int PAD = 2;
    private static final int TEX_W = 128;
    private static final int TEX_H = 64;

    private Icons() {
    }

    private static void cell(GuiGraphics g, int x, int y, int idx, int c) {
        int a = c >>> 24;
        if (a == 0) {
            a = 255;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F,
                (c & 0xFF) / 255.0F, a / 255.0F);
        g.blit(TEX, x - PAD, y - PAD, idx % 8 * CELL, idx / 8 * CELL, CELL, CELL, TEX_W, TEX_H);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void searchIcon(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 0, c);
    }

    public static void iconCheck(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 1, c);
    }

    public static void iconCross(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 2, c);
    }

    public static void iconCopy(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 3, c);
    }

    public static void iconArrowRight(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 4, c);
    }

    public static void iconWarn(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 5, c);
    }

    public static void iconRefLink(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 6, c);
    }

    public static void iconScroll(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 7, c);
    }

    public static void iconEye(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 8, c);
    }

    public static void iconEyeOff(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 9, c);
    }

    public static void iconTriRight(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 10, c);
    }

    public static void iconTriLeft(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 11, c);
    }

    public static void iconTriDown(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 12, c);
    }

    public static void iconStar(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 13, c);
    }

    public static void iconBits(GuiGraphics g, int x, int y, int w, int[] rows, int c) {
        for (int r = 0; r < rows.length; r++) {
            int bits = rows[r];
            for (int col = 0; col < w; col++) {
                if ((bits & (1 << (w - 1 - col))) != 0) {
                    g.fill(x + col, y + r, x + col + 1, y + r + 1, c);
                }
            }
        }
    }

    public static void iconCursor(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 14, c);
    }

    public static void iconUndo(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 15, c);
    }

    public static void iconPencil(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 16, c);
    }

    public static void iconFilter(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 17, c);
    }

    public static void iconHide(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 18, c);
    }

    public static void iconBack(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 19, c);
    }

    public static void iconGlobe(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 20, c);
    }

    public static void iconArrow(GuiGraphics g, int x, int y, int c, boolean up) {
        cell(g, x, y, up ? 21 : 22, c);
    }

    public static void iconWin(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 23, c);
    }

    public static void iconCenter(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 24, c);
    }

    public static void iconClock(GuiGraphics g, int x, int y, int c) {
        cell(g, x, y, 25, c);
    }
}