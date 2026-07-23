package net.mokich.panoptic.screen.screengrab;

import net.minecraft.server.level.ServerPlayer;
import net.mokich.panoptic.Guard;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.api.ui.AnnoBar;
import net.mokich.panoptic.api.util.BgTex;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;

import net.mokich.panoptic.config.Perms;
import net.mokich.panoptic.inspect.InspectStore;
import net.mokich.panoptic.inspect.InspectType;
import net.mokich.panoptic.inspect.Inspectors;
import net.mokich.panoptic.network.GiveRequestPacket;
import net.mokich.panoptic.screenshot.GrabOp;
import net.mokich.panoptic.screenshot.GrabReplay;
import net.mokich.panoptic.screenshot.ScreenGrab;
import net.mokich.panoptic.screenshot.ScreenGrabStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenInspectorScreen extends Screen implements TextTyping {
    @Override
    public boolean gmtTyping() {
        return renaming != null;
    }
    private static final int MARGIN = 14;
    private static final int SLOT_HL = 0x80FFFFFF;
    private static final int MARK_HL = 0x66FFC24B;
    private static final int SIDE_W = 194;
    private static final int LIST_ROW = 20;
    private static final int[] CARD_SIZES = {110, 135, 160, 195, 235, 280};
    private static int cardSizeIdx = 2;

    private ScreenGrab viewing;
    private List<ItemStack> items = new ArrayList<>();
    private final Set<String> marked = new HashSet<>();

    private int panelLeft;
    private int panelRight;
    private int panelTop;
    private int panelBottom;
    private int contentTop;

    private double gridScroll;
    private double gridTarget;
    private double listTarget;
    private long lastNanos;
    private final List<int[]> cardZones = new ArrayList<>();
    private List<ScreenGrab> catalogView = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();
    private final List<int[]> selZones = new ArrayList<>();
    private String catalogTip;

    private int gridTop;
    private int gridViewH;
    private int gridMaxScroll;
    private int gridThumbH;
    private int gridTrackX;
    private int listTopF;
    private int listViewH;
    private int listMaxScroll;
    private int listThumbH;
    private int listTrackX;
    private int dragBar;

    private ScreenGrab ctxGrab;
    private int ctxX;
    private int ctxY;
    private final List<int[]> ctxZones = new ArrayList<>();

    private ScreenGrab renaming;
    private String renameText = "";
    private int renameCaret;
    private int renameSel = -1;
    private final List<int[]> renameZones = new ArrayList<>();

    private double zoom = 1.0;
    private double zoomTarget = 1.0;
    private double anchorX;
    private double anchorY;
    private double offX;
    private double offY;
    private boolean fitDone;
    private boolean helpHover;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private boolean moved;

    private boolean selArea;
    private boolean selAreaRemove;
    private double selAX;
    private double selAY;
    private double selBX;
    private double selBY;

    private boolean drawMode;
    private final AnnoBar drawBar = new AnnoBar();
    private final List<GrabOp> newAnnos = new ArrayList<>();
    private final List<Float> strokePts = new ArrayList<>();
    private boolean stroking;
    private boolean boxing;
    private double boxAX;
    private double boxAY;

    private double listScroll;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private GrabOp hoveredWorld;
    private ItemStack hoverListStack = ItemStack.EMPTY;
    private final List<int[]> rowZones = new ArrayList<>();
    private final List<int[]> btnZones = new ArrayList<>();
    private String sideTip;

    private String flash;
    private long flashUntil;

    private int guiL;
    private int guiR;
    private int guiT;
    private int guiB;
    private int sideL;

    public ScreenInspectorScreen() {
        super(Component.translatable("panoptic.tool.screens"));
    }

    public ScreenInspectorScreen(ScreenGrab grab) {
        this();
        openGrab(grab);
    }

    private void openGrab(ScreenGrab grab) {
        this.viewing = grab;
        this.fitDone = false;
        this.listScroll = 0;
        this.listTarget = 0;
        this.marked.clear();
        this.drawMode = false;
        this.newAnnos.clear();
        Map<String, ItemStack> uniq = new LinkedHashMap<>();
        for (GrabOp o : grab.ops) {
            if ("i".equals(o.t) || "wb".equals(o.t) || "we".equals(o.t)) {
                ItemStack s = o.stack();
                if (!s.isEmpty()) {
                    uniq.putIfAbsent(itemKey(s), s);
                }
            }
        }
        this.items = new ArrayList<>(uniq.values());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelLeft = MARGIN;
        panelRight = this.width - MARGIN;
        panelTop = MARGIN;
        panelBottom = this.height - MARGIN;
        contentTop = panelTop + 20;
        fitDone = false;
    }

    private void sound(float pitch) {
        if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        try {
            renderSafe(g, mouseX, mouseY, partial);
        } catch (Throwable t) {
            Guard.report(t);
            viewing = null;
            g.drawCenteredString(this.font, I18n.get("panoptic.screens.render_error"), this.width / 2, this.height / 2, 0xFFE06666);
        }
    }

    private void renderSafe(GuiGraphics g, int mouseX, int mouseY, float partial) {
        animateScroll();
        renderBackground(g);
        GuiStyle.panel(g, panelLeft, panelTop, panelRight, panelBottom);
        String title = viewing == null
                ? I18n.get("panoptic.screens.title", ScreenGrabStore.grabs().size())
                : (viewing.displayTitle() != null && !viewing.displayTitle().isEmpty() ? viewing.displayTitle() : I18n.get("panoptic.screens.viewing"));
        GuiStyle.panelHeader(g, this.font, panelLeft, panelTop, panelRight, title,
                I18n.get(viewing == null ? "panoptic.screens.close" : "panoptic.screens.back"));

        helpHover = viewing == null && HelpCard.icon(g, this.font,
                panelLeft + 10 + this.font.width(title) + 6, panelTop + 1, mouseX, mouseY);

        if (viewing == null) {
            renderCatalog(g, mouseX, mouseY);
            renderContextMenu(g, mouseX, mouseY);
            renderRename(g, mouseX, mouseY);
        } else {
            renderViewer(g, mouseX, mouseY);
        }

        if (flash != null && System.currentTimeMillis() < flashUntil) {
            int w = this.font.width(flash) + 12;
            g.fill((panelLeft + panelRight) / 2 - w / 2, panelBottom - 18, (panelLeft + panelRight) / 2 + w / 2, panelBottom - 5, GuiStyle.T(0xE0141109));
            g.drawCenteredString(this.font, flash, (panelLeft + panelRight) / 2, panelBottom - 15, GuiStyle.ACCENT);
        }
        drawHelp(g);
    }

    private void drawHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.screens.help_b1"));
        bullets.add(Component.translatable("panoptic.screens.help_b2"));
        bullets.add(Component.translatable("panoptic.screens.help_b3"));
        bullets.add(Component.translatable("panoptic.screens.help_b4"));
        bullets.add(Component.translatable("panoptic.screens.help_b5"));
        List<HelpCard.KeyHint> keys = List.of(
                ModBinds.hint(ModBinds.Bind.SCREENGRAB, "panoptic.screens.help_capture"),
                new HelpCard.KeyHint(I18n.get("panoptic.screens.g_wheel"), I18n.get("panoptic.screens.g_wheel_d")),
                new HelpCard.KeyHint(I18n.get("panoptic.screens.g_sel"), I18n.get("panoptic.screens.g_sel_d")),
                new HelpCard.KeyHint(I18n.get("panoptic.screens.g_area"), I18n.get("panoptic.screens.g_area_d")));
        HelpCard.render(g, this.font, this.width, this.height, helpHover,
                Component.translatable("panoptic.screens.help_title"),
                Component.translatable("panoptic.screens.help_sum"), bullets, keys);
    }

    private void animateScroll() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        double f = 1.0 - Math.exp(-dt * 16.0);
        gridScroll += (gridTarget - gridScroll) * f;
        listScroll += (listTarget - listScroll) * f;
        if (Math.abs(gridTarget - gridScroll) < 0.4) {
            gridScroll = gridTarget;
        }
        if (Math.abs(listTarget - listScroll) < 0.4) {
            listScroll = listTarget;
        }
        if (zoom != zoomTarget) {
            double old = zoom;
            zoom += (zoomTarget - zoom) * f;
            if (Math.abs(zoomTarget - zoom) < zoomTarget * 0.002) {
                zoom = zoomTarget;
            }
            offX = anchorX - (anchorX - offX) * (zoom / old);
            offY = anchorY - (anchorY - offY) * (zoom / old);
        }
    }

    private void rebuildCatalogView() {
        catalogView = new ArrayList<>();
        List<ScreenGrab> grabs = ScreenGrabStore.grabs();
        Set<Long> alive = new HashSet<>();
        for (ScreenGrab grab : grabs) {
            alive.add(grab.id);
            if (grab.favorite) {
                catalogView.add(grab);
            }
        }
        for (ScreenGrab grab : grabs) {
            if (!grab.favorite) {
                catalogView.add(grab);
            }
        }
        selectedIds.retainAll(alive);
    }

    private void renderCatalog(GuiGraphics g, int mouseX, int mouseY) {
        cardZones.clear();
        selZones.clear();
        catalogTip = null;
        rebuildCatalogView();
        List<ScreenGrab> grabs = catalogView;
        int left = panelLeft + 8;
        int right = panelRight - 8;
        int top = contentTop;
        int bottom = panelBottom - 6;
        gridTop = top;
        gridViewH = bottom - top;
        gridTrackX = right + 1;
        if (grabs.isEmpty()) {
            gridMaxScroll = 0;
            g.drawCenteredString(this.font, I18n.get("panoptic.screens.empty"), (left + right) / 2, (top + bottom) / 2 - 8, GuiStyle.DIM);
            g.drawCenteredString(this.font, I18n.get("panoptic.screens.empty_hint", ModBinds.label(ModBinds.Bind.SCREENGRAB)),
                    (left + right) / 2, (top + bottom) / 2 + 4, GuiStyle.MUTED);
            return;
        }

        int gap = 8;
        int cardW = Math.min(CARD_SIZES[cardSizeIdx], right - left);
        int cols = Math.max(1, (right - left + gap) / (cardW + gap));
        cardW = (right - left - gap * (cols - 1)) / cols;
        int cardH = cardW * 3 / 4 + 28;
        int rows = (grabs.size() + cols - 1) / cols;
        int contentH = rows * (cardH + gap);
        int viewH = bottom - top;
        int maxScroll = Math.max(0, contentH - viewH);
        gridMaxScroll = maxScroll;
        gridTarget = Mth.clamp(gridTarget, 0, maxScroll);
        gridScroll = Mth.clamp(gridScroll, 0, maxScroll);

        boolean modal = ctxGrab != null || renaming != null;
        g.enableScissor(left, top, right, bottom);
        int y0 = top - (int) Math.round(gridScroll);
        for (int i = 0; i < grabs.size(); i++) {
            ScreenGrab grab = grabs.get(i);
            int cx = left + (i % cols) * (cardW + gap);
            int cy = y0 + (i / cols) * (cardH + gap);
            if (cy + cardH < top || cy > bottom) {
                continue;
            }
            boolean sel = selectedIds.contains(grab.id);
            boolean hov = mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH
                    && mouseY >= top && mouseY <= bottom && !modal;
            int border = sel ? GuiStyle.ACCENT : grab.favorite ? GuiStyle.BORDER_T : hov ? GuiStyle.BORDER_T : GuiStyle.BORDER;
            GuiStyle.plate(g, cx, cy, cx + cardW, cy + cardH,
                    hov ? GuiStyle.T(0xFF3A2F1B) : GuiStyle.T(0xFF241E14), hov ? GuiStyle.T(0xFF241D11) : GuiStyle.T(0xFF17120B), border);
            if (sel) {
                g.fill(cx + 1, cy + 1, cx + cardW - 1, cy + cardH - 1, GuiStyle.T(0x2AE8C06C));
            }
            drawThumb(g, grab, cx + 3, cy + 3, cardW - 6, cardH - 31);
            String label = grab.displayTitle() != null && !grab.displayTitle().isEmpty()
                    ? grab.displayTitle() : I18n.get("panoptic.screens.src_gui");
            g.drawString(this.font, trim(label, cardW - 12), cx + 5, cy + cardH - 21, hov ? GuiStyle.TEXT : GuiStyle.MUTED);
            g.drawString(this.font, relTime(grab.id), cx + 5, cy + cardH - 11, GuiStyle.DIM);
            if (hov && this.font.width(label) > cardW - 12 && mouseY >= cy + cardH - 22 && mouseY < cy + cardH - 10) {
                catalogTip = label;
            }

            int bs = 14;
            int sx1 = cx + 4;
            int sy1 = cy + 4;
            int dx1 = cx + cardW - 4 - bs;
            boolean starHov = hov && mouseX >= sx1 && mouseX < sx1 + bs && mouseY >= sy1 && mouseY < sy1 + bs;
            boolean delHov = hov && mouseX >= dx1 && mouseX < dx1 + bs && mouseY >= sy1 && mouseY < sy1 + bs;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 350.0F);
            if (grab.favorite || hov) {
                g.fill(sx1, sy1, sx1 + bs, sy1 + bs, GuiStyle.T(0xC81A140C));
                GuiStyle.rect(g, sx1, sy1, sx1 + bs, sy1 + bs, starHov ? GuiStyle.ACCENT : GuiStyle.BORDER);
                Icons.iconStar(g, sx1 + 2, sy1 + 2, grab.favorite ? GuiStyle.ACCENT : starHov ? GuiStyle.TEXT : GuiStyle.DIM);
                if (starHov) {
                    catalogTip = I18n.get(grab.favorite ? "panoptic.screens.ctx_unfav" : "panoptic.screens.ctx_fav");
                }
            }
            if (hov) {
                g.fill(dx1, sy1, dx1 + bs, sy1 + bs, delHov ? 0xC8401414 : GuiStyle.T(0xC81A140C));
                GuiStyle.rect(g, dx1, sy1, dx1 + bs, sy1 + bs, delHov ? 0xFFFF6B6B : GuiStyle.BORDER);
                Icons.iconCross(g, dx1 + 4, sy1 + 4, delHov ? 0xFFFF6B6B : GuiStyle.DIM);
                if (delHov) {
                    catalogTip = I18n.get("panoptic.screens.ctx_delete");
                }
            }
            g.pose().popPose();
            cardZones.add(new int[]{cx, cy, cx + cardW, cy + cardH, i,
                    dx1, sy1, dx1 + bs, sy1 + bs, sx1, sy1, sx1 + bs, sy1 + bs});
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = bottom - top;
            gridThumbH = Math.max(20, (int) (trackH * (float) viewH / contentH));
            int thumbY = top + (int) ((trackH - gridThumbH) * (gridScroll / maxScroll));
            g.fill(right + 2, top, right + 5, bottom, 0x40000000);
            g.fill(right + 2, thumbY, right + 5, thumbY + gridThumbH, dragBar == 2 ? GuiStyle.ACCENT : GuiStyle.BORDER_B);
        }

        if (!selectedIds.isEmpty() && !modal) {
            int n = selectedIds.size();
            String[] labels = {
                    I18n.get("panoptic.screens.sel_fav", n),
                    I18n.get("panoptic.screens.sel_del", n),
                    I18n.get("panoptic.screens.sel_clear")
            };
            int[] w = new int[labels.length];
            int total = 0;
            for (int i = 0; i < labels.length; i++) {
                w[i] = this.font.width(labels[i]) + 14;
                total += w[i] + 4;
            }
            total -= 4;
            int bx = (left + right) / 2 - total / 2;
            int by = bottom - 20;
            GuiStyle.plate(g, bx - 6, by - 4, bx + total + 6, by + 17, GuiStyle.T(0xF2332A19), GuiStyle.T(0xF21D1810), GuiStyle.BORDER_B);
            for (int i = 0; i < labels.length; i++) {
                boolean hov = mouseX >= bx && mouseX < bx + w[i] && mouseY >= by && mouseY < by + 13;
                GuiStyle.button(g, this.font, bx, by, bx + w[i], by + 13, labels[i], hov, true);
                selZones.add(new int[]{bx, by, bx + w[i], by + 13, i});
                bx += w[i] + 4;
            }
        }

        if (catalogTip != null && !modal) {
            g.renderTooltip(this.font, Component.literal(catalogTip), mouseX, mouseY);
        }
    }

    private void renderContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        ctxZones.clear();
        if (ctxGrab == null) {
            return;
        }
        String[] labels = {
                I18n.get("panoptic.screens.ctx_open"),
                I18n.get(ctxGrab.favorite ? "panoptic.screens.ctx_unfav" : "panoptic.screens.ctx_fav"),
                I18n.get("panoptic.screens.ctx_rename"),
                I18n.get("panoptic.screens.ctx_dup"),
                I18n.get("panoptic.screens.ctx_delete")
        };
        int w = 96;
        for (String l : labels) {
            w = Math.max(w, this.font.width(l) + 20);
        }
        int rowH = 15;
        int h = labels.length * rowH + 4;
        int mx = Math.min(ctxX, panelRight - w - 4);
        int my = Math.min(ctxY, panelBottom - h - 4);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        GuiStyle.plate(g, mx, my, mx + w, my + h, GuiStyle.T(0xF23A2F1B), GuiStyle.T(0xF21D1810), GuiStyle.ACCENT);
        for (int i = 0; i < labels.length; i++) {
            int ry = my + 2 + i * rowH;
            boolean hov = mouseX >= mx + 2 && mouseX < mx + w - 2 && mouseY >= ry && mouseY < ry + rowH;
            if (hov) {
                g.fill(mx + 2, ry, mx + w - 2, ry + rowH, GuiStyle.ROWHOVER);
            }
            int col = i == 4 ? (hov ? 0xFFFF6B6B : 0xFFCF8A8A) : hov ? GuiStyle.ACCENT : GuiStyle.MUTED;
            g.drawString(this.font, labels[i], mx + 8, ry + 4, col);
            ctxZones.add(new int[]{mx + 2, ry, mx + w - 2, ry + rowH, i});
        }
        g.pose().popPose();
    }

    private void renderRename(GuiGraphics g, int mouseX, int mouseY) {
        renameZones.clear();
        if (renaming == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 350.0F);
        g.fill(panelLeft, panelTop + 15, panelRight, panelBottom, 0xB0000000);
        int w = Math.min(300, panelRight - panelLeft - 40);
        int cx = (panelLeft + panelRight) / 2;
        int cy = (panelTop + panelBottom) / 2;
        int bx1 = cx - w / 2;
        int bx2 = cx + w / 2;
        int by1 = cy - 30;
        int by2 = cy + 30;
        GuiStyle.plate(g, bx1, by1, bx2, by2, GuiStyle.T(0xF22B2418), GuiStyle.T(0xF21D1810), GuiStyle.ACCENT);
        g.drawString(this.font, I18n.get("panoptic.screens.rename_title"), bx1 + 8, by1 + 6, GuiStyle.ACCENT);
        int fx1 = bx1 + 8;
        int fx2 = bx2 - 8;
        int fy1 = by1 + 18;
        int fy2 = by1 + 32;
        GuiStyle.plate(g, fx1, fy1, fx2, fy2, GuiStyle.SEARCH_BG, 0xFF120E08, GuiStyle.BORDER_T);
        int fieldW = fx2 - fx1 - 8;
        int caretPx = this.font.width(renameText.substring(0, Math.min(renameCaret, renameText.length())));
        int scx = Math.max(0, caretPx - fieldW + 2);
        g.enableScissor(fx1 + 3, fy1, fx2 - 3, fy2);
        if (renameHasSel()) {
            int a = Math.min(renameSel, renameCaret);
            int b = Math.max(renameSel, renameCaret);
            int ax = this.font.width(renameText.substring(0, a));
            int bx = this.font.width(renameText.substring(0, b));
            g.fill(fx1 + 4 - scx + ax, fy1 + 2, fx1 + 4 - scx + bx, fy2 - 2, 0x883A6EA5);
        }
        g.drawString(this.font, renameText, fx1 + 4 - scx, fy1 + 3, GuiStyle.TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            g.fill(fx1 + 4 - scx + caretPx, fy1 + 2, fx1 + 5 - scx + caretPx, fy2 - 2, GuiStyle.ACCENT);
        }
        g.disableScissor();

        String okL = I18n.get("panoptic.screens.rename_ok");
        String noL = I18n.get("panoptic.screens.rename_cancel");
        int okW = Math.max(52, this.font.width(okL) + 14);
        int noW = Math.max(52, this.font.width(noL) + 14);
        int okX2 = bx2 - 8;
        int okX1 = okX2 - okW;
        int noX2 = okX1 - 4;
        int noX1 = noX2 - noW;
        int btnY1 = by2 - 20;
        int btnY2 = by2 - 6;
        boolean okHov = mouseX >= okX1 && mouseX < okX2 && mouseY >= btnY1 && mouseY < btnY2;
        boolean noHov = mouseX >= noX1 && mouseX < noX2 && mouseY >= btnY1 && mouseY < btnY2;
        GuiStyle.button(g, this.font, noX1, btnY1, noX2, btnY2, noL, noHov, true);
        GuiStyle.button(g, this.font, okX1, btnY1, okX2, btnY2, okL, okHov, true, true);
        renameZones.add(new int[]{okX1, btnY1, okX2, btnY2, 0});
        renameZones.add(new int[]{noX1, btnY1, noX2, btnY2, 1});
        g.pose().popPose();
    }

    private void backdrop(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fillGradient(x1, y1, x2, y2, GuiStyle.T(0xFF241D13), GuiStyle.T(0xFF120D08));
        GuiStyle.rect(g, x1, y1, x2, y2, GuiStyle.BORDER);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, GuiStyle.T(0x18FFE7B0));
        g.fill(x1, y2 - 1, x2, y2, 0x30000000);
    }

    private void drawThumb(GuiGraphics g, ScreenGrab grab, int x, int y, int w, int h) {
        backdrop(g, x, y, x + w, y + h);
        if (grab.regionW <= 0 || grab.regionH <= 0) {
            return;
        }
        double fit = Math.min(w / (double) grab.regionW, h / (double) grab.regionH);
        double ox = x + (w - grab.regionW * fit) / 2.0;
        double oy = y + (h - grab.regionH * fit) / 2.0;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        ResourceLocation bgRl = BgTex.get(grab);
        if (bgRl != null) {
            GrabReplay.blitUV(g, bgRl, (float) ox, (float) oy,
                    (float) (ox + grab.regionW * fit), (float) (oy + grab.regionH * fit), 0, 1, 0, 1);
            GrabReplay.replay(g, grab.ops, ox, oy, fit, this.font, -1, -1, null, false, true);
        } else if (!grab.ops.isEmpty()) {
            GrabReplay.replay(g, grab.ops, ox, oy, fit, this.font, -1, -1, null, false);
        }
        g.disableScissor();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        GuiStyle.rect(g, x, y, x + w, y + h, GuiStyle.BORDER);
        g.pose().popPose();
    }

    private void renderViewer(GuiGraphics g, int mouseX, int mouseY) {
        sideL = panelRight - SIDE_W;
        guiL = panelLeft + 6;
        guiR = sideL - 6;
        guiT = contentTop;
        guiB = panelBottom - 6;

        if (!fitDone) {
            fitDone = true;
            double fit = Math.min((guiR - guiL) / (double) Math.max(1, viewing.regionW),
                    (guiB - guiT) / (double) Math.max(1, viewing.regionH));
            zoom = Mth.clamp(fit, 0.1, 4.0);
            zoomTarget = zoom;
            offX = guiL + ((guiR - guiL) - viewing.regionW * zoom) / 2.0;
            offY = guiT + ((guiB - guiT) - viewing.regionH * zoom) / 2.0;
        }

        backdrop(g, guiL, guiT, guiR, guiB);
        int[] hoverRect = new int[4];
        g.enableScissor(guiL + 1, guiT + 1, guiR - 1, guiB - 1);
        ResourceLocation bgRl = BgTex.get(viewing);
        if (bgRl != null) {
            GrabReplay.blitUV(g, bgRl, (float) offX, (float) offY,
                    (float) (offX + viewing.regionW * zoom), (float) (offY + viewing.regionH * zoom), 0, 1, 0, 1);
            hoveredStack = GrabReplay.replay(g, viewing.ops, offX, offY, zoom, this.font, mouseX, mouseY, hoverRect, true, true);
        } else {
            hoveredStack = GrabReplay.replay(g, viewing.ops, offX, offY, zoom, this.font, mouseX, mouseY, hoverRect);
        }
        int frX1 = (int) Math.round(offX);
        int frY1 = (int) Math.round(offY);
        int frX2 = (int) Math.round(offX + viewing.regionW * zoom);
        int frY2 = (int) Math.round(offY + viewing.regionH * zoom);
        GuiStyle.rect(g, frX1 - 2, frY1 - 2, frX2 + 2, frY2 + 2, GuiStyle.T(0xFF0F0C07));
        GuiStyle.rect(g, frX1 - 1, frY1 - 1, frX2 + 1, frY2 + 1, GuiStyle.BORDER_B);
        hoveredWorld = null;
        if (!drawMode && hoveredStack.isEmpty()
                && mouseX >= guiL && mouseX < guiR && mouseY >= guiT && mouseY < guiB) {
            for (GrabOp o : viewing.ops) {
                if (!"wb".equals(o.t) && !"we".equals(o.t)) {
                    continue;
                }
                int[] r = worldRect(o);
                if (mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3]) {
                    hoveredWorld = o;
                }
            }
        }
        if (drawMode) {
            g.pose().pushPose();
            g.pose().translate(offX, offY, 280.0);
            g.pose().scale((float) zoom, (float) zoom, 1.0F);
            for (GrabOp op : newAnnos) {
                GrabReplay.drawAnno(g, op);
            }
            drawLiveAnno(g, mouseX, mouseY);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        if (!marked.isEmpty()) {
            for (GrabOp o : viewing.ops) {
                if (!zoneOp(o)) {
                    continue;
                }
                ItemStack s = o.stack();
                if (!s.isEmpty() && marked.contains(itemKey(s))) {
                    int[] r = worldRect(o);
                    g.fill(r[0], r[1], r[2], r[3], GuiStyle.T(MARK_HL));
                    GuiStyle.rect(g, r[0], r[1], r[2], r[3], GuiStyle.ACCENT);
                }
            }
        }
        if (!hoverListStack.isEmpty()) {
            for (GrabOp o : viewing.ops) {
                if (zoneOp(o) && sameItem(o.stack(), hoverListStack)) {
                    int[] r = worldRect(o);
                    g.fillGradient(r[0], r[1], r[2], r[3], SLOT_HL, SLOT_HL);
                }
            }
        }
        if (!hoveredStack.isEmpty() && !drawMode) {
            g.fillGradient(hoverRect[0], hoverRect[1], hoverRect[2], hoverRect[3], SLOT_HL, SLOT_HL);
        }
        if (hoveredWorld != null) {
            int[] r = worldRect(hoveredWorld);
            g.fill(r[0], r[1], r[2], r[3], 0x28FFFFFF);
            GuiStyle.rect(g, r[0], r[1], r[2], r[3], GuiStyle.ACCENT);
        }
        if (selArea) {
            int ax = (int) Math.min(selAX, selBX);
            int ay = (int) Math.min(selAY, selBY);
            int bx = (int) Math.max(selAX, selBX);
            int by = (int) Math.max(selAY, selBY);
            g.fill(ax, ay, bx, by, selAreaRemove ? 0x22FF5B5B : GuiStyle.T(0x22E8C06C));
            GuiStyle.rect(g, ax, ay, bx, by, selAreaRemove ? 0xFFFF6B6B : GuiStyle.ACCENT);
        }
        g.pose().popPose();
        g.disableScissor();

        if (!hoveredStack.isEmpty() && !drawMode) {
            try {
                g.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
            } catch (Throwable ignored) {
            }
        } else if (hoveredWorld != null && hoveredWorld.data != null) {
            List<Component> lines = new ArrayList<>();
            for (String ln : hoveredWorld.data.split("\n")) {
                lines.add(Component.literal(ln));
            }
            try {
                g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            } catch (Throwable ignored) {
            }
        }

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 500.0F);
        String zl = I18n.get("panoptic.screens.zoom", (int) Math.round(zoom * 100));
        g.fill(guiL + 1, guiB - 12, guiL + 7 + this.font.width(zl), guiB - 1, GuiStyle.T(0xA0141109));
        g.drawString(this.font, zl, guiL + 4, guiB - 10, GuiStyle.MUTED);
        g.pose().popPose();

        renderSidePanel(g, mouseX, mouseY);

        if (drawMode) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 450.0F);
            drawBar.render(g, this.font, (guiL + guiR) / 2, guiT + 5, mouseX, mouseY, !newAnnos.isEmpty());
            if (drawBar.hoverTip != null) {
                g.renderTooltip(this.font, Component.literal(drawBar.hoverTip), mouseX, mouseY);
            }
            g.pose().popPose();
        }
    }

    private void drawLiveAnno(GuiGraphics g, int mouseX, int mouseY) {
        int c = drawBar.color();
        int th = drawBar.thickness;
        if (stroking && strokePts.size() >= 4) {
            for (int i = 0; i + 3 < strokePts.size(); i += 2) {
                GrabReplay.stampLine(g, strokePts.get(i), strokePts.get(i + 1), strokePts.get(i + 2), strokePts.get(i + 3), th, c);
            }
        }
        if (boxing) {
            double mx = canvasX(mouseX);
            double my = canvasY(mouseY);
            int ax = (int) Math.min(boxAX, mx);
            int ay = (int) Math.min(boxAY, my);
            int bx = (int) Math.max(boxAX, mx);
            int by = (int) Math.max(boxAY, my);
            g.fill(ax, ay, bx, ay + th, c);
            g.fill(ax, by - th, bx, by, c);
            g.fill(ax, ay, ax + th, by, c);
            g.fill(bx - th, ay, bx, by, c);
        }
    }

    private static boolean zoneOp(GrabOp o) {
        return "i".equals(o.t) || "wb".equals(o.t) || "we".equals(o.t);
    }

    private int[] worldRect(GrabOp o) {
        float bx2 = "i".equals(o.t) ? o.x1 + o.scale : Math.max(o.x1, o.x2);
        float by2 = "i".equals(o.t) ? o.y1 + o.scale : Math.max(o.y1, o.y2);
        float bx1 = "i".equals(o.t) ? o.x1 : Math.min(o.x1, o.x2);
        float by1 = "i".equals(o.t) ? o.y1 : Math.min(o.y1, o.y2);
        return new int[]{
                (int) Math.round(offX + bx1 * zoom),
                (int) Math.round(offY + by1 * zoom),
                (int) Math.round(offX + bx2 * zoom),
                (int) Math.round(offY + by2 * zoom)
        };
    }

    private double canvasX(double mx) {
        return Mth.clamp((mx - offX) / zoom, 0, viewing.regionW);
    }

    private double canvasY(double my) {
        return Mth.clamp((my - offY) / zoom, 0, viewing.regionH);
    }

    private void renderSidePanel(GuiGraphics g, int mouseX, int mouseY) {
        rowZones.clear();
        btnZones.clear();
        hoverListStack = ItemStack.EMPTY;
        int x1 = sideL;
        int x2 = panelRight - 6;
        int top = contentTop;
        GuiStyle.plate(g, x1, top, x2, panelBottom - 6, GuiStyle.T(0xF22B2418), GuiStyle.T(0xF21D1810), GuiStyle.BORDER);

        g.drawString(this.font, I18n.get("panoptic.screens.meta"), x1 + 6, top + 5, GuiStyle.ACCENT);
        int y = top + 17;
        kv(g, x1, x2, y, I18n.get("panoptic.screens.size"), viewing.regionW + " x " + viewing.regionH);
        y += 10;
        kv(g, x1, x2, y, I18n.get("panoptic.screens.items_short"), String.valueOf(viewing.itemCount()));
        y += 10;
        kv(g, x1, x2, y, I18n.get("panoptic.screens.unique"), String.valueOf(items.size()));
        y += 10;
        int selN = selection().size();
        kv(g, x1, x2, y, I18n.get("panoptic.screens.marked"), marked.isEmpty() ? "—" : String.valueOf(selN));
        y += 12;
        GuiStyle.divider(g, x1 + 5, x2 - 5, y);
        y += 4;

        int btnY = panelBottom - 6 - 47;
        int listTop = y;
        int listBottom = btnY - 4;
        int viewH = listBottom - listTop;
        int contentH = items.size() * LIST_ROW;
        int maxScroll = Math.max(0, contentH - viewH);
        listTopF = listTop;
        listViewH = viewH;
        listMaxScroll = maxScroll;
        listTrackX = x2 - 6;
        listTarget = Mth.clamp(listTarget, 0, maxScroll);
        listScroll = Mth.clamp(listScroll, 0, maxScroll);

        g.enableScissor(x1 + 2, listTop, x2 - 2, listBottom);
        int ry = listTop - (int) Math.round(listScroll);
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (ry + LIST_ROW >= listTop && ry <= listBottom) {
                boolean hov = mouseX >= x1 + 2 && mouseX < x2 - 2 && mouseY >= ry && mouseY < ry + LIST_ROW
                        && mouseY >= listTop && mouseY < listBottom;
                boolean mk = marked.contains(itemKey(st));
                if (mk) {
                    g.fill(x1 + 2, ry, x2 - 2, ry + LIST_ROW, GuiStyle.T(MARK_HL));
                    g.fill(x1 + 2, ry, x1 + 4, ry + LIST_ROW, GuiStyle.ACCENT);
                }
                if (hov) {
                    GuiStyle.row(g, x1 + 2, ry, x2 - 2, ry + LIST_ROW, true, false);
                    hoverListStack = st;
                }
                GuiStyle.slot(g, x1 + 3, ry + 1, 18);
                g.renderItem(st, x1 + 4, ry + 2);
                g.renderItemDecorations(this.font, st, x1 + 4, ry + 2);
                g.drawString(this.font, trim(st.getHoverName().getString(), SIDE_W - 34), x1 + 25, ry + 3, hov ? GuiStyle.TEXT : GuiStyle.MUTED);
                ResourceLocation id = itemRl(st);
                g.drawString(this.font, trim(id == null ? "?" : id.toString(), SIDE_W - 34), x1 + 25, ry + 12, GuiStyle.DIM);
                rowZones.add(new int[]{x1 + 2, ry, x2 - 2, ry + LIST_ROW, i});
            }
            ry += LIST_ROW;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            listThumbH = Math.max(16, (int) (viewH * (float) viewH / contentH));
            int thumbY = listTop + (int) ((viewH - listThumbH) * (listScroll / maxScroll));
            g.fill(x2 - 4, listTop, x2 - 2, listBottom, 0x40000000);
            g.fill(x2 - 4, thumbY, x2 - 2, thumbY + listThumbH, dragBar == 1 ? GuiStyle.ACCENT : GuiStyle.BORDER_B);
        }

        int bw = (x2 - 6 - (x1 + 6) - 4) / 2;
        sideTip = null;
        button(g, 1, x1 + 6, btnY, bw, I18n.get("panoptic.screens.give_sel"), mouseX, mouseY, true);
        button(g, 0, x1 + 6 + bw + 4, btnY, bw, I18n.get("panoptic.screens.to_inspector"), mouseX, mouseY, true);
        button(g, 2, x1 + 6, btnY + 15, bw, I18n.get("panoptic.screens.copy_ids"), mouseX, mouseY, true);
        button(g, 4, x1 + 6 + bw + 4, btnY + 15, bw, I18n.get("panoptic.screens.draw"), mouseX, mouseY, true);
        button(g, 5, x1 + 6, btnY + 30, bw, I18n.get("panoptic.screens.reset"), mouseX, mouseY, true);
        button(g, 3, x1 + 6 + bw + 4, btnY + 30, bw, I18n.get("panoptic.screens.delete"), mouseX, mouseY, true);
        if (sideTip != null) {
            List<Component> lines = new ArrayList<>();
            for (String ln : sideTip.split("\n")) {
                lines.add(Component.literal(ln));
            }
            g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private static final String[] BTN_TIPS = {
            "panoptic.screens.tip.record", "panoptic.screens.tip.give", "panoptic.screens.tip.copy",
            "panoptic.screens.tip.delete", "panoptic.screens.tip.draw", "panoptic.screens.tip.reset"
    };
    private static final ModBinds.Bind[] BTN_BINDS = {
            ModBinds.Bind.VIEW_RECORD, ModBinds.Bind.VIEW_GIVE, ModBinds.Bind.VIEW_COPY,
            ModBinds.Bind.VIEW_DELETE, ModBinds.Bind.VIEW_DRAW, ModBinds.Bind.VIEW_RESET
    };

    private void button(GuiGraphics g, int id, int x, int y, int w, String label, int mouseX, int mouseY, boolean enabled) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13;
        GuiStyle.button(g, this.font, x, y, x + w, y + 13, label, hov && enabled, enabled, id == 4 && drawMode);
        if (enabled) {
            btnZones.add(new int[]{x, y, x + w, y + 13, id});
        }
        if (hov && enabled && id >= 0 && id < BTN_TIPS.length) {
            sideTip = I18n.get(BTN_TIPS[id]) + "\n§8" + ModBinds.label(BTN_BINDS[id]);
        }
    }

    private void kv(GuiGraphics g, int x1, int x2, int y, String k, String v) {
        g.drawString(this.font, k, x1 + 6, y, GuiStyle.DIM);
        g.drawString(this.font, trim(v, SIDE_W - 12 - this.font.width(k) - 6), x2 - 6 - this.font.width(trim(v, SIDE_W - 12 - this.font.width(k) - 6)), y, GuiStyle.TEXT);
    }

    private String trim(String s, int w) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= w) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, w - 6) + "…";
    }

    private static ResourceLocation itemRl(ItemStack s) {
        return ForgeRegistries.ITEMS.getKey(s.getItem());
    }

    private static String itemId(ItemStack s) {
        ResourceLocation r = itemRl(s);
        return r == null ? "?" : r.toString();
    }

    private static String itemKey(ItemStack s) {
        return itemId(s) + "|" + (s.getTag() == null ? "" : s.getTag());
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }

    private static String relTime(long ts) {
        long d = System.currentTimeMillis() - ts;
        if (d < 60_000L) {
            return I18n.get("panoptic.screens.time.now");
        }
        if (d < 3_600_000L) {
            return I18n.get("panoptic.screens.time.min", d / 60_000L);
        }
        if (d < 86_400_000L) {
            return I18n.get("panoptic.screens.time.hour", d / 3_600_000L);
        }
        return I18n.get("panoptic.screens.time.day", d / 86_400_000L);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (viewing == null) {
            if (hasControlDown()) {
                int old = cardSizeIdx;
                cardSizeIdx = Mth.clamp(cardSizeIdx + (delta > 0 ? 1 : -1), 0, CARD_SIZES.length - 1);
                if (cardSizeIdx != old) {
                    sound(delta > 0 ? 1.2F : 1.0F);
                }
                return true;
            }
            gridTarget -= delta * 80;
            return true;
        }
        if (mx >= sideL) {
            listTarget -= delta * 54;
            return true;
        }
        zoomTarget = Mth.clamp(zoomTarget * (delta > 0 ? 1.25 : 1 / 1.25), 0.1, 24.0);
        anchorX = mx;
        anchorY = my;
        return true;
    }

    private void jumpGridBar(double my) {
        if (gridMaxScroll <= 0) {
            return;
        }
        double frac = (my - gridTop - gridThumbH / 2.0) / Math.max(1, gridViewH - gridThumbH);
        gridTarget = Mth.clamp(frac * gridMaxScroll, 0, gridMaxScroll);
        gridScroll = gridTarget;
    }

    private void jumpListBar(double my) {
        if (listMaxScroll <= 0) {
            return;
        }
        double frac = (my - listTopF - listThumbH / 2.0) / Math.max(1, listViewH - listThumbH);
        listTarget = Mth.clamp(frac * listMaxScroll, 0, listMaxScroll);
        listScroll = listTarget;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (renaming != null) {
            for (int[] z : renameZones) {
                if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                    if (z[4] == 0) {
                        commitRename();
                    } else {
                        renaming = null;
                        sound(0.8F);
                    }
                    return true;
                }
            }
            return true;
        }
        if (ctxGrab != null) {
            for (int[] z : ctxZones) {
                if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                    contextAction(z[4]);
                    return true;
                }
            }
            ctxGrab = null;
            sound(0.8F);
            return true;
        }
        if (my >= panelTop && my < panelTop + 16 && mx >= panelRight - 70 && mx < panelRight - 6) {
            if (viewing == null) {
                onClose();
            } else {
                exitViewer();
            }
            return true;
        }
        if (viewing == null) {
            for (int[] z : selZones) {
                if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                    selAction(z[4]);
                    return true;
                }
            }
            if (button == 0 && gridMaxScroll > 0 && mx >= gridTrackX && mx < gridTrackX + 6
                    && my >= gridTop && my < gridTop + gridViewH) {
                jumpGridBar(my);
                dragBar = 2;
                return true;
            }
            for (int[] z : cardZones) {
                if (mx < z[0] || mx >= z[2] || my < z[1] || my >= z[3]) {
                    continue;
                }
                if (z[4] < 0 || z[4] >= catalogView.size()) {
                    continue;
                }
                ScreenGrab grab = catalogView.get(z[4]);
                if (button == 1) {
                    ctxGrab = grab;
                    ctxX = (int) mx;
                    ctxY = (int) my;
                    sound(1.0F);
                    return true;
                }
                if (hasControlDown()) {
                    if (!selectedIds.remove(grab.id)) {
                        selectedIds.add(grab.id);
                    }
                    sound(1.1F);
                    return true;
                }
                if (mx >= z[9] && mx < z[11] && my >= z[10] && my < z[12]) {
                    grab.favorite = !grab.favorite;
                    ScreenGrabStore.save();
                    sound(grab.favorite ? 1.4F : 0.9F);
                    return true;
                }
                if (mx >= z[5] && mx < z[7] && my >= z[6] && my < z[8]) {
                    ScreenGrabStore.remove(grab);
                    sound(0.7F);
                    return true;
                }
                openGrab(grab);
                sound(1.0F);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        if (drawMode) {
            if (drawBar.pickerMouseDown(mx, my)) {
                return true;
            }
            int a = drawBar.click(mx, my);
            if (a >= 0 && a != AnnoBar.ACT_INSIDE) {
                drawAction(a);
                return true;
            }
            if (a == AnnoBar.ACT_INSIDE) {
                return true;
            }
            if (button == 0 && mx >= guiL && mx < guiR && my >= guiT && my < guiB) {
                if (drawBar.tool == AnnoBar.ACT_PEN) {
                    stroking = true;
                    strokePts.clear();
                    strokePts.add((float) canvasX(mx));
                    strokePts.add((float) canvasY(my));
                } else if (drawBar.tool == AnnoBar.ACT_BOX) {
                    boxing = true;
                    boxAX = canvasX(mx);
                    boxAY = canvasY(my);
                } else {
                    dragging = true;
                    moved = false;
                    pressX = mx;
                    pressY = my;
                }
                return true;
            }
            return true;
        }

        if (button == 0 && listMaxScroll > 0 && mx >= listTrackX && mx < listTrackX + 6
                && my >= listTopF && my < listTopF + listViewH) {
            jumpListBar(my);
            dragBar = 1;
            return true;
        }
        for (int[] z : btnZones) {
            if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                sideButton(z[4]);
                return true;
            }
        }
        for (int[] z : rowZones) {
            if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                if (button == 1) {
                    giveMany(List.of(items.get(z[4])));
                } else if (hasShiftDown()) {
                    marked.add(itemKey(items.get(z[4])));
                    sound(1.1F);
                } else if (hasAltDown()) {
                    marked.remove(itemKey(items.get(z[4])));
                    sound(0.9F);
                } else {
                    toggleMark(items.get(z[4]));
                }
                return true;
            }
        }
        if (mx >= guiL && mx < guiR && my >= guiT && my < guiB) {
            if (button == 1 && !hoveredStack.isEmpty()) {
                giveMany(List.of(hoveredStack));
                return true;
            }
            if (button == 1 && hoveredWorld != null && !hoveredWorld.stack().isEmpty()) {
                giveMany(List.of(hoveredWorld.stack()));
                return true;
            }
            if (button == 2) {
                copyNbtUnderMouse();
                return true;
            }
            if (button == 0) {
                if (hasShiftDown() || hasAltDown()) {
                    selArea = true;
                    selAreaRemove = hasAltDown();
                    selAX = mx;
                    selAY = my;
                    selBX = mx;
                    selBY = my;
                    return true;
                }
                dragging = true;
                moved = false;
                pressX = mx;
                pressY = my;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void copyNbtUnderMouse() {
        ItemStack s = !hoveredStack.isEmpty() ? hoveredStack
                : hoveredWorld != null ? hoveredWorld.stack() : ItemStack.EMPTY;
        String out = null;
        if (!s.isEmpty()) {
            out = itemId(s) + (s.getTag() == null ? "" : s.getTag().toString());
        } else if (hoveredWorld != null && hoveredWorld.nbt != null) {
            out = hoveredWorld.nbt;
        }
        if (out != null) {
            this.minecraft.keyboardHandler.setClipboard(out);
            flash(I18n.get("panoptic.screens.copied_nbt"));
            sound(1.4F);
        }
    }

    private void selAction(int id) {
        List<ScreenGrab> sel = new ArrayList<>();
        for (ScreenGrab grab : catalogView) {
            if (selectedIds.contains(grab.id)) {
                sel.add(grab);
            }
        }
        switch (id) {
            case 0 -> {
                boolean allFav = true;
                for (ScreenGrab grab : sel) {
                    allFav &= grab.favorite;
                }
                for (ScreenGrab grab : sel) {
                    grab.favorite = !allFav;
                }
                ScreenGrabStore.save();
                selectedIds.clear();
                sound(1.4F);
            }
            case 1 -> {
                for (ScreenGrab grab : sel) {
                    ScreenGrabStore.remove(grab);
                }
                selectedIds.clear();
                sound(0.7F);
            }
            case 2 -> {
                selectedIds.clear();
                sound(0.9F);
            }
            default -> {
            }
        }
    }

    private void drawAction(int id) {
        switch (id) {
            case AnnoBar.ACT_CURSOR, AnnoBar.ACT_PEN, AnnoBar.ACT_BOX -> drawBar.tool = id;
            case AnnoBar.ACT_COLOR -> drawBar.togglePicker();
            case AnnoBar.ACT_UNDO -> {
                if (!newAnnos.isEmpty()) {
                    newAnnos.remove(newAnnos.size() - 1);
                }
            }
            case AnnoBar.ACT_DONE -> {
                if (!newAnnos.isEmpty()) {
                    viewing.ops.addAll(newAnnos);
                    ScreenGrabStore.save();
                }
                newAnnos.clear();
                drawMode = false;
                drawBar.closePicker();
                sound(1.3F);
            }
            case AnnoBar.ACT_CANCEL -> {
                newAnnos.clear();
                drawMode = false;
                drawBar.closePicker();
                sound(0.8F);
            }
            default -> {
            }
        }
    }

    private void exitViewer() {
        drawMode = false;
        newAnnos.clear();
        viewing = null;
        sound(0.9F);
    }

    private boolean canGive() {
        return this.minecraft.player != null
                && (this.minecraft.player.isCreative() || this.minecraft.getSingleplayerServer() != null
                || Perms.serverSynced());
    }

    private List<ItemStack> selection() {
        if (marked.isEmpty()) {
            return items;
        }
        List<ItemStack> r = new ArrayList<>();
        for (ItemStack s : items) {
            if (marked.contains(itemKey(s))) {
                r.add(s);
            }
        }
        return r;
    }

    private void toggleMark(ItemStack s) {
        if (s.isEmpty()) {
            return;
        }
        String k = itemKey(s);
        if (!marked.remove(k)) {
            marked.add(k);
        }
        sound(1.1F);
    }

    private void giveMany(List<ItemStack> list) {
        if (!Perms.allowed(Perms.Feature.SCREENS_GIVE)) {
            flash(I18n.get("panoptic.perm.denied"));
            if (ModSettings.getBool(ModSettings.UI_SOUNDS)) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.25F));
            }
            return;
        }
        if (list.isEmpty()) {
            return;
        }
        if (!canGive()) {
            flash(I18n.get("panoptic.screens.no_rights"));
            sound(0.7F);
            return;
        }
        int given = 0;
        if (this.minecraft.player.isCreative()) {
            for (ItemStack s : list) {
                if (s.isEmpty()) {
                    continue;
                }
                int slot = this.minecraft.player.getInventory().getFreeSlot();
                if (slot < 0) {
                    break;
                }
                ItemStack give = s.copy();
                give.setCount(give.getMaxStackSize());
                int container = slot < 9 ? slot + 36 : slot;
                this.minecraft.player.getInventory().setItem(slot, give.copy());
                this.minecraft.gameMode.handleCreativeModeItemAdd(give, container);
                given++;
            }
        } else {
            var sv = this.minecraft.getSingleplayerServer();
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack s : list) {
                if (!s.isEmpty()) {
                    ItemStack c = s.copy();
                    c.setCount(c.getMaxStackSize());
                    copies.add(c);
                }
            }
            if (sv != null && !copies.isEmpty()) {
                UUID uid = this.minecraft.player.getUUID();
                sv.execute(() -> {
                    ServerPlayer sp = sv.getPlayerList().getPlayer(uid);
                    if (sp == null) {
                        return;
                    }
                    for (ItemStack c : copies) {
                        sp.getInventory().placeItemBackInInventory(c);
                    }
                });
                given = copies.size();
            } else if (!copies.isEmpty() && Perms.serverSynced()) {
                Panoptic.PERMS_CHANNEL.sendToServer(
                        new GiveRequestPacket(copies));
                given = copies.size();
            }
        }
        if (given == 0) {
            flash(I18n.get("panoptic.screens.inv_full"));
            sound(0.7F);
            return;
        }
        marked.clear();
        flash(given == 1 ? I18n.get("panoptic.screens.given", list.get(0).getHoverName().getString())
                : I18n.get("panoptic.screens.given_n", given));
        sound(1.5F);
    }

    private void sideButton(int id) {
        switch (id) {
            case 0 -> {
                int n = 0;
                for (ItemStack s : selection()) {
                    if (!InspectStore.has(InspectType.ITEM, itemId(s))) {
                        InspectStore.add(Inspectors.item(s));
                        n++;
                    }
                }
                flash(I18n.get("panoptic.screens.added_n", n));
                sound(1.3F);
            }
            case 1 -> giveMany(selection());
            case 2 -> {
                List<ItemStack> sel = selection();
                StringBuilder sb = new StringBuilder();
                for (ItemStack s : sel) {
                    sb.append(itemId(s));
                    if (s.getTag() != null) {
                        sb.append(s.getTag());
                    }
                    sb.append('\n');
                }
                this.minecraft.keyboardHandler.setClipboard(sb.toString());
                flash(I18n.get("panoptic.screens.copied_ids", sel.size()));
                sound(1.4F);
            }
            case 3 -> {
                ScreenGrab del = viewing;
                exitViewer();
                ScreenGrabStore.remove(del);
                sound(0.7F);
            }
            case 4 -> {
                if (drawMode) {
                    drawAction(AnnoBar.ACT_CANCEL);
                } else {
                    drawMode = true;
                    newAnnos.clear();
                    drawBar.tool = AnnoBar.ACT_PEN;
                    drawBar.closePicker();
                    sound(1.2F);
                }
            }
            case 5 -> {
                fitDone = false;
                sound(0.9F);
            }
            default -> {
            }
        }
    }

    private void contextAction(int id) {
        ScreenGrab grab = ctxGrab;
        ctxGrab = null;
        if (grab == null) {
            return;
        }
        switch (id) {
            case 0 -> {
                openGrab(grab);
                sound(1.0F);
            }
            case 1 -> {
                grab.favorite = !grab.favorite;
                ScreenGrabStore.save();
                sound(grab.favorite ? 1.4F : 0.9F);
            }
            case 2 -> startRename(grab);
            case 3 -> {
                ScreenGrab dup = ScreenGrabStore.duplicate(grab);
                String base = grab.displayTitle() != null && !grab.displayTitle().isEmpty()
                        ? grab.displayTitle() : I18n.get("panoptic.screens.src_gui");
                dup.customName = nextCopyName(base);
                ScreenGrabStore.save();
                sound(1.3F);
            }
            case 4 -> {
                if (viewing == grab) {
                    exitViewer();
                }
                ScreenGrabStore.remove(grab);
                sound(0.7F);
            }
            default -> {
            }
        }
    }

    private String nextCopyName(String base) {
        String word = I18n.get("panoptic.screens.copy_word");
        String q = Pattern.quote(word);
        Matcher tail = Pattern
                .compile("(?i)(\\s*\\(?\\s*" + q + "\\s*\\)?(\\s*\\d+)?)+\\s*$").matcher(base);
        String root = tail.find() && tail.start() > 0 ? base.substring(0, tail.start()).trim() : base.trim();
        if (root.isEmpty()) {
            root = I18n.get("panoptic.screens.src_gui");
        }
        Pattern numbered = Pattern
                .compile("(?i)^" + Pattern.quote(root) + "\\s+" + q + "\\s+(\\d+)$");
        int max = 0;
        for (ScreenGrab g : ScreenGrabStore.grabs()) {
            String n = g.displayTitle();
            if (n == null) {
                continue;
            }
            Matcher m = numbered.matcher(n.trim());
            if (m.matches()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return root + " " + word + " " + (max + 1);
    }

    private void startRename(ScreenGrab grab) {
        renaming = grab;
        String base = grab.displayTitle();
        renameText = base == null ? "" : base;
        renameCaret = renameText.length();
        renameSel = renameText.isEmpty() ? -1 : 0;
        sound(1.1F);
    }

    private void commitRename() {
        if (renaming == null) {
            return;
        }
        String t = renameText.trim();
        renaming.customName = t.isEmpty() ? null : t;
        ScreenGrabStore.save();
        renaming = null;
        sound(1.3F);
    }

    private void flash(String msg) {
        flash = msg;
        flashUntil = System.currentTimeMillis() + 1500;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragBar == 1) {
            jumpListBar(my);
            return true;
        }
        if (dragBar == 2) {
            jumpGridBar(my);
            return true;
        }
        if (viewing != null && selArea) {
            selBX = Mth.clamp(mx, guiL, guiR);
            selBY = Mth.clamp(my, guiT, guiB);
            return true;
        }
        if (viewing != null && drawMode && drawBar.pickerMouseDrag(mx, my)) {
            return true;
        }
        if (viewing != null && drawMode && stroking) {
            strokePts.add((float) canvasX(mx));
            strokePts.add((float) canvasY(my));
            return true;
        }
        if (viewing != null && drawMode && boxing) {
            return true;
        }
        if (viewing != null && dragging) {
            if (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3) {
                moved = true;
            }
            offX += dx;
            offY += dy;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragBar != 0 && button == 0) {
            dragBar = 0;
            return true;
        }
        if (viewing != null && selArea && button == 0) {
            selArea = false;
            applyAreaSelection();
            return true;
        }
        if (viewing != null && drawMode && button == 0) {
            drawBar.pickerMouseUp();
            if (stroking) {
                stroking = false;
                if (strokePts.size() >= 4) {
                    GrabOp a = new GrabOp();
                    a.t = "p";
                    a.c1 = drawBar.color();
                    a.scale = drawBar.thickness;
                    float[] arr = new float[strokePts.size()];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = strokePts.get(i);
                    }
                    a.pts = arr;
                    newAnnos.add(a);
                }
                strokePts.clear();
                return true;
            }
            if (boxing) {
                boxing = false;
                double bx = canvasX(mx);
                double by = canvasY(my);
                if (Math.abs(bx - boxAX) > 2 && Math.abs(by - boxAY) > 2) {
                    GrabOp a = new GrabOp();
                    a.t = "r";
                    a.x1 = (float) boxAX;
                    a.y1 = (float) boxAY;
                    a.x2 = (float) bx;
                    a.y2 = (float) by;
                    a.c1 = drawBar.color();
                    a.scale = drawBar.thickness;
                    newAnnos.add(a);
                }
                return true;
            }
            if (dragging) {
                dragging = false;
                return true;
            }
        }
        if (viewing != null && dragging && button == 0) {
            dragging = false;
            if (!moved && !drawMode) {
                if (!hoveredStack.isEmpty()) {
                    toggleMark(hoveredStack);
                } else if (hoveredWorld != null && !hoveredWorld.stack().isEmpty()) {
                    toggleMark(hoveredWorld.stack());
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void applyAreaSelection() {
        double ax = Math.min(selAX, selBX);
        double ay = Math.min(selAY, selBY);
        double bx = Math.max(selAX, selBX);
        double by = Math.max(selAY, selBY);
        if (bx - ax < 3 && by - ay < 3) {
            return;
        }
        int n = 0;
        for (GrabOp o : viewing.ops) {
            if (!zoneOp(o)) {
                continue;
            }
            ItemStack s = o.stack();
            if (s.isEmpty()) {
                continue;
            }
            int[] r = worldRect(o);
            if (r[2] >= ax && r[0] <= bx && r[3] >= ay && r[1] <= by) {
                if (selAreaRemove ? marked.remove(itemKey(s)) : marked.add(itemKey(s))) {
                    n++;
                }
            }
        }
        if (n > 0) {
            sound(1.2F);
        }
    }

    private boolean renameHasSel() {
        return renameSel != -1 && renameSel != renameCaret;
    }

    private void renameDelSel() {
        if (!renameHasSel()) {
            renameSel = -1;
            return;
        }
        int a = Math.min(renameSel, renameCaret);
        int b = Math.max(renameSel, renameCaret);
        renameText = renameText.substring(0, a) + renameText.substring(b);
        renameCaret = a;
        renameSel = -1;
    }

    private String renameSelected() {
        if (!renameHasSel()) {
            return "";
        }
        return renameText.substring(Math.min(renameSel, renameCaret), Math.max(renameSel, renameCaret));
    }

    private void renameMove(int pos, boolean shift) {
        if (shift) {
            if (renameSel == -1) {
                renameSel = renameCaret;
            }
        } else {
            renameSel = -1;
        }
        renameCaret = Mth.clamp(pos, 0, renameText.length());
    }

    private void renameInsert(String ins) {
        renameDelSel();
        ins = ins.replaceAll("[\\r\\n]", "");
        int room = 64 - renameText.length();
        if (room <= 0) {
            return;
        }
        if (ins.length() > room) {
            ins = ins.substring(0, room);
        }
        renameText = renameText.substring(0, renameCaret) + ins + renameText.substring(renameCaret);
        renameCaret += ins.length();
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (renaming != null) {
            if (c >= 32 && c != 127) {
                renameInsert(String.valueOf(c));
            }
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (renaming != null) {
            boolean ctrl = (mods & 0x2) != 0;
            boolean shift = (mods & 0x1) != 0;
            switch (key) {
                case 257, 335 -> commitRename();
                case 256 -> {
                    renaming = null;
                    sound(0.8F);
                }
                case 259 -> {
                    if (renameHasSel()) {
                        renameDelSel();
                    } else if (renameCaret > 0) {
                        renameText = renameText.substring(0, renameCaret - 1) + renameText.substring(renameCaret);
                        renameCaret--;
                    }
                }
                case 261 -> {
                    if (renameHasSel()) {
                        renameDelSel();
                    } else if (renameCaret < renameText.length()) {
                        renameText = renameText.substring(0, renameCaret) + renameText.substring(renameCaret + 1);
                    }
                }
                case 263 -> renameMove(renameCaret - 1, shift);
                case 262 -> renameMove(renameCaret + 1, shift);
                case 268 -> renameMove(0, shift);
                case 269 -> renameMove(renameText.length(), shift);
                case 65 -> {
                    if (ctrl) {
                        renameSel = 0;
                        renameCaret = renameText.length();
                    }
                }
                case 67 -> {
                    if (ctrl && renameHasSel()) {
                        this.minecraft.keyboardHandler.setClipboard(renameSelected());
                    }
                }
                case 88 -> {
                    if (ctrl && renameHasSel()) {
                        this.minecraft.keyboardHandler.setClipboard(renameSelected());
                        renameDelSel();
                    }
                }
                case 86 -> {
                    if (ctrl) {
                        renameInsert(this.minecraft.keyboardHandler.getClipboard());
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (ctxGrab != null && key == 256) {
            ctxGrab = null;
            sound(0.8F);
            return true;
        }
        if (key == 256) {
            if (viewing != null && drawMode) {
                drawAction(AnnoBar.ACT_CANCEL);
                return true;
            }
            if (viewing != null) {
                exitViewer();
                return true;
            }
            if (!selectedIds.isEmpty()) {
                selectedIds.clear();
                sound(0.9F);
                return true;
            }
            onClose();
            return true;
        }
        if (viewing != null && drawMode) {
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_DONE, key) || key == 257 || key == 335) {
                drawAction(AnnoBar.ACT_DONE);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_UNDO, key)) {
                drawAction(AnnoBar.ACT_UNDO);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_CURSOR, key)) {
                drawBar.tool = AnnoBar.ACT_CURSOR;
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_PEN, key)) {
                drawBar.tool = AnnoBar.ACT_PEN;
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_BOX, key)) {
                drawBar.tool = AnnoBar.ACT_BOX;
                return true;
            }
        }
        if (viewing != null) {
            if (ModBinds.matchesNow(ModBinds.Bind.VIEW_GIVE, key)) {
                sideButton(1);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.VIEW_RECORD, key)) {
                sideButton(0);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.VIEW_COPY, key)) {
                sideButton(2);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.VIEW_DRAW, key)) {
                sideButton(4);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.VIEW_RESET, key)) {
                sideButton(5);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.VIEW_DELETE, key)) {
                sideButton(3);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.MARK_ALL, key)) {
                for (ItemStack s : items) {
                    marked.add(itemKey(s));
                }
                sound(1.2F);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.MARK_CLEAR, key)) {
                marked.clear();
                sound(0.8F);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}