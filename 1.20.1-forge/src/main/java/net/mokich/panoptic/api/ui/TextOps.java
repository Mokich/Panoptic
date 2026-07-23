package net.mokich.panoptic.api.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class TextOps {
    public static final class Res {
        public String text;
        public int caret;
        public int sel;
        public boolean handled;
        public boolean changed;

        Res(String text, int caret, int sel, boolean handled, boolean changed) {
            this.text = text;
            this.caret = caret;
            this.sel = sel;
            this.handled = handled;
            this.changed = changed;
        }
    }

    private TextOps() {}

    public static Res type(String text, int caret, int sel, char c, int maxLen) {
        caret = Mth.clamp(caret, 0, text.length());
        if (c < 32 || c == 127) {
            return new Res(text, caret, sel, false, false);
        }
        if (hasSel(text, caret, sel)) {
            int a = Math.min(sel, caret);
            int b = Math.max(sel, caret);
            text = text.substring(0, a) + text.substring(b);
            caret = a;
        }
        if (text.length() >= maxLen) {
            return new Res(text, caret, -1, true, true);
        }
        text = text.substring(0, caret) + c + text.substring(caret);
        return new Res(text, caret + 1, -1, true, true);
    }

    public static Res key(String text, int caret, int sel, int key, int mods, int maxLen) {
        caret = Mth.clamp(caret, 0, text.length());
        boolean ctrl = (mods & 0x2) != 0;
        boolean shift = (mods & 0x1) != 0;
        Minecraft mc = Minecraft.getInstance();
        switch (key) {
            case 259 -> {
                if (hasSel(text, caret, sel)) {
                    return delSel(text, caret, sel);
                }
                if (caret > 0) {
                    int np = ctrl ? wordLeft(text, caret) : caret - 1;
                    return new Res(text.substring(0, np) + text.substring(caret), np, -1, true, true);
                }
                return new Res(text, caret, -1, true, false);
            }
            case 261 -> {
                if (hasSel(text, caret, sel)) {
                    return delSel(text, caret, sel);
                }
                if (caret < text.length()) {
                    int np = ctrl ? wordRight(text, caret) : caret + 1;
                    return new Res(text.substring(0, caret) + text.substring(np), caret, -1, true, true);
                }
                return new Res(text, caret, -1, true, false);
            }
            case 263 -> {
                int np = ctrl ? wordLeft(text, caret) : caret - 1;
                return move(text, caret, sel, np, shift);
            }
            case 262 -> {
                int np = ctrl ? wordRight(text, caret) : caret + 1;
                return move(text, caret, sel, np, shift);
            }
            case 268 -> {
                return move(text, caret, sel, 0, shift);
            }
            case 269 -> {
                return move(text, caret, sel, text.length(), shift);
            }
            case 65 -> {
                if (ctrl) {
                    return new Res(text, text.length(), 0, true, false);
                }
            }
            case 67 -> {
                if (ctrl) {
                    if (hasSel(text, caret, sel)) {
                        mc.keyboardHandler.setClipboard(selected(text, caret, sel));
                    } else if (!text.isEmpty()) {
                        mc.keyboardHandler.setClipboard(text);
                    }
                    return new Res(text, caret, sel, true, false);
                }
            }
            case 88 -> {
                if (ctrl) {
                    if (hasSel(text, caret, sel)) {
                        mc.keyboardHandler.setClipboard(selected(text, caret, sel));
                        return delSel(text, caret, sel);
                    }
                    return new Res(text, caret, sel, true, false);
                }
            }
            case 86 -> {
                if (ctrl) {
                    String clip = mc.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", " ").trim();
                    if (hasSel(text, caret, sel)) {
                        Res r = delSel(text, caret, sel);
                        text = r.text;
                        caret = r.caret;
                    }
                    int room = maxLen - text.length();
                    if (clip.length() > room) {
                        clip = clip.substring(0, Math.max(0, room));
                    }
                    text = text.substring(0, caret) + clip + text.substring(caret);
                    return new Res(text, caret + clip.length(), -1, true, true);
                }
            }
            default -> {
            }
        }
        return new Res(text, caret, sel, false, false);
    }

    private static Res move(String text, int caret, int sel, int pos, boolean shift) {
        int np = Mth.clamp(pos, 0, text.length());
        int ns;
        if (shift) {
            ns = sel == -1 ? caret : sel;
        } else {
            ns = -1;
        }
        return new Res(text, np, ns, true, false);
    }

    private static Res delSel(String text, int caret, int sel) {
        int a = Math.min(sel, caret);
        int b = Math.max(sel, caret);
        return new Res(text.substring(0, a) + text.substring(b), a, -1, true, true);
    }

    public static boolean hasSel(String text, int caret, int sel) {
        return sel != -1 && sel != caret && sel >= 0 && sel <= text.length();
    }

    public static String selected(String text, int caret, int sel) {
        if (!hasSel(text, caret, sel)) {
            return "";
        }
        return text.substring(Math.min(sel, caret), Math.max(sel, caret));
    }

    public static int wordLeft(String text, int caret) {
        int i = Mth.clamp(caret, 0, text.length());
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return i;
    }

    public static int wordRight(String text, int caret) {
        int i = Mth.clamp(caret, 0, text.length());
        while (i < text.length() && text.charAt(i) != ' ') {
            i++;
        }
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    public static void drawSel(GuiGraphics g, Font font, String text, int caret, int sel,
                               int textX, int y1, int y2) {
        if (!hasSel(text, caret, sel)) {
            return;
        }
        int a = Math.min(sel, caret);
        int b = Math.max(sel, caret);
        int ax = font.width(text.substring(0, a));
        int bx = font.width(text.substring(0, b));
        g.fill(textX + ax, y1, textX + bx, y2, 0x883A6EA5);
    }
}