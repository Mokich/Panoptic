package net.mokich.panoptic.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.Scroll;
import net.mokich.panoptic.api.ui.TextOps;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.network.AdminEditPacket;
import net.mokich.panoptic.network.AdminOpenPacket;
import net.mokich.panoptic.network.AdminStatePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PermsAdminScreen extends Screen implements TextTyping {
    private static final List<String> NODES = List.of(
            "panoptic.inspector",
            "panoptic.seed.view",
            "panoptic.seed.structures",
            "panoptic.trade",
            "panoptic.trade.spawn",
            "panoptic.screens",
            "panoptic.screens.give");
    private static final List<String> PLAYER_NODES = List.of(
            "panoptic.inspector",
            "panoptic.seed.view",
            "panoptic.seed.structures",
            "panoptic.trade",
            "panoptic.trade.spawn",
            "panoptic.screens",
            "panoptic.screens.give",
            "panoptic.admin");
    private static final int ROW_H = 18;
    private static final int PERM_H = 21;
    private static final int DROP_ROW = 14;

    private final Screen parent;
    private int tab;
    private Map<String, Set<String>> groups = new LinkedHashMap<>();
    private List<AdminStatePacket.PlayerRow> players = new ArrayList<>();
    private String rawJson = "";
    private boolean loaded;
    private String selGroup = "default";
    private UUID selPlayer;
    private double listScroll;
    private double listTarget;
    private boolean listDrag;
    private String newGroup = "";
    private int ngCaret;
    private int ngSel = -1;
    private boolean ngFocus;
    private String flash;
    private long flashUntil;
    private boolean helpHover;
    private long appearStart;
    private long lastNano;
    private float knobDt;
    private final Map<String, Float> knobAnim = new HashMap<>();
    private boolean grpOpen;
    private double grpScroll;
    private double grpTarget;
    private boolean grpDrag;
    private int grpX1, grpY1, grpX2, grpY2;

    private int panX1, panX2, panY1, panY2, leftX1, leftX2, rightX1, rightX2, listY1, rowsY;

    public PermsAdminScreen(Screen parent) {
        super(Component.translatable("panoptic.adm.title"));
        this.parent = parent;
    }

    @Override
    public boolean gmtTyping() {
        return ngFocus;
    }

    @Override
    protected void init() {
        int w = Math.min(660, this.width - 12);
        int h = Math.min(400, this.height - 12);
        panX1 = (this.width - w) / 2;
        panX2 = panX1 + w;
        panY1 = (this.height - h) / 2;
        panY2 = panY1 + h;
        leftX1 = panX1 + 8;
        leftX2 = panX1 + 236;
        rightX1 = leftX2 + 10;
        rightX2 = panX2 - 8;
        rowsY = panY1 + 44;
        listY1 = rowsY + 18;
        if (!loaded) {
            ClientPlayNetworking.send(new AdminOpenPacket());
        }
    }

    public void applyState(AdminStatePacket state) {
        groups = state.groups;
        players = state.players;
        rawJson = state.rawJson;
        loaded = true;
        if (!groups.containsKey(selGroup)) {
            selGroup = "default";
        }
    }

    private void send(byte op, String a, String b) {
        ClientPlayNetworking.send(new AdminEditPacket(op, a, b));
    }

    private void sound(float pitch) {
        if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(
                            SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.4F));
        }
    }

    private void doFlash(String key) {
        flash = I18n.get(key);
        flashUntil = System.currentTimeMillis() + 2200;
    }

    private int listBottom() {
        return tab == 0 ? panY2 - 32 : panY2 - 10;
    }

    private int listCount() {
        return tab == 0 ? groups.size() : players.size();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderTransparentBackground(g);
        long now = System.nanoTime();
        if (appearStart == 0) {
            appearStart = now;
        }
        knobDt = lastNano == 0 ? 0.016F : Math.min(0.1F, (now - lastNano) / 1.0e9F);
        lastNano = now;
        float t = Math.min(1.0F, (now - appearStart) / 1.6e8F);
        float appear = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        int visible = (listBottom() - listY1) / ROW_H;
        listTarget = Mth.clamp(listTarget, 0, Math.max(0, listCount() - visible));
        listScroll = Scroll.ease(listScroll, listTarget, knobDt, 16.0);
        grpScroll = Scroll.ease(grpScroll, grpTarget, knobDt, 16.0);

        g.pose().pushPose();
        float sc = 0.94F + 0.06F * appear;
        float ccx = (panX1 + panX2) / 2.0F;
        float ccy = (panY1 + panY2) / 2.0F;
        g.pose().translate(ccx, ccy, 0);
        g.pose().scale(sc, sc, 1.0F);
        g.pose().translate(-ccx, -ccy, 0);
        GuiStyle.panel(g, panX1, panY1, panX2, panY2);
        String title = I18n.get("panoptic.adm.title");
        GuiStyle.panelHeader(g, this.font, panX1, panY1, panX2, title, I18n.get("panoptic.gui.close"));
        helpHover = HelpCard.icon(g, this.font, panX1 + 10 + this.font.width(title) + 6, panY1 + 1, mouseX, mouseY);

        int tabX = leftX1;
        tabX = drawTab(g, tabX, 0, "panoptic.adm.tab_groups", mouseX, mouseY);
        drawTab(g, tabX, 1, "panoptic.adm.tab_players", mouseX, mouseY);

        String exp = I18n.get("panoptic.adm.export");
        String imp = I18n.get("panoptic.adm.import");
        int impW = this.font.width(imp) + 14;
        int expW = this.font.width(exp) + 14;
        int impX = rightX2 - impW;
        int expX = impX - 6 - expW;
        boolean expHov = hov(mouseX, mouseY, expX, panY1 + 22, expX + expW, panY1 + 37);
        boolean impHov = hov(mouseX, mouseY, impX, panY1 + 22, impX + impW, panY1 + 37);
        GuiStyle.button(g, this.font, expX, panY1 + 22, expX + expW, panY1 + 37, exp, expHov, loaded);
        GuiStyle.button(g, this.font, impX, panY1 + 22, impX + impW, panY1 + 37, imp, impHov, loaded);

        if (!loaded) {
            g.drawCenteredString(this.font, I18n.get("panoptic.adm.loading"),
                    (panX1 + panX2) / 2, (panY1 + panY2) / 2, GuiStyle.MUTED);
        } else {
            GuiStyle.plate(g, leftX1, rowsY, leftX2, panY2 - 8,
                    GuiStyle.T(0xF2221A10), GuiStyle.T(0xF2170F08), GuiStyle.T(0xFF4A3A1D));
            GuiStyle.plate(g, rightX1, rowsY, rightX2, panY2 - 8,
                    GuiStyle.T(0xF2221A10), GuiStyle.T(0xF2170F08), GuiStyle.T(0xFF4A3A1D));
            if (tab == 0) {
                renderGroups(g, mouseX, mouseY);
            } else {
                renderPlayers(g, mouseX, mouseY);
            }
        }

        if (grpOpen && tab == 1 && loaded) {
            renderGroupDrop(g, mouseX, mouseY);
        }

        if (flash != null && System.currentTimeMillis() < flashUntil) {
            int fw = this.font.width(flash) + 14;
            int fx = (panX1 + panX2) / 2 - fw / 2;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(fx, panY2 - 22, fx + fw, panY2 - 8, GuiStyle.T(0xF01A150D));
            GuiStyle.rect(g, fx, panY2 - 22, fx + fw, panY2 - 8, GuiStyle.ACCENT);
            g.drawCenteredString(this.font, flash, (panX1 + panX2) / 2, panY2 - 19, GuiStyle.ACCENT);
            g.pose().popPose();
        }
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partial);
        drawHelp(g);
    }

    private void drawHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.adm.help_b1"));
        bullets.add(Component.translatable("panoptic.adm.help_b2"));
        bullets.add(Component.translatable("panoptic.adm.help_b3"));
        bullets.add(Component.translatable("panoptic.adm.help_b4"));
        bullets.add(Component.translatable("panoptic.adm.help_b5"));
        bullets.add(Component.translatable("panoptic.adm.help_b6"));
        HelpCard.render(g, this.font, this.width, this.height, helpHover,
                Component.translatable("panoptic.adm.title"),
                Component.translatable("panoptic.adm.help_sum"), bullets, List.of());
    }

    private int drawTab(GuiGraphics g, int x, int idx, String key, int mouseX, int mouseY) {
        String label = I18n.get(key);
        int w = this.font.width(label) + 10;
        boolean active = tab == idx;
        boolean h = hov(mouseX, mouseY, x, panY1 + 22, x + w, panY1 + 38);
        g.drawString(this.font, label, x + 5, panY1 + 26,
                active ? GuiStyle.ACCENT : h ? GuiStyle.TEXT : GuiStyle.MUTED);
        if (active) {
            g.fill(x + 2, panY1 + 37, x + w - 2, panY1 + 39, GuiStyle.ACCENT);
        } else if (h) {
            g.fill(x + 2, panY1 + 37, x + w - 2, panY1 + 39, GuiStyle.T(0x50E8C06C));
        }
        return x + w + 10;
    }

    private void sectionHeader(GuiGraphics g, int x1, int x2, int y, String title, String right) {
        g.fillGradient(x1 + 1, y, x2 - 1, y + 15, GuiStyle.T(0xFF3A2F1B), GuiStyle.T(0xFF241D11));
        g.fill(x1 + 1, y, x2 - 1, y + 1, GuiStyle.T(0x30FFE7B0));
        g.fill(x1 + 4, y + 3, x1 + 6, y + 12, GuiStyle.ACCENT);
        g.drawString(this.font, trim(title, x2 - x1 - 60), x1 + 10, y + 4, GuiStyle.ACCENT);
        if (right != null) {
            g.drawString(this.font, right, x2 - 6 - this.font.width(right), y + 4, GuiStyle.DIM);
        }
    }

    private void toggle(GuiGraphics g, String key, int x2, int y, boolean on, boolean hovered) {
        float a = knobAnim.computeIfAbsent(key, k -> on ? 1.0F : 0.0F);
        a += ((on ? 1.0F : 0.0F) - a) * (1.0F - (float) Math.exp(-knobDt * 16.0));
        knobAnim.put(key, a);
        int tx1 = x2 - 30;
        int ty1 = y + 3;
        int ty2 = y + 15;
        g.fill(tx1, ty1, x2, ty2, GuiStyle.mix(GuiStyle.T(0xFF17130B), GuiStyle.T(0xFF6E5426), a));
        GuiStyle.rect(g, tx1, ty1, x2, ty2,
                GuiStyle.mix(hovered ? GuiStyle.BORDER_T : GuiStyle.BORDER, GuiStyle.ACCENT, a));
        int kx = Math.round(tx1 + 2 + (x2 - 12 - tx1 - 2) * a);
        g.fill(kx, ty1 + 2, kx + 10, ty2 - 2, GuiStyle.mix(GuiStyle.DIM, 0xFFFFE7B0, a));
    }

    private void triState(GuiGraphics g, int x, int y, int state, int mouseX, int mouseY) {
        int w = 54;
        int y2 = y + 15;
        g.fill(x, y, x + w, y2, GuiStyle.T(0xFF17130B));
        int active = state == 0 ? 0 : state > 0 ? 1 : 2;
        int color = state == 0 ? GuiStyle.MUTED : state > 0 ? GuiStyle.ACCENT : 0xFFFF6B6B;
        int hovSeg = mouseY >= y && mouseY < y2 && mouseX >= x && mouseX < x + w ? (mouseX - x) / 18 : -1;
        if (hovSeg >= 0 && hovSeg != active) {
            g.fill(x + hovSeg * 18, y, x + hovSeg * 18 + 18, y2, GuiStyle.ROWHOVER);
        }
        int ax = x + active * 18;
        g.fill(ax, y, ax + 18, y2, (color & 0x00FFFFFF) | 0x38000000);
        GuiStyle.rect(g, x, y, x + w, y2, hovSeg >= 0 ? GuiStyle.BORDER_T : GuiStyle.T(0xFF3A2E17));
        g.fill(x + 18, y + 1, x + 19, y2 - 1, GuiStyle.T(0xFF3A2E17));
        g.fill(x + 36, y + 1, x + 37, y2 - 1, GuiStyle.T(0xFF3A2E17));
        GuiStyle.rect(g, ax, y, ax + 18, y2, color);
        int dim = GuiStyle.T(0xFF4E4232);
        g.fill(x + 6, y + 7, x + 12, y + 8, active == 0 ? color : hovSeg == 0 ? GuiStyle.MUTED : dim);
        Icons.iconCheck(g, x + 25, y + 4, active == 1 ? color : hovSeg == 1 ? GuiStyle.MUTED : dim);
        Icons.iconCross(g, x + 42, y + 4, active == 2 ? color : hovSeg == 2 ? GuiStyle.MUTED : dim);
    }

    private void renderGroups(GuiGraphics g, int mouseX, int mouseY) {
        List<String> names = new ArrayList<>(groups.keySet());
        sectionHeader(g, leftX1, leftX2, rowsY + 1,
                I18n.get("panoptic.adm.tab_groups"), String.valueOf(names.size()));
        int bottom = listBottom();
        int scrollPx = (int) Math.round(listScroll * ROW_H);
        g.enableScissor(leftX1 + 1, listY1, leftX2 - 1, bottom);
        int y = listY1 + 2 - scrollPx;
        for (String name : names) {
            if (y + ROW_H >= listY1 && y <= bottom) {
                boolean sel = name.equals(selGroup);
                boolean h = hov(mouseX, mouseY, leftX1 + 2, y, leftX2 - 8, y + ROW_H)
                        && mouseY >= listY1 && mouseY < bottom;
                GuiStyle.row(g, leftX1 + 2, y, leftX2 - 8, y + ROW_H, h, sel);
                if (sel) {
                    g.fill(leftX1 + 2, y + 2, leftX1 + 4, y + ROW_H - 2, GuiStyle.ACCENT);
                }
                g.drawString(this.font, trim(name, 130), leftX1 + 9, y + 5,
                        sel ? GuiStyle.ACCENT : GuiStyle.TEXT, false);
                if ("default".equals(name)) {
                    g.drawString(this.font, I18n.get("panoptic.adm.default"),
                            leftX1 + 12 + this.font.width(trim(name, 130)), y + 5, GuiStyle.DIM, false);
                }
                String cnt = String.valueOf(groups.get(name).size());
                int cw = this.font.width(cnt) + 8;
                g.fill(leftX2 - 10 - cw, y + 3, leftX2 - 10, y + ROW_H - 3, GuiStyle.T(0x33E8C06C));
                g.drawCenteredString(this.font, cnt, leftX2 - 10 - cw / 2, y + 5, GuiStyle.MUTED);
            }
            y += ROW_H;
        }
        g.disableScissor();
        drawListScrollbar(g, names.size());

        int fy = panY2 - 26;
        int addX = leftX2 - 46;
        int delX = leftX2 - 22;
        g.fill(leftX1 + 2, fy, addX - 4, fy + 15, GuiStyle.T(0xFF17130B));
        GuiStyle.rect(g, leftX1 + 2, fy, addX - 4, fy + 15, ngFocus ? GuiStyle.ACCENT : GuiStyle.T(0xFF4A3A1D));
        if (newGroup.isEmpty() && !ngFocus) {
            g.drawString(this.font, I18n.get("panoptic.adm.new_group_hint"), leftX1 + 7, fy + 4, GuiStyle.DIM, false);
        } else {
            g.drawString(this.font, newGroup, leftX1 + 7, fy + 4, GuiStyle.TEXT, false);
            if (ngFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = leftX1 + 7 + this.font.width(newGroup.substring(0, Mth.clamp(ngCaret, 0, newGroup.length())));
                g.fill(cx, fy + 3, cx + 1, fy + 13, GuiStyle.ACCENT);
            }
        }
        boolean addHov = hov(mouseX, mouseY, addX, fy, addX + 20, fy + 15);
        GuiStyle.button(g, this.font, addX, fy, addX + 20, fy + 15, "+", addHov, !newGroup.isBlank());
        boolean delHov = hov(mouseX, mouseY, delX, fy, delX + 20, fy + 15);
        GuiStyle.button(g, this.font, delX, fy, delX + 20, fy + 15, "-", delHov, !"default".equals(selGroup));

        Set<String> nodes = groups.getOrDefault(selGroup, Set.of());
        sectionHeader(g, rightX1, rightX2, rowsY + 1, selGroup, nodes.size() + " / " + NODES.size());
        int ny = rowsY + 20;
        for (String node : NODES) {
            boolean on = nodes.contains(node);
            boolean h = hov(mouseX, mouseY, rightX1 + 2, ny, rightX2 - 2, ny + PERM_H);
            if (h) {
                g.fill(rightX1 + 2, ny, rightX2 - 2, ny + PERM_H, GuiStyle.ROWHOVER);
            }
            g.drawString(this.font, nodeLabel(node), rightX1 + 8, ny + 6,
                    on ? GuiStyle.TEXT : GuiStyle.MUTED, false);
            g.drawString(this.font, node, rightX1 + 165, ny + 6, GuiStyle.T(0xFF6B5E48), false);
            toggle(g, selGroup + "|" + node, rightX2 - 8, ny, on, h);
            ny += PERM_H;
        }
    }

    private void renderPlayers(GuiGraphics g, int mouseX, int mouseY) {
        sectionHeader(g, leftX1, leftX2, rowsY + 1,
                I18n.get("panoptic.adm.tab_players"), String.valueOf(players.size()));
        int bottom = listBottom();
        int scrollPx = (int) Math.round(listScroll * ROW_H);
        g.enableScissor(leftX1 + 1, listY1, leftX2 - 1, bottom);
        int y = listY1 + 2 - scrollPx;
        if (players.isEmpty()) {
            g.drawString(this.font, I18n.get("panoptic.adm.no_players"), leftX1 + 8, listY1 + 6, GuiStyle.DIM, false);
        }
        for (AdminStatePacket.PlayerRow p : players) {
            if (y + ROW_H >= listY1 && y <= bottom) {
                boolean sel = p.id().equals(selPlayer);
                boolean h = hov(mouseX, mouseY, leftX1 + 2, y, leftX2 - 8, y + ROW_H)
                        && mouseY >= listY1 && mouseY < bottom;
                GuiStyle.row(g, leftX1 + 2, y, leftX2 - 8, y + ROW_H, h, sel);
                if (sel) {
                    g.fill(leftX1 + 2, y + 2, leftX1 + 4, y + ROW_H - 2, GuiStyle.ACCENT);
                }
                g.drawString(this.font, trim(p.name(), 115), leftX1 + 9, y + 5,
                        sel ? GuiStyle.ACCENT : GuiStyle.TEXT, false);
                String grp = trim(p.group().isEmpty() ? "default" : p.group(), 74);
                int gw = this.font.width(grp) + 8;
                g.fill(leftX2 - 10 - gw, y + 3, leftX2 - 10, y + ROW_H - 3, GuiStyle.T(0x2455B4FF));
                g.drawCenteredString(this.font, grp, leftX2 - 10 - gw / 2, y + 5, GuiStyle.MUTED);
            }
            y += ROW_H;
        }
        g.disableScissor();
        drawListScrollbar(g, players.size());

        AdminStatePacket.PlayerRow row = selected();
        if (row == null) {
            sectionHeader(g, rightX1, rightX2, rowsY + 1, I18n.get("panoptic.adm.tab_players"), null);
            g.drawCenteredString(this.font, I18n.get("panoptic.adm.select_player"),
                    (rightX1 + rightX2) / 2, (rowsY + panY2) / 2, GuiStyle.DIM);
            return;
        }
        sectionHeader(g, rightX1, rightX2, rowsY + 1, row.name(), null);
        String grpLabel = I18n.get("panoptic.adm.group", row.group().isEmpty() ? "default" : row.group());
        int gw = this.font.width(grpLabel) + 22;
        grpX2 = rightX2 - 6;
        grpX1 = grpX2 - gw;
        grpY1 = rowsY + 1;
        grpY2 = rowsY + 15;
        boolean gHov = hov(mouseX, mouseY, grpX1, grpY1, grpX2, grpY2);
        GuiStyle.button(g, this.font, grpX1, grpY1, grpX2, grpY2, "", gHov || grpOpen, true);
        g.drawString(this.font, grpLabel, grpX1 + 7, grpY1 + 4,
                gHov || grpOpen ? GuiStyle.ACCENT : GuiStyle.TEXT, false);
        Icons.iconTriDown(g, grpX2 - 12, grpY1 + 6, gHov || grpOpen ? GuiStyle.ACCENT : GuiStyle.MUTED);

        int ny = rowsY + 20;
        for (String node : PLAYER_NODES) {
            int state = row.allow().contains(node) ? 1 : row.deny().contains(node) ? -1 : 0;
            boolean h = hov(mouseX, mouseY, rightX1 + 2, ny, rightX2 - 2, ny + PERM_H);
            if (h && !grpOpen) {
                g.fill(rightX1 + 2, ny, rightX2 - 2, ny + PERM_H, GuiStyle.ROWHOVER);
            }
            g.drawString(this.font, nodeLabel(node), rightX1 + 8, ny + 6,
                    state < 0 ? GuiStyle.DIM : GuiStyle.TEXT, false);
            g.drawString(this.font, node, rightX1 + 165, ny + 6, GuiStyle.T(0xFF6B5E48), false);
            triState(g, rightX2 - 62, ny + 2, state, grpOpen ? -1 : mouseX, mouseY);
            ny += PERM_H;
        }
    }

    private List<String> dropNames() {
        return new ArrayList<>(groups.keySet());
    }

    private int dropHeight(int count) {
        return Math.min(count, 8) * DROP_ROW + 4;
    }

    private void renderGroupDrop(GuiGraphics g, int mouseX, int mouseY) {
        List<String> names = dropNames();
        int visible = Math.min(names.size(), 8);
        int h = dropHeight(names.size());
        int x1 = grpX1;
        int x2 = grpX2;
        int y1 = grpY2 + 2;
        int y2 = y1 + h;
        grpTarget = Mth.clamp(grpTarget, 0, Math.max(0, names.size() - visible));
        g.pose().pushPose();
        g.pose().translate(0, 0, 260);
        g.fill(x1 + 2, y1 + 3, x2 + 2, y2 + 3, 0x66000000);
        GuiStyle.plate(g, x1, y1, x2, y2, GuiStyle.T(0xF8262013), GuiStyle.T(0xF8170F08), GuiStyle.BORDER_T);
        g.enableScissor(x1 + 1, y1 + 2, x2 - 1, y2 - 2);
        int scrollPx = (int) Math.round(grpScroll * DROP_ROW);
        int y = y1 + 2 - scrollPx;
        AdminStatePacket.PlayerRow row = selected();
        String cur = row == null || row.group().isEmpty() ? "default" : row.group();
        for (String name : names) {
            if (y + DROP_ROW >= y1 && y <= y2) {
                boolean active = name.equals(cur);
                boolean h2 = hov(mouseX, mouseY, x1 + 1, y, x2 - (names.size() > 8 ? 6 : 1), y + DROP_ROW)
                        && mouseY >= y1 && mouseY < y2;
                if (h2) {
                    g.fill(x1 + 1, y, x2 - 1, y + DROP_ROW, GuiStyle.ROWHOVER);
                }
                g.drawString(this.font, trim(name, x2 - x1 - 20), x1 + 6, y + 3,
                        active ? GuiStyle.ACCENT : h2 ? GuiStyle.TEXT : GuiStyle.MUTED, false);
                if (active) {
                    Icons.iconCheck(g, x2 - 14, y + 4, GuiStyle.ACCENT);
                }
            }
            y += DROP_ROW;
        }
        g.disableScissor();
        if (names.size() > visible) {
            int trackY1 = y1 + 2;
            int trackY2 = y2 - 2;
            int trackH = trackY2 - trackY1;
            int thumbH = Math.max(12, trackH * visible / names.size());
            int thumbY = trackY1 + (int) ((trackH - thumbH) * grpScroll / Math.max(1, names.size() - visible));
            g.fill(x2 - 5, trackY1, x2 - 3, trackY2, 0x40000000);
            g.fill(x2 - 5, thumbY, x2 - 3, thumbY + thumbH, grpDrag ? GuiStyle.ACCENT : GuiStyle.BORDER_B);
        }
        g.pose().popPose();
    }

    private void drawListScrollbar(GuiGraphics g, int total) {
        int bottom = listBottom();
        int visible = (bottom - listY1) / ROW_H;
        if (total <= visible) {
            return;
        }
        int trackY1 = listY1 + 2;
        int trackH = bottom - 2 - trackY1;
        int thumbH = Math.max(16, trackH * visible / total);
        int thumbY = trackY1 + (int) ((trackH - thumbH) * listScroll / Math.max(1, total - visible));
        g.fill(leftX2 - 6, trackY1, leftX2 - 3, bottom - 2, 0x40000000);
        g.fill(leftX2 - 6, thumbY, leftX2 - 3, thumbY + thumbH, listDrag ? GuiStyle.ACCENT : GuiStyle.BORDER_B);
    }

    private AdminStatePacket.PlayerRow selected() {
        for (AdminStatePacket.PlayerRow p : players) {
            if (p.id().equals(selPlayer)) {
                return p;
            }
        }
        return null;
    }

    private String nodeLabel(String node) {
        return I18n.get("panoptic.adm.node." + node.substring("panoptic.".length()));
    }

    private static boolean hov(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private String trim(String s, int w) {
        return this.font.width(s) <= w ? s : this.font.plainSubstrByWidth(s, w - 6) + "…";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (grpOpen && tab == 1) {
            if (clickGroupDrop((int) mx, (int) my)) {
                return true;
            }
            grpOpen = false;
            return true;
        }
        if (hov((int) mx, (int) my, panX2 - 70, panY1 + 2, panX2 - 2, panY1 + 16)) {
            onClose();
            return true;
        }
        int tabX = leftX1;
        for (int i = 0; i < 2; i++) {
            String label = I18n.get(i == 0 ? "panoptic.adm.tab_groups" : "panoptic.adm.tab_players");
            int w = this.font.width(label) + 10;
            if (hov((int) mx, (int) my, tabX, panY1 + 22, tabX + w, panY1 + 39)) {
                tab = i;
                listScroll = 0;
                listTarget = 0;
                ngFocus = false;
                grpOpen = false;
                sound(1.1F);
                return true;
            }
            tabX += w + 10;
        }
        String exp = I18n.get("panoptic.adm.export");
        String imp = I18n.get("panoptic.adm.import");
        int impW = this.font.width(imp) + 14;
        int expW = this.font.width(exp) + 14;
        int impX = rightX2 - impW;
        int expX = impX - 6 - expW;
        if (loaded && hov((int) mx, (int) my, expX, panY1 + 22, expX + expW, panY1 + 37)) {
            this.minecraft.keyboardHandler.setClipboard(rawJson);
            doFlash("panoptic.adm.exported");
            sound(1.4F);
            return true;
        }
        if (loaded && hov((int) mx, (int) my, impX, panY1 + 22, impX + impW, panY1 + 37)) {
            String clip = this.minecraft.keyboardHandler.getClipboard();
            if (clip != null && clip.trim().startsWith("{")) {
                send(AdminEditPacket.OP_IMPORT, clip, "");
                doFlash("panoptic.adm.import_sent");
                sound(1.4F);
            } else {
                doFlash("panoptic.adm.import_fail");
                sound(0.7F);
            }
            return true;
        }
        if (!loaded) {
            return super.mouseClicked(mx, my, button);
        }
        if (listScrollbarClick((int) mx, (int) my)) {
            return true;
        }
        if (tab == 0 ? clickGroups((int) mx, (int) my) : clickPlayers((int) mx, (int) my)) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean listScrollbarClick(int mx, int my) {
        int bottom = listBottom();
        int visible = (bottom - listY1) / ROW_H;
        int total = listCount();
        if (total <= visible || mx < leftX2 - 8 || mx > leftX2 - 1 || my < listY1 || my > bottom) {
            return false;
        }
        listDrag = true;
        dragListTo(my);
        return true;
    }

    private void dragListTo(int my) {
        int bottom = listBottom();
        int visible = (bottom - listY1) / ROW_H;
        int total = listCount();
        int trackY1 = listY1 + 2;
        int trackH = bottom - 2 - trackY1;
        double frac = Mth.clamp((my - trackY1) / (double) Math.max(1, trackH), 0, 1);
        listTarget = frac * Math.max(0, total - visible);
    }

    private boolean clickGroupDrop(int mx, int my) {
        List<String> names = dropNames();
        int visible = Math.min(names.size(), 8);
        int h = dropHeight(names.size());
        int y1 = grpY2 + 2;
        int y2 = y1 + h;
        if (!hov(mx, my, grpX1, y1, grpX2, y2)) {
            return false;
        }
        if (names.size() > visible && mx >= grpX2 - 7) {
            grpDrag = true;
            dragGrpTo(my);
            return true;
        }
        int idx = (int) ((my - (y1 + 2) + grpScroll * DROP_ROW) / DROP_ROW);
        if (idx >= 0 && idx < names.size()) {
            AdminStatePacket.PlayerRow row = selected();
            if (row != null) {
                String next = names.get(idx);
                send(AdminEditPacket.OP_PLAYER_GROUP, row.id().toString(),
                        "default".equals(next) ? "" : next);
                sound(1.2F);
            }
            grpOpen = false;
        }
        return true;
    }

    private void dragGrpTo(int my) {
        List<String> names = dropNames();
        int visible = Math.min(names.size(), 8);
        int y1 = grpY2 + 4;
        int trackH = dropHeight(names.size()) - 4;
        double frac = Mth.clamp((my - y1) / (double) Math.max(1, trackH), 0, 1);
        grpTarget = frac * Math.max(0, names.size() - visible);
    }

    private boolean clickGroups(int mx, int my) {
        ngFocus = false;
        List<String> names = new ArrayList<>(groups.keySet());
        int bottom = listBottom();
        if (hov(mx, my, leftX1 + 2, listY1, leftX2 - 8, bottom)) {
            int idx = (int) ((my - (listY1 + 2) + listScroll * ROW_H) / ROW_H);
            if (idx >= 0 && idx < names.size()) {
                selGroup = names.get(idx);
                sound(1.2F);
            }
            return true;
        }
        int fy = panY2 - 26;
        int addX = leftX2 - 46;
        int delX = leftX2 - 22;
        if (hov(mx, my, leftX1 + 2, fy, addX - 4, fy + 15)) {
            ngFocus = true;
            return true;
        }
        if (hov(mx, my, addX, fy, addX + 20, fy + 15) && !newGroup.isBlank()) {
            send(AdminEditPacket.OP_GROUP_CREATE, newGroup.trim(), "");
            newGroup = "";
            ngCaret = 0;
            sound(1.3F);
            return true;
        }
        if (hov(mx, my, delX, fy, delX + 20, fy + 15) && !"default".equals(selGroup)) {
            send(AdminEditPacket.OP_GROUP_REMOVE, selGroup, "");
            selGroup = "default";
            sound(0.8F);
            return true;
        }
        Set<String> nodes = groups.getOrDefault(selGroup, Set.of());
        int ny = rowsY + 20;
        for (String node : NODES) {
            if (hov(mx, my, rightX1 + 2, ny, rightX2 - 2, ny + PERM_H)) {
                boolean on = nodes.contains(node);
                send(on ? AdminEditPacket.OP_GROUP_NODE_REMOVE : AdminEditPacket.OP_GROUP_NODE_ADD, selGroup, node);
                sound(on ? 0.9F : 1.3F);
                return true;
            }
            ny += PERM_H;
        }
        return false;
    }

    private boolean clickPlayers(int mx, int my) {
        int bottom = listBottom();
        if (hov(mx, my, leftX1 + 2, listY1, leftX2 - 8, bottom)) {
            int idx = (int) ((my - (listY1 + 2) + listScroll * ROW_H) / ROW_H);
            if (idx >= 0 && idx < players.size()) {
                selPlayer = players.get(idx).id();
                grpOpen = false;
                sound(1.2F);
            }
            return true;
        }
        AdminStatePacket.PlayerRow row = selected();
        if (row == null) {
            return false;
        }
        if (hov(mx, my, grpX1, grpY1, grpX2, grpY2)) {
            grpOpen = !grpOpen;
            grpScroll = 0;
            grpTarget = 0;
            sound(1.1F);
            return true;
        }
        int ny = rowsY + 20;
        for (String node : PLAYER_NODES) {
            if (hov(mx, my, rightX1 + 2, ny, rightX2 - 2, ny + PERM_H)) {
                int state = row.allow().contains(node) ? 1 : row.deny().contains(node) ? -1 : 0;
                int sx = rightX2 - 62;
                byte op;
                if (mx >= sx && mx < sx + 18) {
                    op = AdminEditPacket.OP_PLAYER_UNSET;
                } else if (mx >= sx + 18 && mx < sx + 36) {
                    op = AdminEditPacket.OP_PLAYER_ALLOW;
                } else if (mx >= sx + 36 && mx < sx + 55) {
                    op = AdminEditPacket.OP_PLAYER_DENY;
                } else {
                    op = state == 0 ? AdminEditPacket.OP_PLAYER_ALLOW
                            : state > 0 ? AdminEditPacket.OP_PLAYER_DENY : AdminEditPacket.OP_PLAYER_UNSET;
                }
                send(op, row.id().toString(), node);
                sound(1.2F);
                return true;
            }
            ny += PERM_H;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (listDrag) {
            dragListTo((int) my);
            return true;
        }
        if (grpDrag) {
            dragGrpTo((int) my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        listDrag = false;
        grpDrag = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (grpOpen && tab == 1) {
            List<String> names = dropNames();
            int visible = Math.min(names.size(), 8);
            grpTarget = Mth.clamp(grpTarget - Math.signum(delta) * 2, 0, Math.max(0, names.size() - visible));
            return true;
        }
        if (mx < leftX2) {
            int bottom = listBottom();
            int visible = (bottom - listY1) / ROW_H;
            listTarget = Mth.clamp(listTarget - Math.signum(delta) * 3, 0, Math.max(0, listCount() - visible));
            return true;
        }
        return super.mouseScrolled(mx, my, 0, delta);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (ngFocus && tab == 0) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                TextOps.Res r = TextOps.type(newGroup, ngCaret, ngSel, c, 32);
                if (r.handled) {
                    newGroup = r.text;
                    ngCaret = r.caret;
                    ngSel = r.sel;
                }
            }
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (grpOpen && keyCode == 256) {
            grpOpen = false;
            return true;
        }
        if (ngFocus && tab == 0) {
            if (keyCode == 256) {
                ngFocus = false;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (!newGroup.isBlank()) {
                    send(AdminEditPacket.OP_GROUP_CREATE, newGroup.trim(), "");
                    newGroup = "";
                    ngCaret = 0;
                    sound(1.3F);
                }
                return true;
            }
            TextOps.Res r = TextOps.key(newGroup, ngCaret, ngSel, keyCode, modifiers, 32);
            if (r.handled) {
                newGroup = r.text;
                ngCaret = r.caret;
                ngSel = r.sel;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}