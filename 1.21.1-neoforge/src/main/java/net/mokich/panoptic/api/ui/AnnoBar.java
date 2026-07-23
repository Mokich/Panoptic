package net.mokich.panoptic.api.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class AnnoBar {
    public static final int ACT_CURSOR = 0;
    public static final int ACT_PEN = 1;
    public static final int ACT_BOX = 2;
    public static final int ACT_COLOR = 3;
    public static final int ACT_UNDO = 4;
    public static final int ACT_DONE = 5;
    public static final int ACT_CANCEL = 6;
    public static final int ACT_INSIDE = 99;

    private static final float[][] PRESETS = {
            {0.0F, 0.75F, 1.0F},
            {0.118F, 0.71F, 1.0F},
            {0.333F, 0.52F, 0.88F},
            {0.564F, 0.58F, 1.0F},
            {0.0F, 0.0F, 1.0F}
    };
    private static final int SV = 56;
    private static final int HUE_W = 12;

    public int tool = ACT_CURSOR;
    public int thickness = 2;
    public String hoverTip;

    private float hue;
    private float sat = 0.75F;
    private float val = 1.0F;
    private boolean pickerOpen;
    private int picking;

    private final List<int[]> zones = new ArrayList<>();
    private final List<int[]> presetZones = new ArrayList<>();
    private int px1;
    private int py1;
    private int px2;
    private int py2;
    private int svX;
    private int svY;
    private int hueX;
    private int ppx1;
    private int ppy1;
    private int ppx2;
    private int ppy2;

    public int color() {
        return 0xFF000000 | Mth.hsvToRgb(hue, sat, val);
    }

    public void togglePicker() {
        pickerOpen = !pickerOpen;
        picking = 0;
    }

    public void closePicker() {
        pickerOpen = false;
        picking = 0;
    }

    public void render(GuiGraphics g, Font font, int centerX, int y, int mouseX, int mouseY, boolean canUndo) {
        zones.clear();
        hoverTip = null;
        int bs = 18;
        int gap = 2;
        int sep = 8;
        int total = bs * 7 + gap * 4 + sep * 2;
        int x = centerX - total / 2;
        px1 = x - 6;
        py1 = y - 4;
        px2 = x + total + 6;
        py2 = y + bs + 4;
        GuiStyle.plate(g, px1, py1, px2, py2, GuiStyle.T(0xF2332A19), GuiStyle.T(0xF21D1810), GuiStyle.BORDER_B);
        x = btn(g, font, x, y, bs, ACT_CURSOR, mouseX, mouseY, tool == ACT_CURSOR, true, "panoptic.grab.tool_move") + gap;
        x = btn(g, font, x, y, bs, ACT_PEN, mouseX, mouseY, tool == ACT_PEN, true, "panoptic.grab.tool_pen") + gap;
        x = btn(g, font, x, y, bs, ACT_BOX, mouseX, mouseY, tool == ACT_BOX, true, "panoptic.grab.tool_rect") + gap;
        g.fill(x + 2, y + 2, x + 3, y + bs - 2, 0x33FFE7B0);
        x += sep;
        x = btn(g, font, x, y, bs, ACT_COLOR, mouseX, mouseY, pickerOpen, true, "panoptic.grab.tool_color") + gap;
        x = btn(g, font, x, y, bs, ACT_UNDO, mouseX, mouseY, false, canUndo, "panoptic.grab.tool_undo") + gap;
        g.fill(x + 2, y + 2, x + 3, y + bs - 2, 0x33FFE7B0);
        x += sep;
        x = btn(g, font, x, y, bs, ACT_DONE, mouseX, mouseY, false, true, "panoptic.grab.tool_done") + gap;
        btn(g, font, x, y, bs, ACT_CANCEL, mouseX, mouseY, false, true, "panoptic.grab.tool_cancel");
        if (pickerOpen) {
            renderPicker(g, mouseX, mouseY);
        } else {
            ppx1 = 0;
            ppy1 = 0;
            ppx2 = 0;
            ppy2 = 0;
        }
    }

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        presetZones.clear();
        int w = 6 + SV + 4 + HUE_W + 6;
        int h = 6 + SV + 4 + 12 + 6;
        ppx1 = (px1 + px2) / 2 - w / 2;
        ppy1 = py2 + 3;
        ppx2 = ppx1 + w;
        ppy2 = ppy1 + h;
        GuiStyle.plate(g, ppx1, ppy1, ppx2, ppy2, GuiStyle.T(0xF2332A19), GuiStyle.T(0xF21D1810), GuiStyle.BORDER_B);
        svX = ppx1 + 6;
        svY = ppy1 + 6;
        hueX = svX + SV + 4;

        for (int col = 0; col < SV; col++) {
            int top = 0xFF000000 | Mth.hsvToRgb(hue, col / (float) (SV - 1), 1.0F);
            g.fillGradient(svX + col, svY, svX + col + 1, svY + SV, top, 0xFF000000);
        }
        GuiStyle.rect(g, svX - 1, svY - 1, svX + SV + 1, svY + SV + 1, GuiStyle.BORDER);
        int mxp = svX + Math.round(sat * (SV - 1));
        int myp = svY + Math.round((1.0F - val) * (SV - 1));
        GuiStyle.rect(g, mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFF000000);
        GuiStyle.rect(g, mxp - 1, myp - 1, mxp + 2, myp + 2, 0xFFFFFFFF);

        for (int row = 0; row < SV; row++) {
            g.fill(hueX, svY + row, hueX + HUE_W, svY + row + 1, 0xFF000000 | Mth.hsvToRgb(row / (float) (SV - 1), 1.0F, 1.0F));
        }
        GuiStyle.rect(g, hueX - 1, svY - 1, hueX + HUE_W + 1, svY + SV + 1, GuiStyle.BORDER);
        int hy = svY + Math.round(hue * (SV - 1));
        g.fill(hueX - 1, hy - 1, hueX + HUE_W + 1, hy, 0xFF000000);
        g.fill(hueX - 1, hy, hueX + HUE_W + 1, hy + 1, 0xFFFFFFFF);

        int prY = svY + SV + 4;
        int prX = svX;
        for (int i = 0; i < PRESETS.length; i++) {
            int c = 0xFF000000 | Mth.hsvToRgb(PRESETS[i][0], PRESETS[i][1], PRESETS[i][2]);
            boolean hov = mouseX >= prX && mouseX < prX + 12 && mouseY >= prY && mouseY < prY + 12;
            g.fill(prX, prY, prX + 12, prY + 12, c);
            GuiStyle.rect(g, prX, prY, prX + 12, prY + 12, hov ? GuiStyle.ACCENT : 0xFF120E08);
            presetZones.add(new int[]{prX, prY, prX + 12, prY + 12, i});
            prX += 14;
        }
        g.fill(prX + 2, prY, ppx2 - 6, prY + 12, color());
        GuiStyle.rect(g, prX + 2, prY, ppx2 - 6, prY + 12, GuiStyle.BORDER);
    }

    private int btn(GuiGraphics g, Font font, int x, int y, int bs, int id, int mouseX, int mouseY,
                    boolean active, boolean enabled, String tipKey) {
        boolean hov = enabled && mouseX >= x && mouseX < x + bs && mouseY >= y && mouseY < y + bs;
        GuiStyle.button(g, font, x, y, x + bs, y + bs, "", hov, enabled, active);
        int c = !enabled ? GuiStyle.DIM
                : id == ACT_DONE ? (hov ? 0xFF9BE89B : 0xFF6BE06B)
                : id == ACT_CANCEL ? (hov ? 0xFFFF8B8B : 0xFFCF8A8A)
                : active || hov ? GuiStyle.ACCENT : GuiStyle.TEXT;
        drawIcon(g, id, x, y, bs, c);
        if (hov) {
            String hint = switch (id) {
                case ACT_CURSOR -> "1";
                case ACT_PEN -> "2";
                case ACT_BOX -> "3";
                case ACT_UNDO -> "Ctrl+Z";
                case ACT_DONE -> "Enter";
                case ACT_CANCEL -> "Esc";
                default -> null;
            };
            hoverTip = I18n.get(tipKey) + (hint == null ? "" : " §8[" + hint + "]");
        }
        if (enabled) {
            zones.add(new int[]{x, y, x + bs, y + bs, id});
        }
        return x + bs;
    }

    private void drawIcon(GuiGraphics g, int id, int x, int y, int bs, int c) {
        int cx = x + bs / 2;
        int cy = y + bs / 2;
        switch (id) {
            case ACT_CURSOR -> Icons.iconCursor(g, cx - 3, cy - 5, c);
            case ACT_PEN -> Icons.iconPencil(g, cx - 5, cy - 5, c);
            case ACT_BOX -> GuiStyle.rect(g, cx - 4, cy - 4, cx + 4, cy + 4, c);
            case ACT_COLOR -> {
                g.fill(cx - 4, cy - 4, cx + 4, cy + 4, color());
                GuiStyle.rect(g, cx - 4, cy - 4, cx + 4, cy + 4, 0xFF120E08);
            }
            case ACT_UNDO -> Icons.iconUndo(g, cx - 4, cy - 3, c);
            case ACT_DONE -> Icons.iconCheck(g, cx - 2, cy - 3, c);
            case ACT_CANCEL -> Icons.iconCross(g, cx - 3, cy - 3, c);
            default -> {
            }
        }
    }

    public int click(double mx, double my) {
        for (int[] z : zones) {
            if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                if (z[4] != ACT_COLOR) {
                    closePicker();
                }
                return z[4];
            }
        }
        if (mx >= px1 && mx < px2 && my >= py1 && my < py2) {
            return ACT_INSIDE;
        }
        if (pickerOpen && mx >= ppx1 && mx < ppx2 && my >= ppy1 && my < ppy2) {
            return ACT_INSIDE;
        }
        return -1;
    }

    public boolean pickerMouseDown(double mx, double my) {
        if (!pickerOpen) {
            return false;
        }
        if (mx >= svX && mx < svX + SV && my >= svY && my < svY + SV) {
            picking = 1;
            setSv(mx, my);
            return true;
        }
        if (mx >= hueX && mx < hueX + HUE_W && my >= svY && my < svY + SV) {
            picking = 2;
            setHue(my);
            return true;
        }
        for (int[] z : presetZones) {
            if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                float[] p = PRESETS[z[4]];
                hue = p[0];
                sat = p[1];
                val = p[2];
                return true;
            }
        }
        return mx >= ppx1 && mx < ppx2 && my >= ppy1 && my < ppy2;
    }

    public boolean pickerMouseDrag(double mx, double my) {
        if (picking == 1) {
            setSv(mx, my);
            return true;
        }
        if (picking == 2) {
            setHue(my);
            return true;
        }
        return false;
    }

    public void pickerMouseUp() {
        picking = 0;
    }

    private void setSv(double mx, double my) {
        sat = Mth.clamp((float) (mx - svX) / (SV - 1), 0.0F, 1.0F);
        val = 1.0F - Mth.clamp((float) (my - svY) / (SV - 1), 0.0F, 1.0F);
    }

    private void setHue(double my) {
        hue = Mth.clamp((float) (my - svY) / (SV - 1), 0.0F, 0.999F);
    }
}