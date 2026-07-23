package net.mokich.panoptic.api.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiStyle {
    private GuiStyle() {}

    public static int BG = 0xF2241E15;
    public static int BG2 = 0xF21A150D;
    public static int PANEL = 0xFF221C13;
    public static int PANEL2 = 0xFF17130C;
    public static int BORDER = 0xFF63532F;
    public static int BORDER_T = 0xFFDCBC78;
    public static int BORDER_B = 0xFF8C6C33;
    public static int TITLE = 0xFF2E2718;
    public static int HEADER = 0xFF282115;
    public static int TEXT = 0xFFF2EDE1;
    public static int MUTED = 0xFFACA188;
    public static int DIM = 0xFF766C52;
    public static int ACCENT = 0xFFE8C06C;
    public static int ROWHOVER = 0x1AE8C06C;
    public static int SEARCH_BG = 0xFF17130B;

    private static final int[] BASES = {
            0xF2241E15, 0xF21A150D, 0xFF221C13, 0xFF17130C, 0xFF63532F, 0xFFDCBC78, 0xFF8C6C33,
            0xFF2E2718, 0xFF282115, 0xFFF2EDE1, 0xFFACA188, 0xFF766C52, 0xFFE8C06C, 0x1AE8C06C,
            0xFF17130B, 0xFF4A3A1D, 0xFF231C11, 0xFF8C6C33
    };
    private static final float BASE_S = 0.5345F;
    private static final float BASE_V = 0.9098F;
    private static boolean themed;
    private static float themeH;
    private static float themeS;
    private static float themeV;
    private static final Map<Integer, Integer> TINT_CACHE = new HashMap<>();
    private static final List<Runnable> THEME_LISTENERS = new ArrayList<>();

    public static void onTheme(Runnable r) {
        THEME_LISTENERS.add(r);
    }

    public static void applyTheme(int r, int g, int b) {
        float[] hsv = rgbToHsv(r, g, b);
        themeH = hsv[0];
        themeS = hsv[1];
        themeV = hsv[2];
        themed = !(r == 232 && g == 192 && b == 108);
        TINT_CACHE.clear();
        BG = tint(BASES[0]);
        BG2 = tint(BASES[1]);
        PANEL = tint(BASES[2]);
        PANEL2 = tint(BASES[3]);
        BORDER = tint(BASES[4]);
        BORDER_T = tint(BASES[5]);
        BORDER_B = tint(BASES[6]);
        TITLE = tint(BASES[7]);
        HEADER = tint(BASES[8]);
        TEXT = tint(BASES[9]);
        MUTED = tint(BASES[10]);
        DIM = tint(BASES[11]);
        ACCENT = tint(BASES[12]);
        ROWHOVER = tint(BASES[13]);
        SEARCH_BG = tint(BASES[14]);
        PLATE_HI = tint(BASES[15]);
        PLATE_LO = tint(BASES[16]);
        RIVET = tint(BASES[17]);
        for (Runnable l : THEME_LISTENERS) {
            try {
                l.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public static int T(int argb) {
        return tint(argb);
    }
    private static int tint(int argb) {
        if (!themed) {
            return argb;
        }
        Integer cached = TINT_CACHE.get(argb);
        if (cached != null) {
            return cached;
        }
        int a = argb >>> 24;
        float[] hsv = rgbToHsv(argb >> 16 & 255, argb >> 8 & 255, argb & 255);
        float sat = Math.min(1.0F, hsv[1] * (themeS / BASE_S));
        float val = Math.min(1.0F, hsv[2] * (0.55F + 0.45F * (themeV / BASE_V)));
        int rgb = Mth.hsvToRgb(themeH, sat, val);
        int out = a << 24 | rgb & 0xFFFFFF;
        TINT_CACHE.put(argb, out);
        return out;
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255.0F;
        float gf = g / 255.0F;
        float bf = b / 255.0F;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h;
        if (d < 1.0e-5F) {
            h = 0.0F;
        } else if (max == rf) {
            h = ((gf - bf) / d % 6.0F + 6.0F) % 6.0F / 6.0F;
        } else if (max == gf) {
            h = ((bf - rf) / d + 2.0F) / 6.0F;
        } else {
            h = ((rf - gf) / d + 4.0F) / 6.0F;
        }
        float sat = max < 1.0e-5F ? 0.0F : d / max;
        return new float[]{h, sat, max};
    }

    public static void niceBox(GuiGraphics g, int x1, int y1, int x2, int y2, int bg, int bgB, int bTop, int bBot) {
        g.fillGradient(x1 + 1, y1 + 1, x2 - 1, y2 - 1, bg, bgB);
        g.fillGradient(x1, y1 + 1, x1 + 1, y2 - 1, bTop, bBot);
        g.fillGradient(x2 - 1, y1 + 1, x2, y2 - 1, bTop, bBot);
        g.fill(x1 + 1, y1, x2 - 1, y1 + 1, bTop);
        g.fill(x1 + 1, y2 - 1, x2 - 1, y2, bBot);
        g.fill(x1, y1, x1 + 1, y1 + 1, bTop);
        g.fill(x2 - 1, y1, x2, y1 + 1, bTop);
        g.fill(x1, y2 - 1, x1 + 1, y2, bBot);
        g.fill(x2 - 1, y2 - 1, x2, y2, bBot);
    }

    public static void box(GuiGraphics g, int x1, int y1, int x2, int y2) {
        niceBox(g, x1, y1, x2, y2, BG, BG2, BORDER_T, BORDER_B);
    }

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    public static int PLATE_HI = 0xFF4A3A1D;
    public static int PLATE_LO = 0xFF231C11;
    public static int RIVET = 0xFF8C6C33;

    public static void plate(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bot, int border) {
        rect(g, x1, y1, x2, y2, border);
        g.fillGradient(x1 + 1, y1 + 1, x2 - 1, y2 - 1, top, bot);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, mix(top, T(0xFFFFE7B0), 0.26F));
        g.fill(x1 + 1, y1 + 1, x1 + 2, y2 - 1, mix(top, T(0xFFFFE7B0), 0.10F));
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, mix(bot, T(0xFF000000), 0.14F));
    }

    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, T(0x90000000));
        plate(g, x1, y1, x2, y2, T(0xF22B2418), T(0xF21D1810), BORDER_B);
        rivet(g, x1 + 3, y1 + 3);
        rivet(g, x2 - 5, y1 + 3);
        rivet(g, x1 + 3, y2 - 5);
        rivet(g, x2 - 5, y2 - 5);
    }

    public static void panelHeader(GuiGraphics g, Font font,
                                   int x1, int y1, int x2, String title, String right) {
        g.fillGradient(x1 + 1, y1 + 1, x2 - 1, y1 + 14, T(0xFF3A2F1B), T(0xFF241D11));
        g.fill(x1 + 1, y1 + 14, x2 - 1, y1 + 15, BORDER_B);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, T(0x30FFE7B0));
        g.fill(x1 + 4, y1 + 4, x1 + 6, y1 + 12, ACCENT);
        g.drawString(font, title, x1 + 10, y1 + 4, ACCENT);
        if (right != null) {
            g.drawString(font, right, x2 - 6 - font.width(right), y1 + 4, DIM);
        }
    }

    public static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, RIVET);
        g.fill(x, y, x + 1, y + 1, T(0xFFD8B87A));
    }

    public static void button(GuiGraphics g, Font font, int x1, int y1, int x2, int y2,
                              String label, boolean hovered, boolean enabled) {
        button(g, font, x1, y1, x2, y2, label, hovered, enabled, false);
    }

    public static void button(GuiGraphics g, Font font, int x1, int y1, int x2, int y2,
                              String label, boolean hovered, boolean enabled, boolean active) {
        int top;
        int bot;
        int border;
        if (!enabled) {
            top = T(0xFF2A2418);
            bot = T(0xFF201A11);
            border = T(0xFF463B26);
        } else if (active) {
            top = T(0xFF634E27);
            bot = T(0xFF44351B);
            border = ACCENT;
        } else if (hovered) {
            top = T(0xFF554327);
            bot = T(0xFF382C18);
            border = ACCENT;
        } else {
            top = T(0xFF453824);
            bot = T(0xFF2C2416);
            border = T(0xFF7A6438);
        }
        plate(g, x1, y1, x2, y2, top, bot, border);
        int color = !enabled ? DIM : hovered || active ? ACCENT : TEXT;
        g.drawCenteredString(font, label, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2 + 1, color);
    }

    public static void chipButton(GuiGraphics g, Font font, int x1, int y1, int x2, int y2,
                                  String label, boolean hovered, boolean enabled) {
        button(g, font, x1, y1, x2, y2, label, hovered, enabled, false);
    }

    public static void keycap(GuiGraphics g, Font font, int x1, int y1, int x2, int y2,
                              String label, boolean hovered, boolean listening, boolean bound) {
        int top = listening ? T(0xFF634E27) : hovered ? T(0xFF4B3C22) : T(0xFF3A3020);
        int bot = listening ? T(0xFF44351B) : hovered ? T(0xFF302614) : T(0xFF251E13);
        plate(g, x1, y1, x2, y2, top, bot, listening ? ACCENT : hovered ? BORDER_T : T(0xFF7A6438));
        g.fill(x1 + 2, y2 - 3, x2 - 2, y2 - 2, T(0x22000000));
        int color = listening ? ACCENT : bound ? TEXT : DIM;
        g.drawCenteredString(font, label, (x1 + x2) / 2, y1 + (y2 - y1 - 8) / 2 + 1, color);
    }

    public static void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, T(0xFF0F0C07));
        g.fill(x, y, x + size, y + 1, T(0xFF120E08));
        g.fill(x, y, x + 1, y + size, T(0xFF120E08));
        g.fill(x + size - 1, y + 1, x + size, y + size, T(0xFF4A3A1D));
        g.fill(x + 1, y + size - 1, x + size, y + size, T(0xFF4A3A1D));
        g.fill(x + 1, y + 1, x + size - 1, y + 2, T(0x22000000));
    }

    public static void slot(GuiGraphics g, int x, int y) {
        slot(g, x, y, 18);
    }

    public static void divider(GuiGraphics g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, T(0xFF120E08));
        g.fill(x1, y + 1, x2, y + 2, T(0x1AFFE7B0));
    }

    public static void row(GuiGraphics g, int x1, int y1, int x2, int y2, boolean hovered, boolean selected) {
        if (selected) {
            g.fillGradient(x1, y1, x2, y2, T(0x44E8C06C), T(0x22E8C06C));
            g.fill(x1, y1, x1 + 2, y2, ACCENT);
        } else if (hovered) {
            g.fillGradient(x1, y1, x2, y2, T(0x22FFE7B0), T(0x10FFE7B0));
        }
    }

    public static String plural(int n, String one, String few, String many) {
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 == 1 && m100 != 11) {
            return one;
        }
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) {
            return few;
        }
        return many;
    }

    public static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }
}