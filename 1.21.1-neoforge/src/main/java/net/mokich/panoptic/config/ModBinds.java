package net.mokich.panoptic.config;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.mokich.panoptic.api.ui.HelpCard;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

public final class ModBinds {
    public static final int MOUSE_BASE = 1000;
    public static final int UNBOUND = -1;
    public enum Scope {GLOBAL, FILES, VIEWER, DRAW}

    public enum Bind {
        MAIN("panoptic.bind.main", GLFW.GLFW_KEY_GRAVE_ACCENT, false, false, Scope.GLOBAL),
        CAPTURE("key.panoptic.inspect", GLFW.GLFW_KEY_GRAVE_ACCENT, false, false, true, Scope.GLOBAL),
        SCREENGRAB("key.panoptic.screengrab", GLFW.GLFW_KEY_S, true, true, Scope.GLOBAL),
        INSPECTOR("panoptic.bind.inspector", UNBOUND, false, false, Scope.GLOBAL),
        SEEDMAP("key.panoptic.seedmap", UNBOUND, false, false, Scope.GLOBAL),
        VILLAGERS("panoptic.bind.villagers", UNBOUND, false, false, Scope.GLOBAL),
        SCREEN_INSPECTOR("panoptic.bind.screen_inspector", UNBOUND, false, false, Scope.GLOBAL),
        SEARCH("panoptic.bind.search", GLFW.GLFW_KEY_F, true, false, Scope.VIEWER),
        CENTER("panoptic.bind.center", GLFW.GLFW_KEY_SPACE, false, false, Scope.VIEWER),
        COPY_PATHS("key.panoptic.copy_paths", GLFW.GLFW_KEY_P, true, false, Scope.FILES),
        COPY_IDS("key.panoptic.copy_ids", GLFW.GLFW_KEY_I, true, false, Scope.FILES),
        COPY_CODE("key.panoptic.copy_code", GLFW.GLFW_KEY_K, true, false, Scope.FILES),
        SELECT_ALL("key.panoptic.select_all", GLFW.GLFW_KEY_A, true, false, Scope.FILES),
        INVERT_SEL("key.panoptic.invert_sel", GLFW.GLFW_KEY_E, true, false, Scope.FILES),
        CLEAR_SEL("key.panoptic.clear_sel", GLFW.GLFW_KEY_D, true, false, Scope.FILES),
        VIEW_GIVE("panoptic.bind.view_give", GLFW.GLFW_KEY_G, false, false, Scope.VIEWER),
        VIEW_RECORD("panoptic.bind.view_record", GLFW.GLFW_KEY_E, false, false, Scope.VIEWER),
        VIEW_COPY("panoptic.bind.view_copy", GLFW.GLFW_KEY_C, false, false, Scope.VIEWER),
        VIEW_DRAW("panoptic.bind.view_draw", GLFW.GLFW_KEY_B, false, false, Scope.VIEWER),
        VIEW_RESET("panoptic.bind.view_reset", GLFW.GLFW_KEY_R, false, false, Scope.VIEWER),
        VIEW_DELETE("panoptic.bind.view_delete", GLFW.GLFW_KEY_DELETE, false, false, Scope.VIEWER),
        MARK_ALL("panoptic.bind.mark_all", GLFW.GLFW_KEY_A, true, false, Scope.VIEWER),
        MARK_CLEAR("panoptic.bind.mark_clear", GLFW.GLFW_KEY_X, true, false, Scope.VIEWER),
        DRAW_CURSOR("panoptic.bind.draw_cursor", GLFW.GLFW_KEY_1, false, false, Scope.DRAW),
        DRAW_PEN("panoptic.bind.draw_pen", GLFW.GLFW_KEY_2, false, false, Scope.DRAW),
        DRAW_BOX("panoptic.bind.draw_box", GLFW.GLFW_KEY_3, false, false, Scope.DRAW),
        DRAW_UNDO("panoptic.bind.draw_undo", GLFW.GLFW_KEY_Z, true, false, Scope.DRAW),
        DRAW_DONE("panoptic.bind.draw_done", GLFW.GLFW_KEY_ENTER, false, false, Scope.DRAW);

        public final String langKey;
        public final Scope scope;
        final int defCode;
        final boolean defCtrl;
        final boolean defShift;
        final boolean defAlt;

        Bind(String langKey, int defCode, boolean defCtrl, boolean defShift, Scope scope) {
            this(langKey, defCode, defCtrl, defShift, false, scope);
        }

        Bind(String langKey, int defCode, boolean defCtrl, boolean defShift, boolean defAlt, Scope scope) {
            this.langKey = langKey;
            this.defCode = defCode;
            this.defCtrl = defCtrl;
            this.defShift = defShift;
            this.defAlt = defAlt;
            this.scope = scope;
        }
    }

    public static final class KeyDef {
        public int code;
        public boolean ctrl;
        public boolean shift;
        public boolean alt;

        KeyDef(int code, boolean ctrl, boolean shift, boolean alt) {
            this.code = code;
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
        }

        boolean sameAs(KeyDef o) {
            return code != UNBOUND && code == o.code && ctrl == o.ctrl && shift == o.shift && alt == o.alt;
        }
    }

    private static final EnumMap<Bind, KeyDef> KEYS = new EnumMap<>(Bind.class);
    private static boolean loaded;

    private ModBinds() {}

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("panoptic").resolve("binds.txt");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (Bind b : Bind.values()) {
            KEYS.put(b, new KeyDef(b.defCode, b.defCtrl, b.defShift, b.defAlt));
        }
        try {
            Path p = file();
            if (Files.exists(p)) {
                for (String line : Files.readAllLines(p)) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    try {
                        Bind b = Bind.valueOf(line.substring(0, eq).trim());
                        String[] parts = line.substring(eq + 1).trim().split(",");
                        KeyDef k = KEYS.get(b);
                        k.code = Integer.parseInt(parts[0]);
                        k.ctrl = parts.length > 1 && "1".equals(parts[1]);
                        k.shift = parts.length > 2 && "1".equals(parts[2]);
                        k.alt = parts.length > 3 && "1".equals(parts[3]);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<Bind, KeyDef> e : KEYS.entrySet()) {
                KeyDef k = e.getValue();
                lines.add(e.getKey().name() + "=" + k.code + "," + (k.ctrl ? 1 : 0) + "," + (k.shift ? 1 : 0) + "," + (k.alt ? 1 : 0));
            }
            Files.createDirectories(file().getParent());
            Files.write(file(), lines);
        } catch (Exception ignored) {
        }
    }

    public static KeyDef key(Bind b) {
        ensureLoaded();
        return KEYS.get(b);
    }

    public static void set(Bind b, int code, boolean ctrl, boolean shift, boolean alt) {
        ensureLoaded();
        KeyDef k = KEYS.get(b);
        k.code = code;
        k.ctrl = ctrl && !isCtrlKey(code);
        k.shift = shift && !isShiftKey(code);
        k.alt = alt && !isAltKey(code);
        save();
    }

    public static void resetAll() {
        ensureLoaded();
        for (Bind b : Bind.values()) {
            KeyDef k = KEYS.get(b);
            k.code = b.defCode;
            k.ctrl = b.defCtrl;
            k.shift = b.defShift;
            k.alt = b.defAlt;
        }
        save();
    }

    public static void unbind(Bind b) {
        set(b, UNBOUND, false, false, false);
    }

    public static boolean isMouse(int code) {
        return code >= MOUSE_BASE;
    }

    private static boolean isShiftKey(int code) {
        return code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    private static boolean isCtrlKey(int code) {
        return code == GLFW.GLFW_KEY_LEFT_CONTROL || code == GLFW.GLFW_KEY_RIGHT_CONTROL;
    }

    private static boolean isAltKey(int code) {
        return code == GLFW.GLFW_KEY_LEFT_ALT || code == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    public static boolean isModifierKey(int code) {
        return isShiftKey(code) || isCtrlKey(code) || isAltKey(code);
    }

    private static boolean modsOk(KeyDef k) {
        boolean ctrlOk = isCtrlKey(k.code) || Screen.hasControlDown() == k.ctrl;
        boolean shiftOk = isShiftKey(k.code) || Screen.hasShiftDown() == k.shift;
        boolean altOk = isAltKey(k.code) || Screen.hasAltDown() == k.alt;
        return ctrlOk && shiftOk && altOk;
    }

    public static boolean matchesNow(Bind b, int keyCode) {
        KeyDef k = key(b);
        if (k.code == UNBOUND || isMouse(k.code) || k.code != keyCode) {
            return false;
        }
        return modsOk(k);
    }

    public static boolean matchesMouse(Bind b, int button) {
        KeyDef k = key(b);
        if (!isMouse(k.code) || k.code - MOUSE_BASE != button) {
            return false;
        }
        return modsOk(k);
    }

    public static Bind conflictOf(Bind b) {
        ensureLoaded();
        KeyDef k = KEYS.get(b);
        for (Bind other : Bind.values()) {
            if (other != b && KEYS.get(other).sameAs(k) && scopesClash(b.scope, other.scope)) {
                return other;
            }
        }
        return null;
    }

    private static boolean scopesClash(Scope a, Scope b) {
        return a == b || a == Scope.GLOBAL || b == Scope.GLOBAL;
    }

    public static String mouseName(int button) {
        return switch (button) {
            case 0 -> I18n.get("panoptic.set.mouse_left");
            case 1 -> I18n.get("panoptic.set.mouse_right");
            case 2 -> I18n.get("panoptic.set.mouse_middle");
            default -> I18n.get("panoptic.set.mouse_n", button + 1);
        };
    }

    public static HelpCard.KeyHint hint(Bind b, String labelKey) {
        return new HelpCard.KeyHint(label(b), I18n.get(labelKey));
    }

    public static String label(Bind b) {
        KeyDef k = key(b);
        if (k.code == UNBOUND) {
            return I18n.get("panoptic.set.unbound");
        }
        StringBuilder sb = new StringBuilder();
        if (k.ctrl) {
            sb.append("Ctrl+");
        }
        if (k.shift) {
            sb.append("Shift+");
        }
        if (k.alt) {
            sb.append("Alt+");
        }
        if (isMouse(k.code)) {
            sb.append(mouseName(k.code - MOUSE_BASE));
        } else {
            sb.append(keyName(k.code));
        }
        return sb.toString();
    }

    private static String keyName(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) code);
        }
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) code);
        }
        switch (code) {
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "`";
            case GLFW.GLFW_KEY_MINUS: return "-";
            case GLFW.GLFW_KEY_EQUAL: return "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET: return "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return "]";
            case GLFW.GLFW_KEY_BACKSLASH: return "\\";
            case GLFW.GLFW_KEY_SEMICOLON: return ";";
            case GLFW.GLFW_KEY_APOSTROPHE: return "'";
            case GLFW.GLFW_KEY_COMMA: return ",";
            case GLFW.GLFW_KEY_PERIOD: return ".";
            case GLFW.GLFW_KEY_SLASH: return "/";
            default: return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
        }
    }
}