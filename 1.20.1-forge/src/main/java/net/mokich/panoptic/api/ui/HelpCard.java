package net.mokich.panoptic.api.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.mokich.panoptic.api.util.Disc;

import java.util.ArrayList;
import java.util.List;

public final class HelpCard {
    public static final int SIZE = 13;
    private static final int MARKER = 9;

    public record KeyHint(String key, String desc) {
    }

    private static float openT = 0.0F;
    private static long lastNanos = 0L;
    private static String shownKey = null;

    private HelpCard() {
    }

    public static boolean icon(GuiGraphics g, Font font, int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + SIZE && mouseY >= y && mouseY <= y + SIZE;
        int r = 6;
        int cx = x + r;
        int cy = y + r;
        if (hov) {
            disc(g, cx, cy, r + 1, (0x55 << 24) | (GuiStyle.ACCENT & 0xFFFFFF));
        }
        disc(g, cx, cy, r, GuiStyle.ACCENT);
        disc(g, cx, cy, r - 1, GuiStyle.BORDER_B);
        disc(g, cx, cy, r - 2, hov ? GuiStyle.T(0xFF33280F) : GuiStyle.PANEL2);
        g.fill(cx - 3, cy - 4, cx + 3, cy - 3, GuiStyle.T(0x30FFF4D8));
        g.drawCenteredString(font, "?", cx + 1, cy - 3, hov ? GuiStyle.ACCENT : GuiStyle.TEXT);
        return hov;
    }

    public static void render(GuiGraphics g, Font font, int screenW, int screenH, boolean hovered,
                              Component title, Component summary, List<Component> bullets, List<KeyHint> keys) {
        String key = title.getString();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1.0e9F;
        lastNanos = now;
        if (dt > 0.05F) {
            dt = 0.05F;
        }

        if (hovered) {
            shownKey = key;
        } else if (!key.equals(shownKey)) {
            return;
        }
        float target = hovered ? 1.0F : 0.0F;
        if (openT < target) {
            openT = Math.min(target, openT + dt / 0.16F);
        } else if (openT > target) {
            openT = Math.max(target, openT - dt / 0.12F);
        }
        if (openT <= 0.0015F && !hovered) {
            return;
        }

        float ease = 1.0F - (1.0F - openT) * (1.0F - openT);

        int wrap = Math.min(300, screenW - 60);
        List<FormattedCharSequence> sum = summary == null ? List.of() : font.split(summary, wrap - 2);
        List<List<FormattedCharSequence>> groups = new ArrayList<>();
        for (Component c : bullets) {
            groups.add(font.split(c, wrap - MARKER - 2));
        }

        int w = font.width(title.getString()) + 24;
        for (FormattedCharSequence l : sum) {
            w = Math.max(w, font.width(l) + 22);
        }
        for (List<FormattedCharSequence> group : groups) {
            for (FormattedCharSequence l : group) {
                w = Math.max(w, font.width(l) + 22 + MARKER);
            }
        }
        for (KeyHint k : keys) {
            w = Math.max(w, keyW(font, k) + 22);
        }

        int h = 22;
        if (!sum.isEmpty()) {
            h += sum.size() * 10 + 4;
        }
        for (List<FormattedCharSequence> group : groups) {
            h += Math.max(1, group.size()) * 10 + 2;
        }
        if (!keys.isEmpty()) {
            h += 6 + keys.size() * 15;
        }
        h += 6;

        float fit = 1.0F;
        int availW = screenW - 8;
        int availH = screenH - 10;
        if (w > availW) {
            fit = Math.min(fit, availW / (float) w);
        }
        if (h > availH) {
            fit = Math.min(fit, availH / (float) h);
        }
        float scaledW = w * fit;
        float scaledH = h * fit;
        float ox = (screenW - scaledW) / 2.0F;
        float oy = screenH - 8.0F - scaledH;
        if (oy < 4.0F) {
            oy = 4.0F;
        }
        float dy = (1.0F - ease) * 5.0F;

        long ms = System.currentTimeMillis();
        float amb = openT * openT;
        float breath = 0.5F + 0.5F * (float) Math.sin(ms / 900.0);
        int titleCol = lerpRgb(GuiStyle.ACCENT, 0xFFFFF4D8, 0.35F * breath * amb);

        g.pose().pushPose();
        g.pose().translate(ox, oy + dy, 400.0F);
        g.pose().scale(fit, fit, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, ease);

        int x2 = w;
        int y2 = h;
        GuiStyle.panel(g, 0, 0, x2, y2);
        g.drawCenteredString(font, title, x2 / 2, 7, titleCol);
        GuiStyle.divider(g, 9, x2 - 9, 18);

        int y = 22;
        for (FormattedCharSequence l : sum) {
            g.drawString(font, l, 11, y, GuiStyle.MUTED, false);
            y += 10;
        }
        if (!sum.isEmpty()) {
            y += 4;
        }
        int bi = 0;
        for (List<FormattedCharSequence> group : groups) {
            if (group.isEmpty()) {
                y += 10;
                continue;
            }
            boolean first = true;
            for (FormattedCharSequence l : group) {
                if (first) {
                    float mp = 0.5F + 0.5F * (float) Math.sin(ms / 470.0 - bi * 0.7);
                    int mcol = lerpRgb(GuiStyle.ACCENT, 0xFFFFF4D8, 0.75F * mp * amb);
                    marker(g, 11, y, mcol);
                    first = false;
                }
                g.drawString(font, l, 11 + MARKER, y, GuiStyle.TEXT, false);
                y += 10;
            }
            y += 2;
            bi++;
        }
        if (!keys.isEmpty()) {
            GuiStyle.divider(g, 9, x2 - 9, y + 1);
            y += 6;
            for (KeyHint k : keys) {
                int kw = font.width(k.key()) + 12;
                GuiStyle.keycap(g, font, 11, y, 11 + kw, y + 13, k.key(), false, false, true);
                g.drawString(font, k.desc(), 11 + kw + 8, y + 3, GuiStyle.MUTED, false);
                y += 15;
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.pose().popPose();
    }

    private static void marker(GuiGraphics g, int x, int y, int color) {
        for (int dy = 0; dy < 5; dy++) {
            int wpx = 3 - Math.abs(dy - 2);
            g.fill(x, y + 2 + dy, x + wpx, y + 3 + dy, color);
        }
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        int[] sp = Disc.spans(r);
        for (int dy = -r; dy <= r; dy++) {
            int hw = sp[dy + r];
            g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
        }
    }

    private static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    private static int keyW(Font font, KeyHint k) {
        return font.width(k.key()) + 12 + 8 + font.width(k.desc());
    }
}