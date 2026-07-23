package net.mokich.panoptic.screen;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.Scroll;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SettingsScreen extends Screen implements TextTyping {
    @Override
    public boolean gmtTyping() {
        return editing != null || listening != null;
    }
    private static final int ROW_H = 20;

    private enum Kind {
        ZOOM,
        INT,
        BOOL,
        TEXT_INT,
        SLIDER
    }

    private static final Object RESET_ROW = new Object();

    private record Opt(String key, String langKey, Kind kind, int min, int max, int step) {
    }

    private final Screen parent;
    private final List<Object> rows = new ArrayList<>();
    private ModBinds.Bind listening;
    private Opt editing;
    private Opt sliderDrag;
    private boolean barDrag;
    private boolean helpHover;
    private String editBuf = "";
    private int pendingModifier;
    private double scroll;
    private double target;
    private long lastNano;
    private int panX1;
    private int panX2;
    private int panY1;
    private int panY2;
    private String hoverTip;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("panoptic.tool.settings"));
        this.parent = parent;
    }

    private void sound(float pitch) {
        if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(
                            SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5F));
        }
    }

    @Override
    protected void init() {
        rows.clear();
        rows.add("panoptic.set.screens");
        rows.add(ModBinds.Bind.MAIN);
        rows.add(ModBinds.Bind.SCREENGRAB);
        rows.add(ModBinds.Bind.CAPTURE);
        rows.add(ModBinds.Bind.SEARCH);
        rows.add(ModBinds.Bind.INSPECTOR);
        rows.add(ModBinds.Bind.SEEDMAP);
        rows.add(ModBinds.Bind.VILLAGERS);
        rows.add(ModBinds.Bind.SCREEN_INSPECTOR);
        rows.add("panoptic.set.files");
        rows.add(ModBinds.Bind.COPY_PATHS);
        rows.add(ModBinds.Bind.COPY_IDS);
        rows.add(ModBinds.Bind.COPY_CODE);
        rows.add(ModBinds.Bind.SELECT_ALL);
        rows.add(ModBinds.Bind.INVERT_SEL);
        rows.add(ModBinds.Bind.CLEAR_SEL);
        rows.add("panoptic.set.screens_view");
        rows.add(ModBinds.Bind.VIEW_GIVE);
        rows.add(ModBinds.Bind.VIEW_RECORD);
        rows.add(ModBinds.Bind.VIEW_COPY);
        rows.add(ModBinds.Bind.VIEW_DRAW);
        rows.add(ModBinds.Bind.VIEW_RESET);
        rows.add(ModBinds.Bind.VIEW_DELETE);
        rows.add(ModBinds.Bind.MARK_ALL);
        rows.add(ModBinds.Bind.MARK_CLEAR);
        rows.add("panoptic.set.draw");
        rows.add(ModBinds.Bind.DRAW_CURSOR);
        rows.add(ModBinds.Bind.DRAW_PEN);
        rows.add(ModBinds.Bind.DRAW_BOX);
        rows.add(ModBinds.Bind.DRAW_UNDO);
        rows.add(ModBinds.Bind.DRAW_DONE);
        rows.add("panoptic.set.options");
        rows.add(new Opt(ModSettings.MAP_MIN_ZOOM, "panoptic.set.map_min_zoom", Kind.ZOOM, 0, 0, 0));
        rows.add(new Opt(ModSettings.MAP_MAX_ZOOM, "panoptic.set.map_max_zoom", Kind.ZOOM, 0, 0, 0));
        rows.add(new Opt(ModSettings.STRUCT_MIN_ZOOM, "panoptic.set.struct_min_zoom", Kind.ZOOM, 0, 0, 0));
        rows.add(new Opt(ModSettings.LOG_MAX_LINES, "panoptic.set.log_max_lines", Kind.TEXT_INT, 0, 999999, 0));
        rows.add("panoptic.set.theme");
        rows.add(new Opt(ModSettings.THEME_R, "panoptic.set.theme_r", Kind.SLIDER, 0, 255, 0));
        rows.add(new Opt(ModSettings.THEME_G, "panoptic.set.theme_g", Kind.SLIDER, 0, 255, 0));
        rows.add(new Opt(ModSettings.THEME_B, "panoptic.set.theme_b", Kind.SLIDER, 0, 255, 0));
        rows.add(RESET_ROW);

        int w = Math.min(420, this.width - 20);
        panX1 = (this.width - w) / 2;
        panX2 = panX1 + w;
        panY1 = 24;
        panY2 = this.height - 14;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        if (lastNano == 0) {
            lastNano = now;
        }
        double dt = Math.min((now - lastNano) / 1.0e9, 0.1);
        lastNano = now;
        scroll = Scroll.ease(scroll, target, dt, 16.0);

        renderBackground(g);
        hoverTip = null;
        g.drawString(this.font, I18n.get("panoptic.tool.settings"), panX1, 8, GuiStyle.ACCENT);
        helpHover = HelpCard.icon(g, this.font, panX1 + this.font.width(I18n.get("panoptic.tool.settings")) + 6, 5, mouseX, mouseY);
        boolean backHov = mouseX >= panX2 - 62 && mouseX < panX2 && mouseY >= 4 && mouseY < 20;
        GuiStyle.button(g, this.font, panX2 - 62, 4, panX2, 20, I18n.get("panoptic.vill.back"), backHov, true);
        GuiStyle.panel(g, panX1, panY1, panX2, panY2);
        g.enableScissor(panX1 + 2, panY1 + 2, panX2 - 2, panY2 - 2);
        int y = panY1 + 5 - (int) Math.round(scroll);
        for (Object o : rows) {
            if (y + ROW_H >= panY1 && y <= panY2) {
                if (o instanceof String header) {
                    g.fillGradient(panX1 + 2, y + 2, panX2 - 2, y + ROW_H - 2, GuiStyle.T(0xFF3A2F1B), GuiStyle.T(0xFF241D11));
                    g.fill(panX1 + 2, y + 2, panX2 - 2, y + 3, GuiStyle.T(0x30FFE7B0));
                    g.fill(panX1 + 5, y + 5, panX1 + 7, y + ROW_H - 5, GuiStyle.ACCENT);
                    g.drawString(this.font, I18n.get(header), panX1 + 11, y + 6, GuiStyle.ACCENT);
                } else if (o instanceof ModBinds.Bind bind) {
                    drawBindRow(g, bind, y, mouseX, mouseY);
                } else if (o instanceof Opt opt) {
                    drawOptRow(g, opt, y, mouseX, mouseY);
                } else if (o == RESET_ROW) {
                    String label = I18n.get("panoptic.set.reset_all");
                    int bw = this.font.width(label) + 20;
                    int bx = (panX1 + panX2) / 2 - bw / 2;
                    boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= y + 2 && mouseY < y + ROW_H - 2;
                    GuiStyle.button(g, this.font, bx, y + 2, bx + bw, y + ROW_H - 2, label, hov, true);
                }
            }
            y += ROW_H;
        }
        g.disableScissor();
        int maxSc = Math.max(0, contentHeight() - (panY2 - panY1));
        if (maxSc > 0) {
            int trackH = panY2 - 2 - (panY1 + 2);
            int thumbH = Math.max(20, (int) (trackH * (float) trackH / (trackH + maxSc)));
            int thumbY = panY1 + 2 + (int) ((trackH - thumbH) * (scroll / maxSc));
            g.fill(panX2 - 6, panY1 + 2, panX2 - 3, panY2 - 2, 0x40000000);
            g.fill(panX2 - 6, thumbY, panX2 - 3, thumbY + thumbH, barDrag ? GuiStyle.ACCENT : GuiStyle.BORDER_B);
        }
        super.render(g, mouseX, mouseY, partial);
        if (hoverTip != null) {
            g.renderTooltip(this.font, Component.literal(hoverTip), mouseX, mouseY);
        }
        drawHelp(g);
    }

    private void drawHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.set.help_b1"));
        bullets.add(Component.translatable("panoptic.set.help_b2"));
        bullets.add(Component.translatable("panoptic.set.help_b3"));
        HelpCard.render(g, this.font, this.width, this.height, helpHover,
                Component.translatable("panoptic.set.help_title"),
                Component.translatable("panoptic.set.help_sum"), bullets, List.of());
    }

    private int chipX1() {
        return panX2 - 128;
    }

    private void drawBindRow(GuiGraphics g, ModBinds.Bind bind, int y, int mouseX, int mouseY) {
        boolean conflict = ModBinds.conflictOf(bind) != null;
        boolean rowHov = mouseX >= panX1 + 2 && mouseX < panX2 - 2 && mouseY >= y + 1 && mouseY < y + ROW_H - 1
                && mouseY >= panY1 && mouseY <= panY2;
        if (rowHov) {
            GuiStyle.row(g, panX1 + 2, y + 1, panX2 - 2, y + ROW_H - 1, true, false);
        }
        g.drawString(this.font, I18n.get(bind.langKey), panX1 + 10, y + 6,
                conflict ? 0xFFE06666 : GuiStyle.TEXT);
        if (conflict && mouseX >= panX1 + 8 && mouseX <= chipX1() - 4 && mouseY >= y && mouseY < y + ROW_H) {
            hoverTip = I18n.get("panoptic.set.conflict", I18n.get(ModBinds.conflictOf(bind).langKey));
        }
        int cx1 = chipX1();
        int cx2 = panX2 - 24;
        boolean lst = listening == bind;
        boolean hov = mouseX >= cx1 && mouseX < cx2 && mouseY >= y + 2 && mouseY < y + ROW_H - 2;
        String label = lst ? I18n.get(pendingModifier != 0 ? "panoptic.set.press_mod" : "panoptic.set.press") : ModBinds.label(bind);
        boolean bound = ModBinds.key(bind).code != -1;
        GuiStyle.keycap(g, this.font, cx1, y + 2, cx2, y + ROW_H - 2, label, hov, lst, bound);
        if (bound && !lst) {
            boolean hx = mouseX >= cx2 + 4 && mouseX < cx2 + 16 && mouseY >= y + 4 && mouseY < y + 16;
            int xc = hx ? GuiStyle.ACCENT : GuiStyle.DIM;
            Icons.iconCross(g, cx2 + 5, y + 6, xc);
            if (hx) {
                hoverTip = I18n.get("panoptic.set.clear");
            }
        }
    }

    private String optValue(Opt opt) {
        double v = ModSettings.get(opt.key());
        return switch (opt.kind()) {
            case ZOOM -> ModSettings.zoomLabel(v);
            case INT -> String.valueOf((int) Math.round(v));
            case BOOL -> I18n.get(v > 0.5 ? "panoptic.set.on" : "panoptic.set.off");
            case TEXT_INT -> (int) Math.round(v) <= 0 ? I18n.get("panoptic.set.all") : String.valueOf((int) Math.round(v));
            case SLIDER -> String.valueOf((int) Math.round(v));
        };
    }

    private void commitEdit() {
        if (editing == null) {
            return;
        }
        int v = 0;
        try {
            v = editBuf.isEmpty() ? 0 : Integer.parseInt(editBuf);
        } catch (NumberFormatException ignored) {
        }
        v = Math.max(editing.min(), Math.min(editing.max(), v));
        ModSettings.set(editing.key(), v);
        editing = null;
        editBuf = "";
        sound(1.3F);
    }

    private void drawOptRow(GuiGraphics g, Opt opt, int y, int mouseX, int mouseY) {
        boolean rowHov = mouseX >= panX1 + 2 && mouseX < panX2 - 2 && mouseY >= y + 1 && mouseY < y + ROW_H - 1
                && mouseY >= panY1 && mouseY <= panY2;
        if (rowHov) {
            GuiStyle.row(g, panX1 + 2, y + 1, panX2 - 2, y + ROW_H - 1, true, false);
        }
        g.drawString(this.font, I18n.get(opt.langKey()), panX1 + 10, y + 6, GuiStyle.TEXT);
        int cx1 = chipX1();
        int cx2 = panX2 - 24;
        if (opt.kind() == Kind.BOOL) {
            boolean on = ModSettings.getBool(opt.key());
            boolean hov = mouseX >= cx1 && mouseX < cx2 && mouseY >= y + 2 && mouseY < y + ROW_H - 2;
            GuiStyle.button(g, this.font, cx1, y + 2, cx2, y + ROW_H - 2, optValue(opt), hov, true, on);
            return;
        }
        if (opt.kind() == Kind.TEXT_INT) {
            boolean edit = editing == opt;
            boolean hov = mouseX >= cx1 && mouseX < cx2 && mouseY >= y + 2 && mouseY < y + ROW_H - 2;
            String shown = edit ? editBuf : optValue(opt);
            GuiStyle.keycap(g, this.font, cx1, y + 2, cx2, y + ROW_H - 2, shown, hov, edit, true);
            if (edit && System.currentTimeMillis() / 500 % 2 == 0) {
                int tw = this.font.width(shown);
                int caretX = (cx1 + cx2) / 2 + tw / 2 + 1;
                g.fill(caretX, y + 5, caretX + 1, y + ROW_H - 5, GuiStyle.ACCENT);
            }
            if (hov && !edit) {
                hoverTip = I18n.get("panoptic.set.zero_all");
            }
            return;
        }
        if (opt.kind() == Kind.SLIDER) {
            int v = (int) Math.round(ModSettings.get(opt.key()));
            int tx1 = cx1;
            int tx2 = panX2 - 46;
            g.fill(tx1, y + 8, tx2, y + 12, GuiStyle.SEARCH_BG);
            GuiStyle.rect(g, tx1, y + 8, tx2, y + 12, GuiStyle.BORDER);
            int fillW = (int) ((tx2 - tx1 - 2) * (v / 255.0));
            int chan = opt.key().equals(ModSettings.THEME_R) ? 0xFFC85050
                    : opt.key().equals(ModSettings.THEME_G) ? 0xFF64B464 : 0xFF5A78C8;
            g.fill(tx1 + 1, y + 9, tx1 + 1 + fillW, y + 11, chan);
            int hx = tx1 + 1 + fillW;
            g.fill(hx - 1, y + 5, hx + 2, y + ROW_H - 5, GuiStyle.TEXT);
            String vs = String.valueOf(v);
            g.drawString(this.font, vs, panX2 - 40, y + 6, GuiStyle.TEXT);
            int cur = 0xFF000000 | ModSettings.getInt(ModSettings.THEME_R) << 16
                    | ModSettings.getInt(ModSettings.THEME_G) << 8 | ModSettings.getInt(ModSettings.THEME_B);
            g.fill(panX2 - 20, y + 5, panX2 - 8, y + ROW_H - 5, cur);
            GuiStyle.rect(g, panX2 - 20, y + 5, panX2 - 8, y + ROW_H - 5, GuiStyle.BORDER);
            return;
        }
        int bw = 14;
        boolean lHov = mouseX >= cx1 && mouseX < cx1 + bw && mouseY >= y + 2 && mouseY < y + ROW_H - 2;
        boolean rHov = mouseX >= cx2 - bw && mouseX < cx2 && mouseY >= y + 2 && mouseY < y + ROW_H - 2;
        GuiStyle.button(g, this.font, cx1, y + 2, cx1 + bw, y + ROW_H - 2, "", lHov, true);
        Icons.iconTriLeft(g, cx1 + 5, y + 7, lHov ? GuiStyle.ACCENT : GuiStyle.TEXT);
        GuiStyle.button(g, this.font, cx2 - bw, y + 2, cx2, y + ROW_H - 2, "", rHov, true);
        Icons.iconTriRight(g, cx2 - 9, y + 7, rHov ? GuiStyle.ACCENT : GuiStyle.TEXT);
        GuiStyle.keycap(g, this.font, cx1 + bw + 2, y + 2, cx2 - bw - 2, y + ROW_H - 2,
                optValue(opt), false, false, true);
    }

    private void stepOpt(Opt opt, int dir) {
        switch (opt.kind()) {
            case ZOOM -> ModSettings.stepZoom(opt.key(), dir);
            case INT -> {
                int v = (int) Math.round(ModSettings.get(opt.key()));
                v = Math.max(opt.min(), Math.min(opt.max(), v + dir * opt.step()));
                ModSettings.set(opt.key(), v);
            }
            case BOOL -> ModSettings.toggle(opt.key());
        }
        sound(dir > 0 ? 1.2F : 1.0F);
    }

    private int contentHeight() {
        return rows.size() * ROW_H + 10;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (editing != null) {
            commitEdit();
        }
        if (listening != null) {
            if (button == 0) {
                listening = null;
                return true;
            }
            ModBinds.set(listening, ModBinds.MOUSE_BASE + button, hasControlDown(), hasShiftDown(), hasAltDown());
            listening = null;
            sound(1.3F);
            return true;
        }
        if (button == 0 && mx >= panX2 - 62 && mx < panX2 && my >= 4 && my < 20) {
            sound(1.0F);
            onClose();
            return true;
        }
        int maxSc0 = Math.max(0, contentHeight() - (panY2 - panY1));
        if (button == 0 && maxSc0 > 0 && mx >= panX2 - 8 && mx < panX2 - 1 && my >= panY1 && my <= panY2) {
            barDrag = true;
            jumpBar(my);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int y = panY1 + 5 - (int) Math.round(scroll);
        for (Object o : rows) {
            boolean inRow = my >= y + 2 && my < y + ROW_H - 2 && my >= panY1 && my <= panY2;
            if (o instanceof ModBinds.Bind bind && inRow) {
                int cx1 = chipX1();
                int cx2 = panX2 - 24;
                if (mx >= cx1 && mx < cx2) {
                    sound(1.1F);
                    listening = bind;
                    return true;
                }
                if (mx >= cx2 + 4 && mx < cx2 + 16 && ModBinds.key(bind).code != -1) {
                    sound(0.8F);
                    ModBinds.unbind(bind);
                    listening = null;
                    return true;
                }
            } else if (o instanceof Opt opt && inRow) {
                int cx1 = chipX1();
                int cx2 = panX2 - 24;
                if (opt.kind() == Kind.BOOL) {
                    if (mx >= cx1 && mx < cx2) {
                        stepOpt(opt, 1);
                        return true;
                    }
                } else if (opt.kind() == Kind.TEXT_INT) {
                    if (mx >= cx1 && mx < cx2) {
                        commitEdit();
                        editing = opt;
                        int cur = (int) Math.round(ModSettings.get(opt.key()));
                        editBuf = cur <= 0 ? "" : String.valueOf(cur);
                        sound(1.1F);
                        return true;
                    }
                } else if (opt.kind() == Kind.SLIDER) {
                    int tx1 = cx1;
                    int tx2 = panX2 - 46;
                    if (mx >= tx1 - 2 && mx <= tx2 + 2) {
                        sliderDrag = opt;
                        sliderSet(opt, mx, tx1, tx2);
                        return true;
                    }
                } else {
                    if (mx >= cx1 && mx < cx1 + 14) {
                        stepOpt(opt, -1);
                        return true;
                    }
                    if (mx >= cx2 - 14 && mx < cx2) {
                        stepOpt(opt, 1);
                        return true;
                    }
                }
            } else if (o == RESET_ROW && inRow) {
                String label = I18n.get("panoptic.set.reset_all");
                int bw2 = this.font.width(label) + 20;
                int bx = (panX1 + panX2) / 2 - bw2 / 2;
                if (mx >= bx && mx < bx + bw2) {
                    ModSettings.resetAll();
                    ModBinds.resetAll();
                    sound(0.8F);
                    return true;
                }
            }
            y += ROW_H;
        }
        listening = null;
        return false;
    }

    private void sliderSet(Opt opt, double mx, int tx1, int tx2) {
        int v = (int) Math.round((mx - tx1 - 1) / Math.max(1.0, tx2 - tx1 - 2) * 255.0);
        ModSettings.set(opt.key(), Math.max(0, Math.min(255, v)));
        ModSettings.applyThemeNow();
    }

    private void jumpBar(double my) {
        int maxSc = Math.max(0, contentHeight() - (panY2 - panY1));
        if (maxSc <= 0) {
            return;
        }
        int trackH = panY2 - 2 - (panY1 + 2);
        int thumbH = Math.max(20, (int) (trackH * (float) trackH / (trackH + maxSc)));
        double frac = (my - (panY1 + 2) - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        target = Mth.clamp(frac * maxSc, 0, maxSc);
        scroll = target;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (barDrag) {
            jumpBar(my);
            return true;
        }
        if (sliderDrag != null) {
            sliderSet(sliderDrag, mx, chipX1(), panX2 - 46);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (barDrag || sliderDrag != null) {
            barDrag = false;
            sliderDrag = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, contentHeight() - (panY2 - panY1));
        target = Mth.clamp(target - delta * 30, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editing != null) {
            if (key == 256) {
                editing = null;
                editBuf = "";
                return true;
            }
            if (key == 257 || key == 335) {
                commitEdit();
                return true;
            }
            if (key == 259 && !editBuf.isEmpty()) {
                editBuf = editBuf.substring(0, editBuf.length() - 1);
                return true;
            }
            return true;
        }
        if (listening != null) {
            if (key == 256) {
                listening = null;
                return true;
            }
            if (key == 259) {
                ModBinds.unbind(listening);
                listening = null;
                return true;
            }
            if (ModBinds.isModifierKey(key)) {
                pendingModifier = key;
                return true;
            }
            ModBinds.set(listening, key, hasControlDown(), hasShiftDown(), hasAltDown());
            listening = null;
            pendingModifier = 0;
            sound(1.3F);
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (editing != null) {
            if (ch >= '0' && ch <= '9' && editBuf.length() < 7) {
                editBuf = editBuf + ch;
            }
            return true;
        }
        return super.charTyped(ch, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (listening != null && pendingModifier != 0 && key == pendingModifier) {
            ModBinds.set(listening, key, false, false, false);
            listening = null;
            pendingModifier = 0;
            sound(1.3F);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}