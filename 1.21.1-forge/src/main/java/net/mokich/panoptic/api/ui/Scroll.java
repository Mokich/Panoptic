package net.mokich.panoptic.api.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class Scroll {
    private Scroll() {}

    public static double ease(double cur, double target, double dt, double k) {
        double n = cur + (target - cur) * (1.0 - Math.exp(-dt * k));
        return Math.abs(target - n) < 0.3 ? target : n;
    }

    public static void bar(GuiGraphics g, int x, int top, int bottom, int content,
                           double scroll, boolean active, int mouseX, int mouseY) {
        int trackH = bottom - top;
        if (content <= trackH) {
            return;
        }
        int max = content - trackH;
        int thumbH = Math.max(18, trackH * trackH / content);
        int thumbY = top + (int) ((trackH - thumbH) * scroll / max);
        boolean hov = mouseX >= x - 1 && mouseX <= x + 5 && mouseY >= top && mouseY <= bottom;
        g.fill(x, top, x + 4, bottom, 0x40000000);
        g.fill(x, thumbY, x + 4, thumbY + thumbH, active || hov ? GuiStyle.ACCENT : GuiStyle.T(0xFF8C6C33));
    }

    public static double thumbToScroll(int mouseY, int top, int bottom, int content) {
        int trackH = bottom - top;
        int max = content - trackH;
        if (max <= 0) {
            return 0;
        }
        int thumbH = Math.max(18, trackH * trackH / content);
        double rel = (mouseY - top - thumbH / 2.0) / (trackH - thumbH);
        return Mth.clamp(rel, 0, 1) * max;
    }
}