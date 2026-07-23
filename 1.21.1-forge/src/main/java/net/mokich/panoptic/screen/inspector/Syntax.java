package net.mokich.panoptic.screen.inspector;

import net.mokich.panoptic.api.ui.GuiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Set;

public final class Syntax {
    private Syntax() {
    }

    private static final Set<String> KEYWORDS = Set.of(
            "uniform", "varying", "attribute", "in", "out", "void", "float", "int", "bool", "const",
            "vec2", "vec3", "vec4", "ivec2", "ivec3", "ivec4", "mat2", "mat3", "mat4",
            "sampler2D", "sampler3D", "if", "else", "for", "while", "return", "struct", "layout",
            "flat", "discard", "true", "false", "function", "local", "new", "var", "let");

    public static boolean isCode(String name) {
        return name.endsWith(".fsh") || name.endsWith(".vsh") || name.endsWith(".glsl")
                || name.endsWith(".gsh") || name.endsWith(".csh") || name.endsWith(".zs")
                || name.endsWith(".js") || name.endsWith(".vert") || name.endsWith(".frag");
    }

    public static boolean isKv(String name) {
        return name.endsWith(".properties") || name.endsWith(".toml") || name.endsWith(".cfg")
                || name.endsWith(".ini") || name.endsWith(".lang");
    }

    public static void json(GuiGraphics g, Font font, String line, int x, int y, int maxW) {
        int cx = x;
        int limit = x + maxW;
        boolean inStr = false;
        StringBuilder run = new StringBuilder();
        int runColor = GuiStyle.TEXT;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            int col;
            if (inStr) {
                col = 0xFF9CE0A0;
                if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
                col = 0xFF9CE0A0;
            } else if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',') {
                col = 0xFF7FA6D8;
            } else if (Character.isDigit(c) || c == '-' || c == '.') {
                col = 0xFFE0B341;
            } else {
                col = GuiStyle.TEXT;
            }
            if (col != runColor && run.length() > 0) {
                cx = run(g, font, run.toString(), cx, y, runColor, limit);
                run.setLength(0);
            }
            runColor = col;
            run.append(c);
        }
        if (run.length() > 0) {
            run(g, font, run.toString(), cx, y, runColor, limit);
        }
    }

    public static void glsl(GuiGraphics g, Font font, String line, int x, int y, int maxW) {
        int limit = x + maxW;
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#")) {
            run(g, font, line, x, y, 0xFFC792EA, limit);
            return;
        }
        int cm = line.indexOf("//");
        String code = cm >= 0 ? line.substring(0, cm) : line;
        int cx = x;
        int i = 0;
        int n = code.length();
        while (i < n && cx < limit) {
            char c = code.charAt(i);
            if (c == '"') {
                int end = code.indexOf('"', i + 1);
                end = end < 0 ? n : end + 1;
                cx = run(g, font, code.substring(i, end), cx, y, 0xFF9CE0A0, limit);
                i = end;
            } else if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_')) {
                    end++;
                }
                String word = code.substring(i, end);
                cx = run(g, font, word, cx, y,
                        KEYWORDS.contains(word) ? 0xFF6CC7E8 : GuiStyle.TEXT, limit);
                i = end;
            } else if (Character.isDigit(c)) {
                int end = i;
                while (end < n && (Character.isDigit(code.charAt(end)) || code.charAt(end) == '.'
                        || code.charAt(end) == 'f' || code.charAt(end) == 'F')) {
                    end++;
                }
                cx = run(g, font, code.substring(i, end), cx, y, 0xFFE0B341, limit);
                i = end;
            } else {
                int end = i;
                while (end < n) {
                    char e = code.charAt(end);
                    if (e == '"' || Character.isLetterOrDigit(e) || e == '_') {
                        break;
                    }
                    end++;
                }
                cx = run(g, font, code.substring(i, end), cx, y, 0xFF7FA6D8, limit);
                i = end;
            }
        }
        if (cm >= 0 && cx < limit) {
            run(g, font, line.substring(cm), cx, y, 0xFF6E7B6E, limit);
        }
    }

    public static void kv(GuiGraphics g, Font font, String line, int x, int y, int maxW) {
        int limit = x + maxW;
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
            run(g, font, line, x, y, 0xFF6E7B6E, limit);
            return;
        }
        int eq = line.indexOf('=');
        if (eq < 0) {
            eq = line.indexOf(':');
        }
        if (eq < 0) {
            run(g, font, line, x, y, GuiStyle.TEXT, limit);
            return;
        }
        int cx = run(g, font, line.substring(0, eq), x, y, GuiStyle.ACCENT, limit);
        cx = run(g, font, String.valueOf(line.charAt(eq)), cx, y, 0xFF7FA6D8, limit);
        run(g, font, line.substring(eq + 1), cx, y, GuiStyle.TEXT, limit);
    }

    private static int run(GuiGraphics g, Font font, String s, int x, int y, int color, int limit) {
        if (x >= limit) {
            return x;
        }
        g.drawString(font, s, x, y, color, false);
        return x + font.width(s);
    }
}
