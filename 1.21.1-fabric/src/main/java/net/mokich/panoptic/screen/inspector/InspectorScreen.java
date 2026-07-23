package net.mokich.panoptic.screen.inspector;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.RenderShape;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.TextOps;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.api.util.SoundFilePreview;
import net.mokich.panoptic.api.util.PngClipboard;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;

import net.mokich.panoptic.inspect.InspectEntry;
import net.mokich.panoptic.inspect.InspectField;
import net.mokich.panoptic.inspect.InspectPrefs;
import net.mokich.panoptic.inspect.InspectStore;
import net.mokich.panoptic.inspect.InspectType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.inspect.Inspectors;
import net.mokich.panoptic.inspect.LanguageNames;
import net.mokich.panoptic.inspect.ResourceFiles;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.mokich.panoptic.network.AllStructuresPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class InspectorScreen extends Screen implements TextTyping {
    @Override
    public boolean gmtTyping() {
        return filesOpen || modOpen;
    }
    private static final int ROW_H = 14;
    private static final int LIST_ROW_H = 18;
    private static final int MARGIN = 20;
    private static final int HEADER_H = 56;

    private static final int OUTER = 0xFF0D0B06;
    private static int PANEL_BG = GuiStyle.T(0xF2241E15);
    private static int PANE_BG = GuiStyle.T(0xFF17130C);
    private static int TITLEBAR = GuiStyle.T(0xFF2E2718);
    private static int BORDER = GuiStyle.T(0xFF63532F);
    private static int ROW_HOVER = GuiStyle.T(0x1AE8C06C);
    private static int SELECTED = GuiStyle.T(0x33E8C06C);
    private static int LABEL = GuiStyle.T(0xACA188);
    private static int VALUE = GuiStyle.T(0xF2EDE1);
    private static int MUTED = GuiStyle.T(0x766C52);

    static {GuiStyle.onTheme(() -> {
            PANEL_BG = GuiStyle.T(0xF2241E15);
            PANE_BG = GuiStyle.T(0xFF17130C);
            TITLEBAR = GuiStyle.T(0xFF2E2718);
            BORDER = GuiStyle.T(0xFF63532F);
            ROW_HOVER = GuiStyle.T(0x1AE8C06C);
            SELECTED = GuiStyle.T(0x33E8C06C);
            LABEL = GuiStyle.T(0xACA188);
            VALUE = GuiStyle.T(0xF2EDE1);
            MUTED = GuiStyle.T(0x766C52);
        });}

    private EditBox search;
    private String topTip;
    private int searchBoxX1;
    private int searchBoxX2;
    private ScanJob scanJob;
    private String notice;
    private long noticeUntil;
    private String filter = "";

    private final Set<InspectEntry> marked = new HashSet<>();
    private final Set<String> expandedProps = new HashSet<>();
    private boolean dragMarking;
    private boolean dragMarkAdd;
    private int dragMarkLast = -1;
    private int dragMarkAnchor = -1;
    private boolean fileDragging;
    private boolean fileDragAdd;
    private int fileDragLast = -1;
    private int lastMarkIndex = -1;
    private int[] typeCounts;
    private int totalCount;

    private int autoScroll;
    private int autoAnchorX, autoAnchorY, autoPressY;
    private long autoPressTime;

    private boolean modOpen;
    private static final Set<String> modFilter = new HashSet<>();
    private static boolean modFilterLoaded;
    private List<ModRow> modRows;
    private double modScroll;
    private int searchY;
    private int ddLeft, ddTop, ddRight, ddBottom;
    private static final int DD_HEADER_H = 16;
    private static final int DD_ROW_H = 16;
    private InspectType typeFilter;
    private InspectEntry selected;

    private double leftScroll, rightScroll, leftTarget, rightTarget;
    private long lastNanos;
    private long appearStart;
    private int dragging;
    private int dragOffset;
    private boolean restoredSelection;
    private boolean listDirty = true;
    private boolean detailDirty = true;
    private boolean langPending;
    private final List<InspectEntry> list = new ArrayList<>();
    private final List<Row> detail = new ArrayList<>();

    private int panelLeft, panelRight, panelTop, panelBottom, chipsY, contentTop, contentBottom;
    private int leftLeft, leftRight, rightLeft, rightRight, detailTop;
    private int delLeft, delTop, delRight, delBottom;
    private boolean deleteHover;

    private String copied;
    private long copiedUntil;

    private boolean filesOpen;
    private double filesScroll, filesTarget;
    private final List<FRow> fileRows = new ArrayList<>();
    private int fLeft, fTop, fRight, fBottom, fCTop, fCBottom, filesContentH;
    private String filesFilter = "";
    private String filesKind;
    private final Set<String> filesCollapsed = new HashSet<>();
    private final List<KindChip> kindChips = new ArrayList<>();
    private final List<KindChip> nsChips = new ArrayList<>();
    private String filesNs;
    private int filesCaret;
    private int filesSel = -1;
    private boolean filesSearchFocused;
    private boolean fHelpHover;
    private boolean mainHelpHover;
    private final Map<String, int[]> kindCountMap = new LinkedHashMap<>();
    private final Map<String, int[]> nsCountMap = new LinkedHashMap<>();
    private final Map<String, int[]> extCountMap = new LinkedHashMap<>();
    private Set<String> qKinds, qNss, qExts;
    private final List<String> qPlains = new ArrayList<>();
    private final List<String> qIns = new ArrayList<>();
    private final List<Suggestion> suggestions = new ArrayList<>();
    private int suggestSel = -1;
    private int suggestTokenStart;
    private boolean suggestHidden;
    private int sugX, sugY, sugW, sugRowH;
    private String lastSugToken = "\u0000";

    private record Suggestion(String insert, String label, String hint, int color) {
    }
    private final Map<String, int[]> texDims = new HashMap<>();
    private final Map<String, List<String>> previewCache = new HashMap<>();
    private final Map<String, StructPreview> structCache = new HashMap<>();
    private static final ExecutorService STRUCT_LOADER =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "gmt-struct-loader");
                t.setDaemon(true);
                return t;
            });
    private FRow previewRow;
    private double previewScroll, previewTarget;
    private long previewAppearStart;
    private static final int PREVIEW_LINES = 22;
    private final Map<String, PreviewParsers.Parsed> parsedCache = new HashMap<>();
    private final Map<String, int[]> measCache = new HashMap<>();
    private final LinkedHashSet<String> filesSelected = new LinkedHashSet<>();
    private final Map<String, String> contentCache = new HashMap<>();
    private int lastFileSel = -1;
    private int filesMatchCount;
    private int panelX, panelY;
    private int fSearchLeft, fSearchRight, fChipsY, fListTop;

    private double uiK = 1.0;
    private int vw;
    private int vh;
    private double chipScroll;
    private double chipScrollTarget;
    private double modScrollTarget;
    private long lastFilesListScroll;
    private String modSearch = "";
    private int modSearchCaret;
    private int modSearchSel = -1;
    private boolean searchHidden;

    private void scis(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor((int) Math.floor(x1 / uiK), (int) Math.floor(y1 / uiK),
                (int) Math.ceil(x2 / uiK), (int) Math.ceil(y2 / uiK));
    }
    @Override
    public void onClose() {
        SoundFilePreview.closeBar();
        super.onClose();
    }

    public InspectorScreen() {
        super(Component.literal("Panoptic"));
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

    @Override
    protected void init() {
        LanguageNames.preloadAsync();
        vw = (int) Math.round(width * uiK);
        vh = (int) Math.round(height * uiK);
        int pw = Math.min(vw - 2 * MARGIN, 860);
        panelLeft = (vw - pw) / 2;
        panelRight = panelLeft + pw;
        panelTop = MARGIN;
        panelBottom = vh - MARGIN;

        searchY = panelTop + 20;
        chipsY = panelTop + 40;
        contentTop = chipsY + 18;
        contentBottom = panelBottom - 8;

        int leftWidth = Mth.clamp((int) ((panelRight - panelLeft) * 0.34), 150, 260);
        leftLeft = panelLeft + 6;
        leftRight = leftLeft + leftWidth;
        rightLeft = leftRight + 10;
        rightRight = panelRight - 6;
        detailTop = contentTop + HEADER_H + 4;

        if (typeFilter == null) {
            typeFilter = InspectPrefs.lastTab();
        }

        if (!modFilterLoaded) {
            modFilterLoaded = true;
            modFilter.addAll(InspectPrefs.modFilter());
        }

        searchHidden = panelRight - panelLeft - 420 < 60;
        search = new EditBox(this.font, panelLeft + 8, searchY, Math.max(40, panelRight - panelLeft - 404), 16, Component.translatable("panoptic.gui.search"));
        search.setHint(Component.translatable("panoptic.gui.search_hint"));
        search.setValue(filter);
        search.setResponder(s -> {
            filter = s.toLowerCase(Locale.ROOT);
            leftTarget = 0;
            listDirty = true;
        });
        addRenderableWidget(search);

        search.setBordered(false);
        search.setX(panelLeft + 24);
        search.setY(searchY + 3);
        search.setHeight(9);
        search.setWidth(Math.max(40, panelRight - panelLeft - 420));
        search.setVisible(!searchHidden);

        int delSize = 14;
        delRight = rightRight - 6;
        delLeft = delRight - delSize;
        delTop = contentTop + 4;
        delBottom = delTop + delSize;

        if (appearStart == 0) {
            appearStart = System.nanoTime();
        }
        listDirty = true;
        detailDirty = true;
    }

    private void drawTopButtons(GuiGraphics g, int mouseX, int mouseY) {
        topTip = null;
        if (searchHidden) {
            searchBoxX1 = -1;
            searchBoxX2 = -1;
        } else {
            int sx1 = panelLeft + 6;
            int sx2 = search.getX() + search.getWidth() + 4;
            searchBoxX1 = sx1;
            searchBoxX2 = sx2;
            g.fill(sx1, searchY - 2, sx2, searchY + 15, GuiStyle.SEARCH_BG);
            GuiStyle.rect(g, sx1, searchY - 2, sx2, searchY + 15,
                    search.isFocused() ? GuiStyle.ACCENT : GuiStyle.BORDER);
            g.fill(sx1 + 1, searchY - 1, sx2 - 1, searchY, 0x30000000);
            Icons.searchIcon(g, sx1 + 6, searchY + 3, search.isFocused() ? GuiStyle.ACCENT : GuiStyle.MUTED);
        }

        String modsLabel = modFilter.isEmpty()
                ? I18n.get("panoptic.gui.mods")
                : I18n.get("panoptic.gui.mods_n", modFilter.size());
        topButton(g, 2, panelRight - 316, searchY - 2, 90, 17, I18n.get("panoptic.gui.scan_all"),
                scanJob == null, "panoptic.gui.scan_warn", mouseX, mouseY);
        topButton(g, 1, panelRight - 220, searchY - 2, 70, 17, modsLabel, true, "panoptic.gui.mods_filter", mouseX, mouseY);
        topButton(g, 3, panelRight - 144, searchY - 2, 66, 17, I18n.get("panoptic.gui.clear"), true, "panoptic.gui.clear_tip", mouseX, mouseY);
        topButton(g, 4, panelRight - 74, searchY - 2, 66, 17, I18n.get("panoptic.gui.close"), true, null, mouseX, mouseY);
    }

    private void topButton(GuiGraphics g, int id, int x, int y, int w, int h, String label,
                           boolean enabled, String tipKey, int mouseX, int mouseY) {
        boolean hov = !filesOpen && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        GuiStyle.button(g, this.font, x, y, x + w, y + h, label, hov, enabled);
        if (hov && tipKey != null) {
            topTip = I18n.get(tipKey);
        }
    }

    private int topButtonAt(double mx, double my) {
        if (my >= searchY - 2 && my < searchY + 15) {
            if (mx >= panelRight - 316 && mx < panelRight - 226) return 2;
            if (mx >= panelRight - 220 && mx < panelRight - 150) return 1;
            if (mx >= panelRight - 144 && mx < panelRight - 78) return 3;
            if (mx >= panelRight - 74 && mx < panelRight - 8) return 4;
        }
        if (selected != null && my >= contentTop + 3 && my < contentTop + 18
                && mx >= delLeft - 92 && mx < delLeft - 6) {
            return 5;
        }
        return 0;
    }

    private void handleTopButton(int id) {
        if (id != 1 && id != 3) {
            sound(1.0F);
        }
        switch (id) {
            case 1 -> {
                modOpen = !modOpen;
                if (modOpen) {
                    rebuildModRows();
                }
                sound(1.0F);
            }
            case 2 -> {
                if (scanJob == null) {
                    openScanConfirm();
                }
            }
            case 3 -> {
                InspectStore.clear();
                selected = null;
                modFilter.clear();
                InspectPrefs.setModFilter(modFilter);
                modSearch = "";
                modSearchCaret = 0;
                modSearchSel = -1;
                listDirty = true;
                detailDirty = true;
                sound(0.7F);
            }
            case 4 -> onClose();
            case 5 -> {
                if (selected != null) {
                    copyAll();
                }
            }
            default -> {
            }
        }
    }

    private float appearProgress() {
        if (appearStart == 0) {
            return 1.0F;
        }
        float t = Mth.clamp((System.nanoTime() - appearStart) / 1.6E8F, 0.0F, 1.0F);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private int chipsTotalWidth() {
        int w = 0;
        String all = I18n.get("panoptic.gui.all") + " " + totalCount;
        w += this.font.width(all) + 16;
        for (InspectType t : InspectType.values()) {
            int c = typeCounts == null ? 0 : typeCounts[t.ordinal()];
            w += this.font.width(I18n.get(t.labelKey()) + " " + c) + 16;
        }
        return w;
    }

    private int chipMaxScroll() {
        return Math.max(0, chipsTotalWidth() - (panelRight - 6 - leftLeft));
    }

    private List<Chip> chips() {
        List<Chip> chips = new ArrayList<>();
        chipScrollTarget = Mth.clamp(chipScrollTarget, 0, chipMaxScroll());
        chipScroll = Mth.clamp(chipScroll, 0, chipMaxScroll());
        int x = leftLeft - (int) Math.round(chipScroll);
        x = addChip(chips, x, null, I18n.get("panoptic.gui.all"), totalCount);
        for (InspectType t : InspectType.values()) {
            int c = typeCounts == null ? 0 : typeCounts[t.ordinal()];
            x = addChip(chips, x, t, I18n.get(t.labelKey()), c);
        }
        return chips;
    }

    private int addChip(List<Chip> chips, int x, InspectType type, String label, int count) {
        String text = label + " " + count;
        int w = this.font.width(text) + 12;
        chips.add(new Chip(type, text, x, w));
        return x + w + 4;
    }

    private void rebuildList() {
        list.clear();
        typeCounts = new int[InspectType.values().length];
        totalCount = 0;
        for (InspectEntry e : InspectStore.entries()) {
            if (passesModText(e)) {
                typeCounts[e.typeEnum().ordinal()]++;
                totalCount++;
            }
            if (matches(e)) {
                list.add(e);
            }
        }
        if ((selected == null || !list.contains(selected)) && !list.isEmpty()) {
            InspectEntry pick = null;
            if (!restoredSelection) {
                restoredSelection = true;
                long id = InspectPrefs.lastSelected();
                if (id >= 0) {
                    for (InspectEntry e : list) {
                        if (e.capturedAt == id) {
                            pick = e;
                            break;
                        }
                    }
                }
            }
            selected = pick != null ? pick : list.get(0);
            detailDirty = true;
        }
        if (list.isEmpty()) {
            selected = null;
        }
        listDirty = false;
    }

    private void rebuildDetail() {
        detail.clear();
        if (selected != null) {
            for (InspectField f : selected.fields) {
                detail.add(Row.field(f));
                if (expandedProps.contains(f.label) && f.value != null) {
                    int wrapW = Math.max(60, rightRight - rightLeft - 24);
                    String rest = f.value;
                    int n = 0;
                    while (!rest.isEmpty() && n < 60) {
                        String chunk = this.font.plainSubstrByWidth(rest, wrapW);
                        if (chunk.isEmpty()) {
                            break;
                        }
                        detail.add(Row.wrap(chunk));
                        rest = rest.substring(chunk.length());
                        n++;
                    }
                }
            }
            langPending = !LanguageNames.ready();
            Map<String, String> names = LanguageNames.allNames(selected.translationKey);
            if (!names.isEmpty()) {
                detail.add(Row.langToggle(names.size()));
                if (selected.langsExpanded) {
                    for (Map.Entry<String, String> n : names.entrySet()) {
                        detail.add(Row.lang(n.getKey(), n.getValue()));
                    }
                }
            }
            if (selected.files == null) {
                selected.files = ResourceFiles.filesFor(selected.id);
            }
            if (!selected.files.isEmpty()) {
                detail.add(Row.filesToggle(selected.files.size()));
            }
        }
        detailDirty = false;
    }

    private void openFiles() {
        filesFilter = "";
        filesCaret = 0;
        filesSearchFocused = false;
        suggestions.clear();
        suggestSel = -1;
        suggestHidden = false;
        lastSugToken = " ";
        filesKind = null;
        filesNs = null;
        filesCollapsed.clear();
        filesSelected.clear();
        lastFileSel = -1;
        texDims.clear();
        previewCache.clear();
        structCache.clear();
        parsedCache.clear();
        measCache.clear();
        contentCache.clear();
        buildKindChips();
        rebuildFileRows();
        filesScroll = 0;
        filesTarget = 0;
        filesOpen = true;
        sound(1.3F);
    }

    private boolean matches(InspectEntry e) {
        return (typeFilter == null || e.typeEnum() == typeFilter) && passesModText(e);
    }

    private boolean passesModText(InspectEntry e) {
        if (!modFilter.isEmpty() && !modFilter.contains(namespaceOf(e))) {
            return false;
        }
        if (filter.isEmpty()) {
            return true;
        }
        if (e.title != null && e.title.toLowerCase(Locale.ROOT).contains(filter)) return true;
        if (e.id != null && e.id.toLowerCase(Locale.ROOT).contains(filter)) return true;
        return I18n.get(e.typeEnum().labelKey()).toLowerCase(Locale.ROOT).contains(filter);
    }

    private void animate() {
        long now = System.nanoTime();
        if (lastNanos != 0) {
            float dt = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
            float f = 1.0F - (float) Math.exp(-dt * 16.0F);
            leftScroll += (leftTarget - leftScroll) * f;
            rightScroll += (rightTarget - rightScroll) * f;
            filesScroll += (filesTarget - filesScroll) * f;
            previewScroll += (previewTarget - previewScroll) * f;
            chipScroll += (chipScrollTarget - chipScroll) * f;
            modScroll += (modScrollTarget - modScroll) * f;
            if (Math.abs(leftTarget - leftScroll) < 0.5) leftScroll = leftTarget;
            if (Math.abs(rightTarget - rightScroll) < 0.5) rightScroll = rightTarget;
            if (Math.abs(filesTarget - filesScroll) < 0.5) filesScroll = filesTarget;
            if (Math.abs(previewTarget - previewScroll) < 0.5) previewScroll = previewTarget;
            if (Math.abs(chipScrollTarget - chipScroll) < 0.5) chipScroll = chipScrollTarget;
            if (Math.abs(modScrollTarget - modScroll) < 0.5) modScroll = modScrollTarget;
        }
        lastNanos = now;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (uiK == 1.0) {
            renderScaled(g, mouseX, mouseY, partialTick);
            return;
        }
        renderTransparentBackground(g);
        g.pose().pushPose();
        g.pose().scale((float) (1.0 / uiK), (float) (1.0 / uiK), 1.0F);
        try {
            renderScaled(g, (int) Math.round(mouseX * uiK), (int) Math.round(mouseY * uiK), partialTick);
        } finally {
            g.pose().popPose();
        }
    }

    private void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (langPending && LanguageNames.ready()) {
            langPending = false;
            detailDirty = true;
        }
        if (listDirty) rebuildList();
        if (detailDirty) rebuildDetail();
        if (autoScroll != 0) {
            int delta = mouseY - autoAnchorY;
            int dead = 8;
            if (Math.abs(delta) > dead) {
                double sp = (delta - (delta > 0 ? dead : -dead)) * 0.4;
                if (autoScroll == 1) {
                    int maxL = Math.max(0, list.size() * LIST_ROW_H - (contentBottom - contentTop));
                    leftTarget = Mth.clamp(leftTarget + sp, 0, maxL);
                } else {
                    int maxR = Math.max(0, detail.size() * ROW_H - (contentBottom - detailTop));
                    rightTarget = Mth.clamp(rightTarget + sp, 0, maxR);
                }
            }
        }
        animate();
        if (scanJob != null) {
            scanJob.step(4_000_000L);
            if (scanJob.done()) {
                int added = InspectStore.addAll(scanJob.results());
                scanJob = null;
                selected = null;
                leftTarget = 0;
                leftScroll = 0;
                listDirty = true;
                detailDirty = true;
                notice = I18n.get("panoptic.gui.scanned", added);
                noticeUntil = System.currentTimeMillis() + 2500;
                sound(1.4F);
            }
        }
        if (uiK == 1.0) {
            renderTransparentBackground(g);
        }

        float appear = appearProgress();
        g.pose().pushPose();
        float scale = 0.92F + 0.08F * appear;
        float cx = (panelLeft + panelRight) / 2.0F;
        float cy = (panelTop + panelBottom) / 2.0F;
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-cx, -cy, 0);

        GuiStyle.panel(g, panelLeft, panelTop, panelRight, panelBottom);
        g.fillGradient(panelLeft + 1, panelTop + 1, panelRight - 1, panelTop + 16,
                GuiStyle.T(0xFF3A2F1B), GuiStyle.T(0xFF241D11));
        g.fill(panelLeft + 1, panelTop + 16, panelRight - 1, panelTop + 17, GuiStyle.BORDER_B);
        g.fill(panelLeft + 1, panelTop + 1, panelRight - 1, panelTop + 2, GuiStyle.T(0x30FFE7B0));
        g.drawString(this.font, I18n.get("panoptic.gui.title"), panelLeft + 8, panelTop + 4, GuiStyle.ACCENT);
        mainHelpHover = !filesOpen && HelpCard.icon(g, this.font,
                panelLeft + 8 + this.font.width(I18n.get("panoptic.gui.title")) + 6, panelTop + 1, mouseX, mouseY);
        String count = I18n.get("panoptic.gui.count", InspectStore.entries().size());
        g.drawString(this.font, count, panelRight - this.font.width(count) - 8, panelTop + 4, MUTED);
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            g.drawCenteredString(this.font, notice, (panelLeft + panelRight) / 2, panelTop + 4, GuiStyle.ACCENT);
        }

        int hmx = mouseX;
        int hmy = mouseY;
        if (inDropdown(mouseX, mouseY)) {
            hmx = Integer.MIN_VALUE;
            hmy = Integer.MIN_VALUE;
        }
        drawTopButtons(g, hmx, hmy);
        renderChips(g, hmx, hmy);
        renderLeft(g, hmx, hmy);
        renderRight(g, hmx, hmy);

        super.render(g, mouseX, mouseY, partialTick);

        if (scanJob != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(panelLeft, panelTop + 16, panelRight, panelBottom, 0xD0000000);
            int bw = Math.min(320, panelRight - panelLeft - 80);
            int bx = (panelLeft + panelRight) / 2 - bw / 2;
            int by = (panelTop + panelBottom) / 2;
            g.drawCenteredString(this.font, I18n.get("panoptic.gui.scanning", scanJob.processed(), scanJob.total()),
                    (panelLeft + panelRight) / 2, by - 16, 0xFFFFFF);
            g.fill(bx, by, bx + bw, by + 10, 0xFF17130B);
            g.fill(bx, by, bx + (int) (bw * scanJob.progress()), by + 10, GuiStyle.ACCENT);
            rect(g, bx, by, bx + bw, by + 10, BORDER);
            g.drawCenteredString(this.font, (int) (scanJob.progress() * 100) + "%",
                    (panelLeft + panelRight) / 2, by + 14, 0xACA188);
            g.pose().popPose();
        }

        if (modOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            renderModDropdown(g, mouseX, mouseY);
            g.pose().popPose();
        }

        if (autoScroll != 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            g.fill(autoAnchorX - 7, autoAnchorY - 7, autoAnchorX + 7, autoAnchorY + 7, 0xC01A150D);
            rect(g, autoAnchorX - 7, autoAnchorY - 7, autoAnchorX + 7, autoAnchorY + 7, GuiStyle.ACCENT);
            Icons.iconScroll(g, autoAnchorX - 2, autoAnchorY - 4, 0xFFFFFFFF);
            g.pose().popPose();
        }

        g.pose().popPose();

        if (filesOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            renderFilesOverlay(g, mouseX, mouseY);
            renderSoundBar(g, mouseX, mouseY);
            g.pose().popPose();
        }

        if (topTip != null && !filesOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            g.renderTooltip(this.font, Component.literal(topTip), mouseX, mouseY);
            g.pose().popPose();
        }

        if (copied != null && System.currentTimeMillis() < copiedUntil) {
            g.renderTooltip(this.font, Component.translatable("panoptic.gui.copied", copied), mouseX, mouseY);
        } else if (deleteHover) {
            g.renderTooltip(this.font, Component.translatable("panoptic.gui.delete"), mouseX, mouseY);
        }
        drawMainHelp(g);
    }

    private void drawMainHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.gui.mhelp_b1"));
        bullets.add(Component.translatable("panoptic.gui.mhelp_b2"));
        bullets.add(Component.translatable("panoptic.gui.mhelp_b3"));
        bullets.add(Component.translatable("panoptic.gui.mhelp_b4"));
        List<HelpCard.KeyHint> keys = List.of(
                ModBinds.hint(ModBinds.Bind.CAPTURE, "panoptic.gui.mhelp_capture"),
                ModBinds.hint(ModBinds.Bind.SEARCH, "panoptic.gui.mhelp_search"),
                ModBinds.hint(ModBinds.Bind.MARK_ALL, "panoptic.gui.mhelp_mark"),
                ModBinds.hint(ModBinds.Bind.MARK_CLEAR, "panoptic.gui.mhelp_clear"));
        HelpCard.render(g, this.font, vw, vh, mainHelpHover,
                Component.translatable("panoptic.gui.mhelp_title"),
                Component.translatable("panoptic.gui.mhelp_sum"), bullets, keys);
    }

    private void renderModDropdown(GuiGraphics g, int mouseX, int mouseY) {
        int rowsCount = filteredModRows().size();
        ddRight = panelRight - 8;
        ddLeft = ddRight - 210;
        ddTop = searchY + 18;
        int maxVisible = Math.max(1, (contentBottom - ddTop - DD_HEADER_H - 14) / DD_ROW_H);
        int visibleRows = Math.min(rowsCount, maxVisible);
        ddBottom = ddTop + DD_HEADER_H + 14 + Math.max(1, visibleRows) * DD_ROW_H;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(ddLeft - 1, ddTop - 1, ddRight + 1, ddBottom + 1, 0xFF0D0B06);
        rect(g, ddLeft - 1, ddTop - 1, ddRight + 1, ddBottom + 1, BORDER);
        g.fill(ddLeft, ddTop, ddRight, ddTop + DD_HEADER_H, TITLEBAR);
        g.drawString(this.font, I18n.get("panoptic.gui.mods"), ddLeft + 5, ddTop + 4, 0xFFFFFF);

        String reset = I18n.get("panoptic.gui.reset");
        int resetX = ddRight - this.font.width(reset) - 5;
        boolean resetHover = mouseX >= resetX - 1 && mouseX <= ddRight - 2 && mouseY >= ddTop && mouseY <= ddTop + DD_HEADER_H;
        g.drawString(this.font, reset, resetX, ddTop + 4,
                modFilter.isEmpty() ? MUTED : (resetHover ? 0xFF6B6B : 0xC8A0A0));

        int searchTop = ddTop + DD_HEADER_H;
        g.fill(ddLeft, searchTop, ddRight, searchTop + 14, GuiStyle.SEARCH_BG);
        rect(g, ddLeft, searchTop, ddRight, searchTop + 14, BORDER);
        Icons.searchIcon(g, ddLeft + 4, searchTop + 4, GuiStyle.ACCENT);
        scis(g, ddLeft + 14, searchTop, ddRight - 2, searchTop + 14);
        TextOps.drawSel(g, this.font, modSearch, modSearchCaret, modSearchSel, ddLeft + 15, searchTop + 2, searchTop + 12);
        g.drawString(this.font, modSearch, ddLeft + 15, searchTop + 3, GuiStyle.TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cxp = ddLeft + 15 + this.font.width(modSearch.substring(0, Mth.clamp(modSearchCaret, 0, modSearch.length())));
            g.fill(cxp, searchTop + 2, cxp + 1, searchTop + 12, GuiStyle.ACCENT);
        }
        g.disableScissor();

        List<ModRow> shownRows = filteredModRows();
        rowsCount = shownRows.size();
        int bodyTop = searchTop + 14;
        int content = rowsCount * DD_ROW_H;
        int maxScroll = Math.max(0, content - (ddBottom - bodyTop));
        modScrollTarget = Mth.clamp(modScrollTarget, 0, maxScroll);
        modScroll = Mth.clamp(modScroll, 0, maxScroll);
        int scroll = (int) Math.round(modScroll);

        scis(g, ddLeft, bodyTop, ddRight, ddBottom);
        for (int i = 0; i < rowsCount; i++) {
            int y = bodyTop - scroll + i * DD_ROW_H;
            if (y + DD_ROW_H < bodyTop || y > ddBottom) {
                continue;
            }
            ModRow r = shownRows.get(i);
            boolean sel = modFilter.contains(r.namespace);
            boolean hover = mouseX >= ddLeft && mouseX <= ddRight && mouseY >= y && mouseY < y + DD_ROW_H;
            if (sel) {
                g.fill(ddLeft, y, ddRight, y + DD_ROW_H, 0x33E8C06C);
            } else if (hover) {
                g.fill(ddLeft, y, ddRight, y + DD_ROW_H, ROW_HOVER);
            }
            int bx = ddLeft + 4;
            int by = y + 3;
            g.fill(bx, by, bx + 10, by + 10, sel ? GuiStyle.ACCENT : 0xFF17130B);
            rect(g, bx, by, bx + 10, by + 10, BORDER);
            if (sel) {
                Icons.iconCheck(g, bx + 2, by + 2, 0xFF17130C);
            }
            String cnt = String.valueOf(r.count);
            int cntX = ddRight - this.font.width(cnt) - 5;
            g.drawString(this.font, trim(r.display, cntX - 4 - (bx + 14)), bx + 14, y + 4, sel ? 0xFFFFFF : 0xD8D0BE);
            g.drawString(this.font, cnt, cntX, y + 4, MUTED);
        }
        g.disableScissor();
        scrollbar(g, ddRight - 1, bodyTop, ddBottom, content, scroll);
        g.pose().popPose();
    }

    private List<ModRow> filteredModRows() {
        if (modRows == null) {
            return List.of();
        }
        String q = modSearch.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return modRows;
        }
        List<ModRow> out = new ArrayList<>();
        for (ModRow r : modRows) {
            if (r.display.toLowerCase(Locale.ROOT).contains(q) || r.namespace.contains(q)) {
                out.add(r);
            }
        }
        return out;
    }

    private void rebuildModRows() {
        Map<String, Integer> counts = new HashMap<>();
        for (InspectEntry e : InspectStore.entries()) {
            counts.merge(namespaceOf(e), 1, Integer::sum);
        }
        List<ModRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> en : counts.entrySet()) {
            rows.add(new ModRow(en.getKey(), modName(en.getKey()), en.getValue()));
        }
        rows.sort(Comparator.comparing(r -> r.display.toLowerCase(Locale.ROOT)));
        modRows = rows;
        modScroll = 0;
    }

    private static String namespaceOf(InspectEntry e) {
        String id = e.id;
        if (id == null) {
            return "minecraft";
        }
        int c = id.indexOf(':');
        return c < 0 ? "minecraft" : id.substring(0, c);
    }

    private static String modName(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(c -> c.getMetadata().getName())
                .orElse(namespace);
    }

    private boolean inDropdown(double mouseX, double mouseY) {
        return modOpen && mouseX >= ddLeft - 1 && mouseX <= ddRight + 1
                && mouseY >= ddTop - 1 && mouseY <= ddBottom + 1;
    }

    private void handleModDropdownClick(double mouseX, double mouseY) {
        String reset = I18n.get("panoptic.gui.reset");
        int resetX = ddRight - this.font.width(reset) - 5;
        if (!modFilter.isEmpty() && mouseX >= resetX - 1 && mouseX <= ddRight - 2
                && mouseY >= ddTop && mouseY <= ddTop + DD_HEADER_H) {
            modFilter.clear();
            InspectPrefs.setModFilter(modFilter);
            leftTarget = 0;
            listDirty = true;
            sound(0.8F);
            return;
        }
        int bodyTop = ddTop + DD_HEADER_H + 14;
        if (mouseY >= bodyTop && mouseY <= ddBottom) {
            List<ModRow> shownRows = filteredModRows();
            int index = (int) ((mouseY - bodyTop + modScroll) / DD_ROW_H);
            if (index >= 0 && index < shownRows.size()) {
                String ns = shownRows.get(index).namespace;
                if (!modFilter.add(ns)) {
                    modFilter.remove(ns);
                }
                InspectPrefs.setModFilter(modFilter);
                leftTarget = 0;
                listDirty = true;
                sound(1.0F);
            }
        }
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        scis(g, panelLeft + 2, chipsY - 1, panelRight - 2, chipsY + 15);
        for (Chip c : chips()) {
            boolean active = c.type == typeFilter;
            boolean hovered = mouseX >= c.x && mouseX <= c.x + c.w && mouseY >= chipsY && mouseY <= chipsY + 14;
            int color = c.type == null ? 0xACA188 : c.type.color();
            if (active) {
                g.fill(c.x, chipsY, c.x + c.w, chipsY + 14, GuiStyle.T(0xD0101117));
                rect(g, c.x, chipsY, c.x + c.w, chipsY + 14, 0xFF000000 | color);
                g.fill(c.x + 1, chipsY + 12, c.x + c.w - 1, chipsY + 14, 0xFF000000 | color);
                g.drawString(this.font, c.label, c.x + 6, chipsY + 3, 0xFFFFFF);
            } else {
                g.fill(c.x, chipsY, c.x + c.w, chipsY + 14, hovered ? 0x33FFFFFF : 0x22FFFFFF);
                rect(g, c.x, chipsY, c.x + c.w, chipsY + 14, BORDER);
                g.drawString(this.font, c.label, c.x + 6, chipsY + 3, hovered ? 0xFFFFFF : color);
            }
        }
        g.disableScissor();
    }

    private void renderLeft(GuiGraphics g, int mouseX, int mouseY) {
        GuiStyle.plate(g, leftLeft - 2, contentTop - 2, leftRight + 2, contentBottom + 2, GuiStyle.T(0xFF1B1710), GuiStyle.T(0xFF13100A), GuiStyle.BORDER);
        rect(g, leftLeft - 2, contentTop - 2, leftRight + 2, contentBottom + 2, BORDER);

        int max = Math.max(0, list.size() * LIST_ROW_H - (contentBottom - contentTop));
        leftTarget = Mth.clamp(leftTarget, 0, max);
        leftScroll = Mth.clamp(leftScroll, 0, max);
        int scroll = (int) Math.round(leftScroll);

        scis(g, leftLeft - 1, contentTop - 1, leftRight + 1, contentBottom + 1);
        for (int i = 0; i < list.size(); i++) {
            int y = contentTop - scroll + i * LIST_ROW_H;
            if (y + LIST_ROW_H < contentTop || y > contentBottom) continue;
            InspectEntry e = list.get(i);
            boolean hovered = mouseX >= leftLeft && mouseX <= leftRight && mouseY >= y && mouseY < y + LIST_ROW_H;
            boolean mk = marked.contains(e);
            if (e == selected) {
                g.fill(leftLeft, y, leftRight, y + LIST_ROW_H, SELECTED);
            } else if (hovered) {
                g.fill(leftLeft, y, leftRight, y + LIST_ROW_H, ROW_HOVER);
            }
            if (mk) {
                g.fill(leftLeft, y, leftRight, y + LIST_ROW_H, 0x22E8C06C);
            }
            g.fill(leftLeft, y, leftLeft + 2, y + LIST_ROW_H, 0xFF000000 | e.typeEnum().color());

            ItemStack icon = e.icon();
            if (!icon.isEmpty()) {
                g.renderItem(icon, leftLeft + 5, y + 1);
            } else {
                g.fill(leftLeft + 8, y + 5, leftLeft + 16, y + 13, 0xFF000000 | e.typeEnum().color());
            }
            g.drawString(this.font, trim(e.title, leftRight - 18 - (leftLeft + 24)), leftLeft + 24, y + 5,
                    e == selected ? 0xFFFFFF : 0xD8D0BE);

            int cbLeft = leftRight - 14;
            int cbTop = y + 4;
            g.fill(cbLeft, cbTop, cbLeft + 10, cbTop + 10, mk ? GuiStyle.ACCENT : 0xFF17130B);
            rect(g, cbLeft, cbTop, cbLeft + 10, cbTop + 10, BORDER);
            if (mk) {
                Icons.iconCheck(g, cbLeft + 2, cbTop + 2, 0xFF17130C);
            }
        }
        g.disableScissor();
        scrollbar(g, leftRight - 1, contentTop, contentBottom, list.size() * LIST_ROW_H, scroll);

        if (!marked.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            int fTop = contentBottom - 16;
            boolean fHover = mouseX >= leftLeft && mouseX <= leftRight && mouseY >= fTop && mouseY <= contentBottom;
            g.fill(leftLeft, fTop, leftRight, contentBottom, fHover ? 0xFF5A1A1A : 0xFF3A1414);
            rect(g, leftLeft, fTop, leftRight, contentBottom, 0xFFFF6B6B);
            g.drawCenteredString(this.font, I18n.get("panoptic.gui.delete_selected", marked.size()),
                    (leftLeft + leftRight) / 2, fTop + 4, 0xFFD0D0);
            g.pose().popPose();
        }
    }

    private void renderRight(GuiGraphics g, int mouseX, int mouseY) {
        deleteHover = false;
        GuiStyle.plate(g, rightLeft - 2, contentTop - 2, rightRight + 2, contentBottom + 2, GuiStyle.T(0xFF1B1710), GuiStyle.T(0xFF13100A), GuiStyle.BORDER);
        rect(g, rightLeft - 2, contentTop - 2, rightRight + 2, contentBottom + 2, BORDER);

        if (selected == null) {
            g.drawString(this.font, I18n.get(InspectStore.entries().isEmpty() ? "panoptic.gui.empty" : "panoptic.gui.select_left"),
                    rightLeft + 6, contentTop + 8, MUTED);
            return;
        }

        InspectType t = selected.typeEnum();
        g.fill(rightLeft, contentTop, rightRight, contentTop + HEADER_H, 0x18FFFFFF);

        int previewCx = rightLeft + 28;
        LivingEntity preview = livingPreview(selected);
        if (preview != null) {
            try {
                int scale = Mth.clamp((int) (36 / Math.max(1.0F, preview.getBbHeight())), 8, 34);
                int baseY = contentTop + HEADER_H - 6;
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, previewCx - 30, baseY - 52, previewCx + 30, baseY, scale, 0.0625F,
                        mouseX, mouseY, preview);
            } catch (Throwable ignored) {
                preview = null;
            }
        }
        if (preview == null) {
            ItemStack icon = selected.icon();
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(rightLeft + 12, contentTop + 14, 0);
                g.pose().scale(2.0F, 2.0F, 1.0F);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            } else {
                g.fill(rightLeft + 12, contentTop + 14, rightLeft + 44, contentTop + 46, 0xFF000000 | t.color());
            }
        }

        int hx = rightLeft + 52;
        String typeLabel = I18n.get(t.labelKey());
        int badgeW = this.font.width(typeLabel) + 6;
        g.fill(hx, contentTop + 8, hx + badgeW, contentTop + 20, 0xFF000000 | t.color());
        g.drawString(this.font, typeLabel, hx + 3, contentTop + 10, 0x101014, false);
        g.drawString(this.font, trim(selected.title, rightRight - 110 - hx), hx, contentTop + 24, 0xFFFFFF);
        g.drawString(this.font, trim(selected.id, rightRight - 14 - hx), hx, contentTop + 38, MUTED);

        deleteHover = mouseX >= delLeft && mouseX <= delRight && mouseY >= delTop && mouseY <= delBottom;
        g.fill(delLeft, delTop, delRight, delBottom, deleteHover ? 0x40FF5555 : 0x18FFFFFF);
        rect(g, delLeft, delTop, delRight, delBottom, deleteHover ? 0xFFFF6B6B : BORDER);
        Icons.iconCross(g, (delLeft + delRight) / 2 - 3, (delTop + delBottom) / 2 - 3,
                deleteHover ? 0xFFFFFFFF : 0xFFC88A8A);
        boolean copyAllHov = !filesOpen && mouseX >= delLeft - 92 && mouseX < delLeft - 6
                && mouseY >= contentTop + 3 && mouseY < contentTop + 18;
        GuiStyle.button(g, this.font, delLeft - 92, contentTop + 3, delLeft - 6, contentTop + 18,
                I18n.get("panoptic.gui.copy_all"), copyAllHov, true, true);

        int max = Math.max(0, detail.size() * ROW_H - (contentBottom - detailTop));
        rightTarget = Mth.clamp(rightTarget, 0, max);
        rightScroll = Mth.clamp(rightScroll, 0, max);
        int scroll = (int) Math.round(rightScroll);

        scis(g, rightLeft - 1, detailTop - 1, rightRight + 1, contentBottom + 1);
        for (int i = 0; i < detail.size(); i++) {
            int y = detailTop - scroll + i * ROW_H;
            if (y + ROW_H < detailTop || y > contentBottom) continue;
            renderDetailRow(g, detail.get(i), y, mouseX, mouseY);
        }
        g.disableScissor();
        scrollbar(g, rightRight - 1, detailTop, contentBottom, detail.size() * ROW_H, scroll);

        if (!filesOpen && !modOpen && mouseX >= rightLeft && mouseX <= rightRight - 28
                && mouseY >= detailTop && mouseY <= contentBottom) {
            int hi = (int) ((mouseY - detailTop + rightScroll) / ROW_H);
            if (hi >= 0 && hi < detail.size()) {
                Row row = detail.get(hi);
                if (row.kind == Kind.FIELD && row.field.value != null) {
                    String label = I18n.get(row.field.label) + ": ";
                    int vx = rightLeft + 6 + this.font.width(label);
                    if (this.font.width(row.field.value) > rightRight - 24 - vx
                            && !expandedProps.contains(row.field.label)) {
                        renderFieldTooltip(g, row, mouseX, mouseY);
                    }
                }
            }
        }
    }

    private void renderFieldTooltip(GuiGraphics g, Row row, int mouseX, int mouseY) {
        List<String> lines = PreviewData.pretty(row.field.value);
        String title = I18n.get(row.field.label);
        int w = this.font.width(title) + 40;
        for (String ln : lines) {
            w = Math.max(w, this.font.width(ln) + 16);
        }
        w = Math.min(w, Math.max(160, vw - 20));
        int h = 16 + lines.size() * 10 + 4;
        int color = selected == null ? 0xE8C06C : selected.typeEnum().color();
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        anchor(w, h, mouseX, mouseY);
        int bx = panelX;
        int by = panelY;
        g.fill(bx + 2, by + 3, bx + w + 2, by + h + 3, 0x55000000);
        niceBox(g, bx, by, bx + w, by + h, GuiStyle.T(0xF21E1810), GuiStyle.T(0xF2140E08),
                lighten(color, 18), darken(color, 78));
        g.fill(bx + 1, by + 1, bx + w - 1, by + 12, GuiStyle.T(0xFF241D12));
        g.fill(bx + 1, by + 12, bx + w - 1, by + 13, (color & 0xFFFFFF) | 0x30000000);
        g.drawString(this.font, trim(title, w - 12), bx + 6, by + 2, GuiStyle.TEXT, false);
        int y = by + 16;
        for (String ln : lines) {
            g.drawString(this.font, trim(ln, w - 12), bx + 7, y, GuiStyle.T(0xFFD8D2C4), false);
            y += 10;
        }
        g.pose().popPose();
    }

    private void renderDetailRow(GuiGraphics g, Row row, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= rightLeft && mouseX <= rightRight && mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            g.fill(rightLeft, y, rightRight, y + ROW_H, ROW_HOVER);
        }
        int ty = y + 3;
        switch (row.kind) {
            case FIELD -> {
                String label = I18n.get(row.field.label) + ": ";
                g.drawString(this.font, label, rightLeft + 6, ty, LABEL);
                int vx = rightLeft + 6 + this.font.width(label);
                boolean overflow = row.field.value != null
                        && this.font.width(row.field.value) > rightRight - 24 - vx;
                int rightLimit = (row.field.copyable ? rightRight - 12 : rightRight - 4) - (overflow ? 12 : 0);
                g.drawString(this.font, trim(row.field.value, rightLimit - vx), vx, ty,
                        hovered && row.field.copyable ? 0xFFFFFF : VALUE);
                if (overflow) {
                    boolean exp = expandedProps.contains(row.field.label);
                    if (exp) {
                        Icons.iconTriDown(g, rightRight - 24, ty + 2, hovered ? GuiStyle.ACCENT : 0x90E8C06C);
                    } else {
                        Icons.iconTriRight(g, rightRight - 23, ty + 1, hovered ? GuiStyle.ACCENT : 0x90E8C06C);
                    }
                }
                if (row.field.copyable) {
                    Icons.iconCopy(g, rightRight - 11, y + 2, hovered ? GuiStyle.ACCENT : 0x60ACA188);
                }
            }
            case WRAP -> g.drawString(this.font, row.value, rightLeft + 16, ty, 0xFFC9C2B2);
            case LANG_TOGGLE -> {
                int lc = hovered ? 0xFFFFFFFF : GuiStyle.ACCENT;
                if (selected.langsExpanded) {
                    Icons.iconTriDown(g, rightLeft + 6, ty + 2, lc);
                } else {
                    Icons.iconTriRight(g, rightLeft + 7, ty + 1, lc);
                }
                g.drawString(this.font, I18n.get("panoptic.gui.names", row.count), rightLeft + 16, ty, lc);
            }
            case LANG -> {
                String label = row.langCode + ": ";
                g.drawString(this.font, label, rightLeft + 16, ty, LABEL);
                int vx = rightLeft + 16 + this.font.width(label);
                int rightLimit = rightRight - 12;
                g.drawString(this.font, trim(row.value, rightLimit - vx), vx, ty, hovered ? 0xFFFFFF : VALUE);
                Icons.iconCopy(g, rightRight - 11, y + 2, hovered ? GuiStyle.ACCENT : 0x60ACA188);
            }
            case FILES_TOGGLE -> {
                int fc = hovered ? 0xFFFFFFFF : GuiStyle.ACCENT;
                Icons.iconTriRight(g, rightLeft + 7, ty + 1, fc);
                g.drawString(this.font, I18n.get("panoptic.gui.files", row.count), rightLeft + 16, ty, fc);
                if (hovered) {
                    Icons.iconArrowRight(g, rightRight - 11, ty + 2, 0xE8C06C);
                }
            }
        }
    }

    private static final int F_HEADER_H = 15;
    private static final int F_ROW_H = 16;

    private void drawSearchIcon(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 1, y, x + 4, y + 1, c);
        g.fill(x + 1, y + 4, x + 4, y + 5, c);
        g.fill(x, y + 1, x + 1, y + 4, c);
        g.fill(x + 4, y + 1, x + 5, y + 4, c);
        g.fill(x + 4, y + 4, x + 6, y + 6, c);
        g.fill(x + 5, y + 5, x + 7, y + 7, c);
    }

    private void niceBox(GuiGraphics g, int x1, int y1, int x2, int y2, int bg, int bgB, int bTop, int bBot) {
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

    private int soundBarY() {
        return Math.min(fBottom + 6, vh - 26);
    }

    private void renderSoundBar(GuiGraphics g, int mouseX, int mouseY) {
        if (!SoundFilePreview.barVisible()) {
            return;
        }
        SoundFilePreview.tick();
        boolean active = SoundFilePreview.active();
        boolean playingNow = SoundFilePreview.playingNow();
        int bw = Math.min(280, fRight - fLeft - 40);
        int bx1 = (fLeft + fRight) / 2 - bw / 2;
        int bx2 = bx1 + bw;
        int by1 = soundBarY();
        int by2 = by1 + 23;
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        GuiStyle.plate(g, bx1, by1, bx2, by2, GuiStyle.T(0xF2332A19), GuiStyle.T(0xF21D1810), GuiStyle.BORDER_B);
        float pulse = playingNow
                ? 0.6F + 0.4F * Mth.sin(System.currentTimeMillis() % 900L / 900.0F * (float) (Math.PI * 2))
                : 0.0F;
        g.drawString(this.font, "♪", bx1 + 7, by1 + 5, GuiStyle.mix(GuiStyle.DIM, GuiStyle.ACCENT, pulse));
        long elapsed = SoundFilePreview.elapsedMs();
        long total = SoundFilePreview.durationMs();
        String time;
        if (!active) {
            time = "—";
        } else if (total > 0) {
            time = String.format(Locale.ROOT, "%d:%02d / %d:%02d",
                    elapsed / 60000L, elapsed / 1000L % 60, total / 60000L, total / 1000L % 60);
        } else {
            time = String.format(Locale.ROOT, "%d:%02d", elapsed / 60000L, elapsed / 1000L % 60);
        }
        int timeW = this.font.width(time);
        g.drawString(this.font, trim(SoundFilePreview.label(), bx2 - 48 - timeW - (bx1 + 18)),
                bx1 + 18, by1 + 5, GuiStyle.TEXT);
        g.drawString(this.font, time, bx2 - 38 - timeW, by1 + 5, GuiStyle.DIM);
        int tlx1 = bx1 + 7;
        int tlx2 = bx2 - 40;
        int tly = by2 - 5;
        boolean tlHov = mouseX >= tlx1 && mouseX <= tlx2 && mouseY >= tly - 4 && mouseY <= by2;
        g.fill(tlx1, tly, tlx2, tly + 2, GuiStyle.T(0x50000000));
        float prog = SoundFilePreview.progress();
        if (prog > 0.0F) {
            int px = tlx1 + Math.round((tlx2 - tlx1) * prog);
            g.fill(tlx1, tly, px, tly + 2, GuiStyle.ACCENT);
            g.fill(px - 1, tly - 2, px + 1, tly + 4, tlHov ? 0xFFFFF4D8 : GuiStyle.ACCENT);
        }

        boolean playHov = mouseX >= bx2 - 33 && mouseX <= bx2 - 20 && mouseY >= by1 + 3 && mouseY <= by1 + 20;
        if (playHov) {
            g.fill(bx2 - 33, by1 + 3, bx2 - 20, by1 + 20, GuiStyle.ROWHOVER);
        }
        int pc = playHov ? GuiStyle.ACCENT : GuiStyle.MUTED;
        if (playingNow) {
            g.fill(bx2 - 31, by1 + 8, bx2 - 29, by1 + 15, pc);
            g.fill(bx2 - 26, by1 + 8, bx2 - 24, by1 + 15, pc);
        } else {
            Icons.iconTriRight(g, bx2 - 30, by1 + 8, pc);
        }
        boolean closeHov = mouseX >= bx2 - 17 && mouseX <= bx2 - 4 && mouseY >= by1 + 3 && mouseY <= by1 + 20;
        if (closeHov) {
            g.fill(bx2 - 17, by1 + 3, bx2 - 4, by1 + 20, 0x40FF5B5B);
        }
        Icons.iconCross(g, bx2 - 14, by1 + 8, closeHov ? 0xFFFF6B6B : GuiStyle.MUTED);
        g.pose().popPose();
    }

    private void renderFilesOverlay(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, vw, vh, 0xC8090B10);

        int w = Math.min(680, vw - 36);
        int h = Math.min(424, vh - 32);
        fLeft = (vw - w) / 2;
        fTop = (vh - h) / 2;
        fRight = fLeft + w;
        fBottom = fTop + h;
        fChipsY = fTop + 40;
        fCBottom = fBottom - 2;

        g.fill(fLeft + 3, fTop + 4, fRight + 3, fBottom + 4, 0x66000000);
        niceBox(g, fLeft, fTop, fRight, fBottom, GuiStyle.BG, GuiStyle.BG2, GuiStyle.BORDER_T, GuiStyle.BORDER_B);

        g.fill(fLeft + 1, fTop + 1, fRight - 1, fTop + 19, GuiStyle.TITLE);
        g.fill(fLeft + 1, fTop + 19, fRight - 1, fTop + 20, GuiStyle.T(0x3DE8C06C));
        int titleX = fLeft + 6;
        if (selected != null && !selected.icon().isEmpty()) {
            g.renderFakeItem(selected.icon(), titleX, fTop + 2);
            titleX += 19;
        }
        String name = selected == null ? "" : selected.title;
        int nameMax = fRight - 40 - titleX;
        scis(g, titleX, fTop + 1, fRight - 36, fTop + 19);
        g.drawString(this.font, trim(name, nameMax), titleX, fTop + 6, GuiStyle.TEXT, false);
        int infoX = titleX + Math.min(this.font.width(name), nameMax) + 6;
        String cnt = I18n.get("panoptic.gui.files", selected == null ? 0 : selected.files.size());
        g.drawString(this.font, cnt, infoX, fTop + 6, GuiStyle.DIM, false);
        if (!filesSelected.isEmpty()) {
            String selCnt = String.valueOf(filesSelected.size());
            int bx = infoX + this.font.width(cnt) + 6;
            int bw = 14 + this.font.width(selCnt);
            g.fill(bx, fTop + 4, bx + bw, fTop + 16, GuiStyle.T(0xFF33280F));
            rect(g, bx, fTop + 4, bx + bw, fTop + 16, GuiStyle.T(0xFF8C6C33));
            Icons.iconCheck(g, bx + 3, fTop + 7, GuiStyle.ACCENT);
            g.drawString(this.font, selCnt, bx + 11, fTop + 6, GuiStyle.ACCENT, false);
        }
        g.disableScissor();
        boolean copyHover = mouseX >= fRight - 32 && mouseX <= fRight - 19 && mouseY >= fTop + 3 && mouseY <= fTop + 17;
        if (copyHover) g.fill(fRight - 32, fTop + 3, fRight - 19, fTop + 17, GuiStyle.ROWHOVER);
        Icons.iconCopy(g, fRight - 29, fTop + 7, copyHover ? GuiStyle.ACCENT : GuiStyle.MUTED);
        boolean closeHover = mouseX >= fRight - 18 && mouseX <= fRight - 5 && mouseY >= fTop + 3 && mouseY <= fTop + 17;
        if (closeHover) g.fill(fRight - 18, fTop + 3, fRight - 5, fTop + 17, 0x40FF5B5B);
        Icons.iconCross(g, fRight - 14, fTop + 7, closeHover ? 0xFFFF6B6B : GuiStyle.MUTED);

        int sy = fTop + 22;
        int helpX = fRight - 6 - 13;
        fSearchLeft = fLeft + 6;
        fSearchRight = helpX - 4;
        boolean clr = !filesFilter.isEmpty();
        g.fill(fSearchLeft, sy, fSearchRight, sy + 14, GuiStyle.SEARCH_BG);
        rect(g, fSearchLeft, sy, fSearchRight, sy + 14, filesSearchFocused ? GuiStyle.ACCENT : GuiStyle.T(0xFF6A5630));
        drawSearchIcon(g, fSearchLeft + 4, sy + 4, filesSearchFocused ? GuiStyle.ACCENT : GuiStyle.MUTED);
        int txStart = fSearchLeft + 15;
        int txEnd = fSearchRight - (clr ? 13 : 4);
        if (!clr) {
            g.drawString(this.font, I18n.get("panoptic.gui.files_search"), txStart, sy + 4, GuiStyle.DIM, false);
        } else {
            int caretPx = this.font.width(filesFilter.substring(0, Mth.clamp(filesCaret, 0, filesFilter.length())));
            int fieldW = txEnd - txStart;
            int scx = Math.max(0, caretPx - fieldW + 2);
            scis(g, txStart, sy, txEnd, sy + 14);
            TextOps.drawSel(g, this.font, filesFilter, filesCaret, filesSel, txStart - scx, sy + 3, sy + 12);
            g.drawString(this.font, filesFilter, txStart - scx, sy + 4, GuiStyle.TEXT, false);
            if (filesSearchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cxp = txStart - scx + caretPx;
                g.fill(cxp, sy + 3, cxp + 1, sy + 12, GuiStyle.ACCENT);
            }
            g.disableScissor();
            boolean clrHover = mouseX >= fSearchRight - 13 && mouseX <= fSearchRight - 2 && mouseY >= sy && mouseY <= sy + 14;
            Icons.iconCross(g, fSearchRight - 11, sy + 5, clrHover ? 0xFFFF6B6B : GuiStyle.MUTED);
        }
        fHelpHover = HelpCard.icon(g, this.font, helpX, sy, mouseX, mouseY);

        computeSuggestions();

        int cy = drawChipRow(g, kindChips, filesKind, fChipsY, mouseX, mouseY);
        if (!nsChips.isEmpty()) {
            cy += 14;
            cy = drawChipRow(g, nsChips, filesNs, cy, mouseX, mouseY);
        }
        fCTop = cy + 12 + 5;

        int viewH = fCBottom - fCTop;
        int max = Math.max(0, filesContentH - viewH);
        filesTarget = Mth.clamp(filesTarget, 0, max);
        g.fillGradient(fLeft + 1, fCTop, fRight - 1, fCBottom, GuiStyle.PANEL, GuiStyle.PANEL2);
        g.fill(fLeft + 1, fCTop, fRight - 1, fCTop + 1, GuiStyle.BORDER);

        FRow preview = null;
        scis(g, fLeft + 1, fCTop, fRight - 1, fCBottom);
        int y = fCTop - (int) filesScroll;
        for (FRow row : fileRows) {
            int rh = row.header ? F_HEADER_H : F_ROW_H;
            if (y + rh >= fCTop && y <= fCBottom) {
                if (row.header) {
                    boolean ch = mouseX >= fLeft + 1 && mouseX <= fRight - 1 && mouseY >= y && mouseY < y + F_HEADER_H
                            && mouseY >= fCTop && mouseY <= fCBottom;
                    g.fill(fLeft + 1, y, fRight - 1, y + F_HEADER_H, ch ? 0xFF332A1B : GuiStyle.HEADER);
                    g.fill(fLeft + 1, y + F_HEADER_H - 1, fRight - 1, y + F_HEADER_H, GuiStyle.BORDER);
                    if (row.collapsed) {
                        Icons.iconTriRight(g, fLeft + 6, y + 5, GuiStyle.MUTED);
                    } else {
                        Icons.iconTriDown(g, fLeft + 5, y + 6, GuiStyle.MUTED);
                    }
                    g.drawString(this.font, trim(row.text, fRight - 42 - fLeft), fLeft + 14, y + 4, GuiStyle.TEXT, false);
                    String c = String.valueOf(row.count);
                    g.drawString(this.font, c, fRight - 10 - this.font.width(c), y + 4, GuiStyle.DIM, false);
                } else {
                    boolean hovered = mouseX >= fLeft + 1 && mouseX <= fRight - 1 && mouseY >= y && mouseY < y + F_ROW_H
                            && mouseY >= fCTop && mouseY <= fCBottom;
                    boolean sel = filesSelected.contains(row.dir + row.name);
                    if (sel) {
                        g.fill(fLeft + 1, y, fRight - 1, y + F_ROW_H, 0x2DC9A45E);
                    }
                    if (hovered) {
                        g.fill(fLeft + 1, y, fRight - 1, y + F_ROW_H, GuiStyle.ROWHOVER);
                        preview = row;
                    }
                    g.fill(fLeft + 1, y, fLeft + 3, y + F_ROW_H, row.color);
                    int cbx = fLeft + 6;
                    int cby = y + 4;
                    g.fill(cbx, cby, cbx + 9, cby + 9, sel ? GuiStyle.ACCENT : 0xFF2E2718);
                    rect(g, cbx, cby, cbx + 9, cby + 9, sel ? GuiStyle.ACCENT : GuiStyle.DIM);
                    if (sel) {
                        Icons.iconCheck(g, cbx + 2, cby + 2, 0xFF17130C);
                    }
                    int pillW = this.font.width(row.ext) + 6;
                    int px = fLeft + 18;
                    g.fill(px, y + 3, px + pillW, y + 13, (row.color & 0x00FFFFFF) | 0x30000000);
                    rect(g, px, y + 3, px + pillW, y + 13, (row.color & 0x00FFFFFF) | 0x90000000);
                    g.drawString(this.font, row.ext, px + 3, y + 4, row.color, false);
                    boolean lintBad = jsonLike(row) && !ensureParsed(row).lint().ok();
                    int tx = px + pillW + 6;
                    int avail = fRight - 8 - (hovered ? 11 : 0) - (lintBad ? 11 : 0) - tx;
                    if (this.font.width(row.dir + row.name) <= avail) {
                        g.drawString(this.font, row.dir, tx, y + 4, GuiStyle.DIM, false);
                        g.drawString(this.font, row.name, tx + this.font.width(row.dir), y + 4,
                                hovered ? 0xFFFFFFFF : GuiStyle.TEXT, false);
                    } else {
                        g.drawString(this.font, "…/", tx, y + 4, GuiStyle.DIM, false);
                        g.drawString(this.font, trim(row.name, avail - this.font.width("…/")),
                                tx + this.font.width("…/"), y + 4, hovered ? 0xFFFFFFFF : GuiStyle.TEXT, false);
                    }
                    int badgeX = fRight - 12;
                    if (hovered) {
                        boolean copyHov = mouseX >= fRight - 15;
                        Icons.iconCopy(g, badgeX, y + 3, copyHov ? 0xFFFFFFFF : GuiStyle.ACCENT);
                        badgeX -= 11;
                    }
                    if (lintBad) {
                        Icons.iconWarn(g, badgeX, y + 4, 0xFFFFB454);
                    }
                }
            }
            y += rh;
        }
        g.disableScissor();

        if (filesContentH > viewH) {
            g.fill(fRight - 4, fCTop, fRight - 1, fCBottom, 0x30000000);
            int thumbH = Math.max(20, viewH * viewH / filesContentH);
            int thumbY = fCTop + (int) ((viewH - thumbH) * filesScroll / max);
            g.fill(fRight - 4, thumbY, fRight - 1, thumbY + thumbH, GuiStyle.DIM);
        }

        renderSuggestions(g, mouseX, mouseY);

        if (System.currentTimeMillis() - lastFilesListScroll < 350) {
            preview = null;
        }
        if (preview != previewRow) {
            boolean wasNull = previewRow == null;
            previewRow = preview;
            previewScroll = 0;
            previewTarget = 0;
            if (wasNull && preview != null) {
                previewAppearStart = System.nanoTime();
            }
        }
        if (preview != null) {
            float ap = previewAppear();
            g.pose().pushPose();
            float s = 0.94F + 0.06F * ap;
            g.pose().translate(mouseX, mouseY, 0);
            g.pose().scale(s, s, 1.0F);
            g.pose().translate(-mouseX, -mouseY, 0);
            renderFilePreview(g, preview, mouseX, mouseY);
            g.pose().popPose();
        }

        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.gui.help_b1"));
        bullets.add(Component.translatable("panoptic.gui.help_b2"));
        bullets.add(Component.translatable("panoptic.gui.help_b3"));
        bullets.add(Component.translatable("panoptic.gui.help_b4"));
        List<HelpCard.KeyHint> keys = List.of(
                ModBinds.hint(ModBinds.Bind.COPY_PATHS, "panoptic.gui.copy_paths"),
                ModBinds.hint(ModBinds.Bind.COPY_IDS, "panoptic.gui.copy_ids"),
                ModBinds.hint(ModBinds.Bind.COPY_CODE, "panoptic.gui.copy_code"),
                ModBinds.hint(ModBinds.Bind.SELECT_ALL, "panoptic.gui.sel_all"),
                ModBinds.hint(ModBinds.Bind.INVERT_SEL, "panoptic.gui.sel_invert"),
                ModBinds.hint(ModBinds.Bind.CLEAR_SEL, "panoptic.gui.sel_none"));
        HelpCard.render(g, this.font, vw, vh, fHelpHover, Component.translatable("panoptic.gui.help_title"),
                Component.translatable("panoptic.gui.help_sum"), bullets, keys);
    }

    private float previewAppear() {
        if (previewAppearStart == 0) {
            return 1.0F;
        }
        float t = Mth.clamp((System.nanoTime() - previewAppearStart) / 1.3E8F, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private int drawChipRow(GuiGraphics g, List<KindChip> chips, String activeId, int cy, int mouseX, int mouseY) {
        int startCx = fLeft + 6;
        int cx = startCx;
        int chipRight = fRight - 6;
        for (KindChip chip : chips) {
            if (cx > startCx && cx + chip.w > chipRight) {
                cx = startCx;
                cy += 14;
            }
            boolean active = Objects.equals(chip.id, activeId);
            boolean ch = mouseX >= cx && mouseX <= cx + chip.w && mouseY >= cy && mouseY <= cy + 12;
            chip.x = cx;
            chip.y = cy;
            GuiStyle.plate(g, cx, cy, cx + chip.w, cy + 12,
                    active ? 0xFF4A3A1D : ch ? 0xFF362C1A : 0xFF261F14,
                    active ? 0xFF2A2113 : ch ? 0xFF1F1810 : 0xFF17120B,
                    active ? chip.color : GuiStyle.BORDER);
            g.fill(cx + 1, cy + 1, cx + 3, cy + 11, chip.color);
            g.drawString(this.font, chip.label, cx + 6, cy + 2, active ? GuiStyle.TEXT : GuiStyle.MUTED, false);
            cx += chip.w + 4;
        }
        return cy;
    }

    private int wordLeft(int c) {
        int i = c;
        while (i > 0 && filesFilter.charAt(i - 1) == ' ') i--;
        while (i > 0 && filesFilter.charAt(i - 1) != ' ') i--;
        return i;
    }

    private int wordRight(int c) {
        int n = filesFilter.length();
        int i = c;
        while (i < n && filesFilter.charAt(i) != ' ') i++;
        while (i < n && filesFilter.charAt(i) == ' ') i++;
        return i;
    }

    private void pasteIntoSearch() {
        String clip = this.minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) {
            return;
        }
        clip = clip.replace("\r", "").replace("\n", " ");
        filesFilter = filesFilter.substring(0, filesCaret) + clip + filesFilter.substring(filesCaret);
        filesCaret += clip.length();
        rebuildFileRows();
    }

    private void computeSuggestions() {
        suggestions.clear();
        int caret = Mth.clamp(filesCaret, 0, filesFilter.length());
        int start = caret;
        while (start > 0 && filesFilter.charAt(start - 1) != ' ') {
            start--;
        }
        suggestTokenStart = start;
        String token = filesFilter.substring(start, caret);
        if (!token.equals(lastSugToken)) {
            lastSugToken = token;
            suggestSel = token.isEmpty() ? -1 : 0;
            suggestHidden = false;
        }
        if (token.isEmpty()) {
            return;
        }
        String low = token.toLowerCase(Locale.ROOT);
        int colon = low.indexOf(':');
        if (colon >= 0) {
            String key = low.substring(0, colon);
            String val = low.substring(colon + 1);
            Map<String, int[]> m = "kind".equals(key) ? kindCountMap
                    : "ns".equals(key) ? nsCountMap : "ext".equals(key) ? extCountMap : null;
            if (m != null) {
                addValueSuggestions(key, val, m, false);
            }
        } else {
            for (String kw : new String[]{"kind:", "ns:", "ext:", "in:"}) {
                if (kw.startsWith(low)) {
                    suggestions.add(new Suggestion(kw, kw, I18n.get("panoptic.gui.sug_filter"), GuiStyle.ACCENT));
                }
            }
            addValueSuggestions("kind", low, kindCountMap, true);
            addValueSuggestions("ns", low, nsCountMap, true);
            addValueSuggestions("ext", low, extCountMap, true);
        }
        if (suggestions.size() > 10) {
            suggestions.subList(10, suggestions.size()).clear();
        }
        if (suggestSel >= suggestions.size()) {
            suggestSel = suggestions.size() - 1;
        }
    }

    private void addValueSuggestions(String key, String partial, Map<String, int[]> counts, boolean prefixOnly) {
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            String v = e.getKey();
            if (v.isEmpty()) {
                continue;
            }
            String vl = v.toLowerCase(Locale.ROOT);
            boolean ok = partial.isEmpty() || (prefixOnly ? vl.startsWith(partial) : vl.contains(partial));
            if (!ok) {
                continue;
            }
            int color = "kind".equals(key) ? kindColor(v) : "ns".equals(key) ? nsColor(v) : GuiStyle.MUTED;
            suggestions.add(new Suggestion(key + ":" + v + " ", key + ":" + v, String.valueOf(e.getValue()[0]), color));
        }
    }

    private void acceptSuggestion(Suggestion s) {
        int caret = Mth.clamp(filesCaret, 0, filesFilter.length());
        String before = filesFilter.substring(0, suggestTokenStart) + s.insert();
        filesFilter = before + filesFilter.substring(caret);
        filesCaret = before.length();
        suggestHidden = !s.insert().endsWith(":");
        lastSugToken = " ";
        rebuildFileRows();
        sound(1.2F);
    }

    private void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        if (suggestHidden || suggestions.isEmpty()) {
            return;
        }
        sugRowH = 12;
        int n = suggestions.size();
        int wMax = 90;
        for (Suggestion s : suggestions) {
            wMax = Math.max(wMax, this.font.width(s.label()) + this.font.width(s.hint()) + 24);
        }
        sugX = fSearchLeft;
        sugW = Math.min(wMax, fSearchRight - fSearchLeft);
        sugY = fTop + 37;
        int dh = n * sugRowH + 2;
        g.fill(sugX + 2, sugY + 2, sugX + sugW + 2, sugY + dh + 2, 0x66000000);
        niceBox(g, sugX, sugY, sugX + sugW, sugY + dh, 0xF41E1810, 0xF4140E08, GuiStyle.BORDER_T, GuiStyle.BORDER_B);
        int yy = sugY + 1;
        for (int i = 0; i < n; i++) {
            Suggestion s = suggestions.get(i);
            boolean hov = mouseX >= sugX && mouseX <= sugX + sugW && mouseY >= yy && mouseY < yy + sugRowH;
            if (hov) {
                suggestSel = i;
            }
            if (i == suggestSel) {
                g.fill(sugX + 1, yy, sugX + sugW - 1, yy + sugRowH, 0x33E8C06C);
            }
            g.fill(sugX + 1, yy, sugX + 3, yy + sugRowH, s.color());
            g.drawString(this.font, trim(s.label(), sugW - 16 - this.font.width(s.hint())), sugX + 6, yy + 2,
                    i == suggestSel ? GuiStyle.TEXT : GuiStyle.MUTED, false);
            if (!s.hint().isEmpty()) {
                g.drawString(this.font, s.hint(), sugX + sugW - 5 - this.font.width(s.hint()), yy + 2, GuiStyle.DIM, false);
            }
            yy += sugRowH;
        }
    }

    private void placeCaret(int mouseX) {
        int txStart = fSearchLeft + 15;
        int txEnd = fSearchRight - (filesFilter.isEmpty() ? 4 : 13);
        int caretPx = this.font.width(filesFilter.substring(0, Mth.clamp(filesCaret, 0, filesFilter.length())));
        int scx = Math.max(0, caretPx - (txEnd - txStart) + 2);
        int target = mouseX - (txStart - scx);
        int idx = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i <= filesFilter.length(); i++) {
            int d = Math.abs(this.font.width(filesFilter.substring(0, i)) - target);
            if (d < best) {
                best = d;
                idx = i;
            }
        }
        filesCaret = idx;
    }

    private void toggleFileSelect(int index) {
        FRow row = fileRows.get(index);
        if (hasShiftDown() && lastFileSel >= 0 && lastFileSel < fileRows.size()) {
            int a = Math.min(lastFileSel, index);
            int b = Math.max(lastFileSel, index);
            for (int i = a; i <= b; i++) {
                FRow r = fileRows.get(i);
                if (!r.header) {
                    filesSelected.add(r.dir + r.name);
                }
            }
        } else {
            String key = row.dir + row.name;
            if (!filesSelected.add(key)) {
                filesSelected.remove(key);
            }
        }
        lastFileSel = index;
        sound(1.2F);
    }

    private int fileRowAt(int mouseY) {
        int y = fCTop - (int) filesScroll;
        for (int i = 0; i < fileRows.size(); i++) {
            int rh = fileRows.get(i).header ? F_HEADER_H : F_ROW_H;
            if (mouseY >= y && mouseY < y + rh) {
                return i;
            }
            y += rh;
        }
        return -1;
    }

    private void fileDragTo(int index) {
        if (index < 0 || index == fileDragLast || fileDragLast < 0) {
            return;
        }
        int lo = Math.min(fileDragLast, index);
        int hi = Math.max(fileDragLast, index);
        for (int i = lo; i <= hi && i < fileRows.size(); i++) {
            FRow r = fileRows.get(i);
            if (r.header) {
                continue;
            }
            String k = r.dir + r.name;
            if (fileDragAdd) {
                filesSelected.add(k);
            } else {
                filesSelected.remove(k);
            }
        }
        fileDragLast = index;
        lastFileSel = index;
    }

    private void renderTexturePreview(GuiGraphics g, FRow row, int mouseX, int mouseY) {
        ResourceLocation rl = textureRL(row);
        if (rl == null) {
            return;
        }
        int[] dim = texDims.computeIfAbsent(row.dir + row.name, k -> readPngDims(rl));
        if (dim[0] <= 0 || dim[1] <= 0) {
            return;
        }
        int box = 128;
        int dw = box;
        int dh = box;
        if (dim[0] >= dim[1]) {
            dh = Math.max(8, box * dim[1] / dim[0]);
        } else {
            dw = Math.max(8, box * dim[0] / dim[1]);
        }
        String title = "PNG  " + row.name;
        int w = fitW(Math.max(120, dw + 12), title);
        int hh = 12 + 4 + dh + 11;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int ix = panelX + (w - dw) / 2;
        int iy = top + 3;
        for (int i = 0; i * 8 < dw; i++) {
            for (int j = 0; j * 8 < dh; j++) {
                int col = (i + j) % 2 == 0 ? 0xFF262017 : 0xFF1A150D;
                g.fill(ix + i * 8, iy + j * 8, Math.min(ix + dw, ix + i * 8 + 8), Math.min(iy + dh, iy + j * 8 + 8), col);
            }
        }
        try {
            g.blit(rl, ix, iy, dw, dh, 0.0F, 0.0F, dim[0], dim[1], dim[0], dim[1]);
        } catch (Throwable ignored) {
        }
        g.drawString(this.font, dim[0] + "×" + dim[1], panelX + 5, iy + dh + 2, GuiStyle.MUTED, false);
    }

    private void renderFilePreview(GuiGraphics g, FRow row, int mouseX, int mouseY) {
        if ("texture".equals(row.kind)) {
            renderTexturePreview(g, row, mouseX, mouseY);
            return;
        }
        if ("NBT".equals(row.ext)) {
            renderStructPreview(g, row, mouseX, mouseY);
            return;
        }
        PreviewParsers.Parsed parsed = jsonLike(row) ? ensureParsed(row) : null;
        Object view = parsed == null ? null : parsed.view();
        List<String> issues = parsed == null || parsed.lint().ok() ? List.of() : parsed.lint().issues();
        if (!Screen.hasAltDown()) {
            if (view instanceof PreviewParsers.Recipe r) {
                renderRecipe(g, row, r, issues, mouseX, mouseY);
                return;
            }
            if (view instanceof PreviewParsers.Tag t) {
                renderTag(g, row, t, issues, mouseX, mouseY);
                return;
            }
            if (view instanceof PreviewParsers.Loot l) {
                renderLoot(g, row, l, issues, mouseX, mouseY);
                return;
            }
            if (view instanceof PreviewParsers.Lang lang) {
                renderLang(g, row, lang, issues, mouseX, mouseY);
                return;
            }
            if (view instanceof PreviewParsers.Model m) {
                renderModel(g, row, m, issues, mouseX, mouseY);
                return;
            }
            if (view instanceof PreviewParsers.Advancement a) {
                renderAdvancement(g, row, a, issues, mouseX, mouseY);
                return;
            }
            if (view instanceof PreviewParsers.Blockstate b) {
                renderBlockstate(g, row, b, issues, mouseX, mouseY);
                return;
            }
        }
        List<String> lines = previewCache.computeIfAbsent(row.dir + row.name,
                k -> PreviewData.lines(row.file, row.dir, row.name, row.ext));
        if (lines.isEmpty()) {
            return;
        }
        previewPanel(g, row, lines, jsonLike(row), !"OGG".equals(row.ext), issues, mouseX, mouseY);
    }

    private boolean jsonLike(FRow row) {
        return "JSON".equals(row.ext) || "MCMETA".equals(row.ext);
    }

    private PreviewParsers.Parsed ensureParsed(FRow row) {
        String key = row.dir + row.name;
        PreviewParsers.Parsed p = parsedCache.get(key);
        if (p == null) {
            byte[] data = PreviewData.readBytes(row.file, row.dir, row.name, 2_000_000);
            p = data == null
                    ? new PreviewParsers.Parsed(null, new PreviewParsers.Lint(true, List.of()), null)
                    : PreviewParsers.parse(key, new String(data, StandardCharsets.UTF_8));
            parsedCache.put(key, p);
        }
        return p;
    }

    private void anchor(int w, int h, int mouseX, int mouseY) {
        int x = mouseX + 14;
        if (x + w > vw - 2) {
            x = mouseX - 14 - w;
        }
        panelX = Mth.clamp(x, 2, Math.max(2, vw - w - 2));
        panelY = Mth.clamp(mouseY + 10, 2, Math.max(2, vh - h - 2));
    }

    private int fitW(int contentW, String title) {
        return Math.min(Math.max(contentW, this.font.width(title) + 56), vw - 6);
    }

    private int frame(GuiGraphics g, FRow row, int w, int h, int mouseX, int mouseY, String title) {
        anchor(w, h, mouseX, mouseY);
        int bx = panelX;
        int by = panelY;
        g.fill(bx + 2, by + 3, bx + w + 2, by + h + 3, 0x55000000);
        niceBox(g, bx, by, bx + w, by + h, 0xF21E1810, 0xF2140E08, lighten(row.color, 18), darken(row.color, 78));
        g.fill(bx + 1, by + 1, bx + w - 1, by + 12, 0xFF241D12);
        g.fill(bx + 1, by + 12, bx + w - 1, by + 13, (row.color & 0xFFFFFF) | 0x30000000);
        g.drawString(this.font, trim(title, w - 56), bx + 6, by + 2, GuiStyle.TEXT, false);
        return by + 12;
    }

    private static int lighten(int c, int d) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + d);
        int g = Math.min(255, ((c >> 8) & 0xFF) + d);
        int b = Math.min(255, (c & 0xFF) + d);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int c, int d) {
        int r = Math.max(0, ((c >> 16) & 0xFF) - d);
        int g = Math.max(0, ((c >> 8) & 0xFF) - d);
        int b = Math.max(0, (c & 0xFF) - d);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private List<String> wrapIssues(List<String> issues, int wrapW) {
        List<String> out = new ArrayList<>();
        int maxW = Math.max(60, wrapW);
        for (String issue : issues) {
            String rest = issue;
            boolean first = true;
            while (!rest.isEmpty() && out.size() < 16) {
                String chunk = this.font.plainSubstrByWidth(rest, maxW);
                if (chunk.isEmpty()) {
                    break;
                }
                out.add((first ? "" : "") + chunk);
                rest = rest.substring(chunk.length());
                first = false;
            }
            if (out.size() >= 16) {
                break;
            }
        }
        return out;
    }

    private void lintFooter(GuiGraphics g, int bx, int bottom, int w, List<String> issues, int mouseX, int mouseY) {
        if (issues.isEmpty()) {
            return;
        }
        List<String> lines = wrapIssues(issues, 316);
        int n = lines.size();
        int fh = n * 9 + 4;
        g.fill(bx + 1, bottom - fh, bx + w - 1, bottom - 1, GuiStyle.T(0xFF2A1414));
        g.fill(bx + 1, bottom - fh - 1, bx + w - 1, bottom - fh, 0xFFFF6B6B);
        for (int i = 0; i < n; i++) {
            String ln = lines.get(i);
            int ty = bottom - fh + 2 + i * 9;
            if (ln.startsWith("")) {
                Icons.iconWarn(g, bx + 5, ty, 0xFFFF9C8A);
                ln = ln.substring(1);
            }
            g.drawString(this.font, trim(ln, w - 20), bx + 15, ty, 0xFFFF9C8A, false);
        }
        if (mouseX >= bx && mouseX <= bx + w && mouseY >= bottom - fh && mouseY <= bottom) {
            List<Component> tip = new ArrayList<>();
            for (String issue : issues) {
                String rest = issue;
                boolean first = true;
                while (!rest.isEmpty()) {
                    String chunk = this.font.plainSubstrByWidth(rest, 300);
                    if (chunk.isEmpty()) {
                        break;
                    }
                    tip.add(Component.literal((first ? "§c" : "§7  ") + chunk));
                    rest = rest.substring(chunk.length());
                    first = false;
                }
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            try {
                g.renderTooltip(this.font, tip, Optional.empty(), mouseX, mouseY);
            } catch (Throwable ignored) {
            }
            g.pose().popPose();
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF2A2F3D);
        rect(g, x, y, x + 18, y + 18, GuiStyle.BORDER);
    }

    private boolean overSlot(int mouseX, int mouseY, int sx, int sy) {
        return mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
    }

    private int footerH(List<String> issues, int w) {
        return issues.isEmpty() ? 0 : wrapIssues(issues, w - 24).size() * 9 + 5;
    }

    private void previewPanel(GuiGraphics g, FRow row, List<String> lines, boolean json, boolean numbered, List<String> issues, int mouseX, int mouseY) {
        int pad = 5;
        int lineH = 9;
        boolean singleLong = lines.size() == 1 && this.font.width(lines.get(0)) > 250;
        boolean tabFmt = false;
        if (singleLong && json && isTabDown()) {
            lines = PreviewData.pretty(lines.get(0));
            tabFmt = true;
            singleLong = false;
        }
        int total = lines.size();
        int visible = Math.min(total, PREVIEW_LINES);
        int maxScroll = Math.max(0, total - PREVIEW_LINES);
        int off = Mth.clamp((int) Math.round(previewScroll), 0, maxScroll);
        int gutter = numbered ? this.font.width(String.valueOf(total)) + 9 : 0;
        int maxLineW;
        if (tabFmt) {
            int m = 0;
            for (String ln : lines) {
                m = Math.max(m, this.font.width(ln));
            }
            maxLineW = Math.min(m, 340);
        } else {
            final List<String> measured = lines;
            maxLineW = measCache.computeIfAbsent("txt|" + row.dir + row.name, k -> {
                int m = 0;
                for (String ln : measured) {
                    m = Math.max(m, this.font.width(ln));
                }
                return new int[]{m};
            })[0];
        }
        int contentW = Math.max(90, gutter + maxLineW);
        int bar = maxScroll > 0 ? 4 : 0;
        String title = row.ext + "  " + row.name;
        int w = fitW(contentW + pad * 2 + bar, title);
        int fH = footerH(issues, 340);
        int hh = 12 + visible * lineH + pad + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        int by = panelY;
        if (maxScroll > 0) {
            String pos = (off + 1) + "–" + (off + visible) + "/" + total;
            g.drawString(this.font, pos, bx + w - 5 - this.font.width(pos), by + 2, GuiStyle.DIM, false);
        }
        int textX = bx + pad + gutter;
        int textW = w - pad * 2 - bar - gutter;
        int contentBottom = by + hh - fH;
        scis(g, bx + 1, top, bx + w - 1 - bar, contentBottom);
        if (numbered) {
            g.fill(bx + 1, top, bx + pad + gutter - 4, contentBottom, 0x1FFFFFFF);
            g.fill(bx + pad + gutter - 4, top, bx + pad + gutter - 3, contentBottom, 0x22FFFFFF);
        }
        int ty = top + 1;
        String lowName = row.name == null ? "" : row.name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < visible; i++) {
            if (numbered) {
                String num = String.valueOf(off + i + 1);
                g.drawString(this.font, num, bx + pad + gutter - 7 - this.font.width(num), ty, GuiStyle.DIM, false);
            }
            String line = lines.get(off + i);
            int shift = 0;
            if (singleLong) {
                int over = this.font.width(line) - textW;
                if (over > 0) {
                    long cycle = 1600L + over * 24L + 1600L;
                    long t = System.currentTimeMillis() % cycle;
                    long travel = Math.max(1, cycle - 3200L);
                    shift = t < 1600L ? 0 : t > cycle - 1600L ? over
                            : (int) ((t - 1600L) * over / travel);
                }
            }
            if (json) {
                Syntax.json(g, this.font, line, textX - shift, ty, textW + shift);
            } else if (Syntax.isCode(lowName)) {
                Syntax.glsl(g, this.font, line, textX - shift, ty, textW + shift);
            } else if (Syntax.isKv(lowName)) {
                Syntax.kv(g, this.font, line, textX - shift, ty, textW + shift);
            } else if (singleLong) {
                g.drawString(this.font, line, textX - shift, ty, GuiStyle.TEXT, false);
            } else {
                g.drawString(this.font, trim(line, textW), textX, ty, GuiStyle.TEXT, false);
            }
            ty += lineH;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackTop = top + 1;
            int trackH = visible * lineH;
            g.fill(bx + w - 3, trackTop, bx + w - 1, trackTop + trackH, 0x33FFFFFF);
            int thumbH = Math.max(8, trackH * visible / total);
            int thumbY = trackTop + (trackH - thumbH) * off / maxScroll;
            g.fill(bx + w - 3, thumbY, bx + w - 1, thumbY + thumbH, row.color);
        }
        lintFooter(g, bx, by + hh, w, issues, mouseX, mouseY);
    }

    private void renderRecipe(GuiGraphics g, FRow row, PreviewParsers.Recipe r, List<String> issues, int mouseX, int mouseY) {
        int slot = 18;
        int gridW = r.gw() * slot;
        int gridH = r.gh() * slot;
        int contentH = Math.max(gridH, slot) + (r.note() != null ? 10 : 0);
        int fH = footerH(issues, 340);
        String title = r.layout() + "  " + row.name;
        int w = fitW(Math.max(160, 8 + gridW + 20 + slot + 10), title);
        int hh = 12 + 6 + contentH + 6 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        int gx = bx + 8;
        int gy = top + 6;
        ItemStack tip = ItemStack.EMPTY;
        for (int i = 0; i < r.grid().length; i++) {
            int cx = gx + (i % r.gw()) * slot;
            int cy = gy + (i / r.gw()) * slot;
            drawSlot(g, cx, cy);
            PreviewParsers.Ingredient ing = r.grid()[i];
            if (ing != null && !ing.options().isEmpty()) {
                ItemStack st = ing.options().get((int) (System.currentTimeMillis() / 1000 % ing.options().size()));
                g.renderItem(st, cx + 1, cy + 1);
                if (overSlot(mouseX, mouseY, cx, cy)) {
                    tip = st;
                }
            }
        }
        int ay = gy + gridH / 2 - 4;
        Icons.iconArrowRight(g, gx + gridW + 5, ay + 2, GuiStyle.ACCENT);
        int rx = gx + gridW + 18;
        int ry = gy + gridH / 2 - 9;
        drawSlot(g, rx, ry);
        if (!r.result().isEmpty()) {
            g.renderItem(r.result(), rx + 1, ry + 1);
            g.renderItemDecorations(this.font, r.result(), rx + 1, ry + 1);
            if (overSlot(mouseX, mouseY, rx, ry)) {
                tip = r.result();
            }
        }
        if (r.note() != null) {
            g.drawString(this.font, r.note(), gx, gy + gridH + 2, GuiStyle.DIM, false);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
        if (!tip.isEmpty()) {
            g.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void renderTag(GuiGraphics g, FRow row, PreviewParsers.Tag t, List<String> issues, int mouseX, int mouseY) {
        int cols = 8;
        int total = t.items().size();
        int gridRows = Math.max(total == 0 ? 0 : 1, (total + cols - 1) / cols);
        int gridH = total == 0 ? 10 : gridRows * 18;
        int refsH = t.refs().isEmpty() ? 0 : 10;
        int missH = t.missing().isEmpty() ? 0 : 10;
        int fH = footerH(issues, 340);
        String title = "# " + row.name + "  (" + total + ")";
        int contentW = cols * 18 + 12;
        if (refsH > 0) {
            contentW = Math.max(contentW, this.font.width("↪ " + String.join(" ", t.refs())) + 12);
        }
        if (missH > 0) {
            contentW = Math.max(contentW, this.font.width("⚠ " + String.join(" ", t.missing())) + 12);
        }
        int w = fitW(contentW, title);
        int hh = 12 + 4 + gridH + refsH + missH + 4 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        ItemStack tip = ItemStack.EMPTY;
        if (total == 0) {
            g.drawString(this.font, I18n.get("panoptic.gui.prev.tag_empty"), bx + 6, top + 3, GuiStyle.DIM, false);
        }
        for (int i = 0; i < total; i++) {
            int cx = bx + 6 + i % cols * 18;
            int cy = top + 4 + i / cols * 18;
            drawSlot(g, cx, cy);
            ItemStack st = t.items().get(i);
            g.renderItem(st, cx + 1, cy + 1);
            if (overSlot(mouseX, mouseY, cx, cy)) {
                tip = st;
            }
        }
        int yy = top + 4 + gridH;
        if (refsH > 0) {
            Icons.iconRefLink(g, bx + 6, yy + 1, 0xFF5FD0D9);
                    g.drawString(this.font, trim(String.join(" ", t.refs()), w - 22), bx + 16, yy, 0xFF5FD0D9, false);
            yy += 10;
        }
        if (missH > 0) {
            Icons.iconWarn(g, bx + 6, yy + 1, 0xFFFF9C8A);
                    g.drawString(this.font, trim(String.join(" ", t.missing()), w - 22), bx + 16, yy, 0xFFFF9C8A, false);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
        if (!tip.isEmpty()) {
            g.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void renderLoot(GuiGraphics g, FRow row, PreviewParsers.Loot l, List<String> issues, int mouseX, int mouseY) {
        int total = l.rows().size();
        int visible = Math.min(total, 12);
        int maxScroll = Math.max(0, total - 12);
        int off = Mth.clamp((int) Math.round(previewScroll), 0, maxScroll);
        int rowH = 18;
        int fH = footerH(issues, 340);
        int[] cols = measCache.computeIfAbsent("loot|" + row.dir + row.name, k -> {
            int nw = 60;
            int pw = 50;
            for (PreviewParsers.LootRow lr : l.rows()) {
                nw = Math.max(nw, this.font.width(lr.name()));
                pw = Math.max(pw, this.font.width(String.format(Locale.ROOT, "%.1f%% ×%d", lr.chance(), lr.weight())));
            }
            return new int[]{nw, pw};
        });
        int nameW = cols[0];
        int pctW = cols[1];
        String title = row.name + "  ·  " + l.pools() + "p";
        int w = fitW(25 + nameW + 10 + pctW + 10, title);
        int hh = 12 + 2 + visible * rowH + 2 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        ItemStack tip = ItemStack.EMPTY;
        int contentBottom = top + 2 + visible * rowH;
        scis(g, bx + 1, top, bx + w - 1, contentBottom);
        int yy = top + 2;
        for (int i = 0; i < visible; i++) {
            PreviewParsers.LootRow lr = l.rows().get(off + i);
            boolean hov = mouseX >= bx + 1 && mouseX <= bx + w - 1 && mouseY >= yy && mouseY < yy + rowH;
            if (hov) {
                g.fill(bx + 1, yy, bx + w - 1, yy + rowH, GuiStyle.ROWHOVER);
            }
            drawSlot(g, bx + 4, yy);
            if (!lr.icon().isEmpty()) {
                g.renderItem(lr.icon(), bx + 5, yy + 1);
                if (hov) {
                    tip = lr.icon();
                }
            }
            g.drawString(this.font, trim(lr.name(), w - 35 - pctW), bx + 25, yy + 1, GuiStyle.TEXT, false);
            String pct = String.format(Locale.ROOT, "%.1f%% ×%d", lr.chance(), lr.weight());
            g.drawString(this.font, pct, bx + w - 8 - this.font.width(pct), yy + 6, GuiStyle.DIM, false);
            yy += rowH;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackTop = top + 2;
            int trackH = visible * rowH;
            g.fill(bx + w - 3, trackTop, bx + w - 1, trackTop + trackH, 0x33FFFFFF);
            int thumbH = Math.max(8, trackH * visible / total);
            int thumbY = trackTop + (trackH - thumbH) * off / maxScroll;
            g.fill(bx + w - 3, thumbY, bx + w - 1, thumbY + thumbH, row.color);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
        if (!tip.isEmpty()) {
            g.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void renderLang(GuiGraphics g, FRow row, PreviewParsers.Lang lang, List<String> issues, int mouseX, int mouseY) {
        int total = lang.entries().size();
        int visible = Math.min(total, 12);
        int maxScroll = Math.max(0, total - 12);
        int off = Mth.clamp((int) Math.round(previewScroll), 0, maxScroll);
        int lineH = 11;
        int fH = footerH(issues, 340);
        int[] cols = measCache.computeIfAbsent("lang|" + row.dir + row.name, k -> {
            int kw = 60;
            int vw = 80;
            for (PreviewParsers.LangEntry e : lang.entries()) {
                kw = Math.max(kw, this.font.width(e.key()));
                vw = Math.max(vw, this.font.width(e.value()));
            }
            return new int[]{Math.min(kw, 200), vw};
        });
        int keyW = cols[0];
        int valW = cols[1];
        String title = "lang  " + row.name + "  (" + total + ")";
        int w = fitW(6 + keyW + 8 + valW + 6, title);
        int hh = 12 + 2 + visible * lineH + 2 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        scis(g, bx + 1, top, bx + w - 1, top + 2 + visible * lineH);
        int yy = top + 2;
        for (int i = 0; i < visible; i++) {
            PreviewParsers.LangEntry e = lang.entries().get(off + i);
            if ((off + i) % 2 == 1) {
                g.fill(bx + 1, yy, bx + w - 1, yy + lineH, 0x10FFFFFF);
            }
            g.drawString(this.font, trim(e.key(), keyW), bx + 6, yy + 2, GuiStyle.DIM, false);
            g.drawString(this.font, trim(e.value(), w - keyW - 22), bx + 6 + keyW + 8, yy + 2, GuiStyle.TEXT, false);
            yy += lineH;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackTop = top + 2;
            int trackH = visible * lineH;
            g.fill(bx + w - 3, trackTop, bx + w - 1, trackTop + trackH, 0x33FFFFFF);
            int thumbH = Math.max(8, trackH * visible / total);
            int thumbY = trackTop + (trackH - thumbH) * off / maxScroll;
            g.fill(bx + w - 3, thumbY, bx + w - 1, thumbY + thumbH, row.color);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
    }

    private void renderModel(GuiGraphics g, FRow row, PreviewParsers.Model m, List<String> issues, int mouseX, int mouseY) {
        List<PreviewParsers.TexRef> texs = m.textures();
        int thumb = 42;
        int cellW = thumb + 6;
        int cellH = thumb + 11;
        int cols = Math.max(1, Math.min(3, texs.size()));
        int gridRows = texs.isEmpty() ? 0 : (texs.size() + cols - 1) / cols;
        int gridH = texs.isEmpty() ? 11 : gridRows * cellH;
        int parentH = m.parent() != null ? 10 : 0;
        int fH = footerH(issues, 340);
        int contentW = cols * cellW + 8;
        for (PreviewParsers.TexRef t : texs) {
            contentW = Math.max(contentW, this.font.width("#" + t.key()) + 6);
        }
        if (m.parent() != null) {
            contentW = Math.max(contentW, this.font.width("↑ " + m.parent()) + 12);
        }
        if (texs.isEmpty()) {
            contentW = Math.max(contentW, this.font.width(I18n.get("panoptic.gui.prev.model_inherit")) + 12);
        }
        String title = "model  " + row.name;
        int w = fitW(Math.max(150, contentW), title);
        int hh = 12 + 4 + gridH + parentH + 4 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        if (texs.isEmpty()) {
            g.drawString(this.font, I18n.get("panoptic.gui.prev.model_inherit"), bx + 6, top + 3, GuiStyle.DIM, false);
        }
        for (int i = 0; i < texs.size(); i++) {
            PreviewParsers.TexRef t = texs.get(i);
            int cx = bx + 4 + i % cols * cellW;
            int cy = top + 4 + i / cols * cellH;
            drawTexThumb(g, cx, cy, thumb, t.texture(), t.exists());
            g.drawString(this.font, trim("#" + t.key(), thumb), cx, cy + thumb + 1, t.exists() ? GuiStyle.DIM : 0xFFFF9C8A, false);
        }
        int yy = top + 4 + gridH;
        if (parentH > 0) {
            g.drawString(this.font, trim("↑ " + m.parent(), w - 12), bx + 6, yy, GuiStyle.DIM, false);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
    }

    private void drawTexThumb(GuiGraphics g, int x, int y, int size, ResourceLocation rl, boolean exists) {
        g.fill(x, y, x + size, y + size, 0xFF1A150D);
        rect(g, x, y, x + size, y + size, 0xFF353B49);
        if (!exists || rl == null) {
            Icons.iconCross(g, x + size / 2 - 3, y + size / 2 - 3, 0xFFFF6B6B);
            return;
        }
        int[] dim = texDims.computeIfAbsent("@" + rl, k -> readPngDims(rl));
        if (dim[0] <= 0 || dim[1] <= 0) {
            return;
        }
        int dw = size - 2;
        int dh = size - 2;
        if (dim[0] >= dim[1]) {
            dh = Math.max(2, (size - 2) * dim[1] / dim[0]);
        } else {
            dw = Math.max(2, (size - 2) * dim[0] / dim[1]);
        }
        int ix = x + 1 + (size - 2 - dw) / 2;
        int iy = y + 1 + (size - 2 - dh) / 2;
        try {
            g.blit(rl, ix, iy, dw, dh, 0.0F, 0.0F, dim[0], dim[1], dim[0], dim[1]);
        } catch (Throwable ignored) {
        }
    }

    private void renderAdvancement(GuiGraphics g, FRow row, PreviewParsers.Advancement a, List<String> issues, int mouseX, int mouseY) {
        boolean noDisplay = a.icon().isEmpty() && a.title().isEmpty() && a.description().isEmpty();
        String titleStr = noDisplay ? row.name : I18n.get(a.title());
        String descStr = noDisplay ? I18n.get("panoptic.gui.prev.no_display") : I18n.get(a.description());
        int parentH = a.parent() != null ? 11 : 0;
        int fH = footerH(issues, 340);
        int contentW = 28 + Math.max(this.font.width(titleStr), this.font.width(descStr)) + 8;
        contentW = Math.max(contentW, this.font.width(I18n.get("panoptic.gui.prev.criteria", a.criteria())) + 12);
        if (a.parent() != null) {
            contentW = Math.max(contentW, this.font.width("↑ " + a.parent()) + 12);
        }
        String title = "advancement  " + row.name;
        int w = fitW(Math.max(180, contentW), title);
        int hh = 12 + 4 + 22 + 12 + parentH + 4 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        drawSlot(g, bx + 5, top + 4);
        if (!a.icon().isEmpty()) {
            g.renderItem(a.icon(), bx + 6, top + 5);
        }
        g.drawString(this.font, trim(titleStr, w - 36), bx + 28, top + 5, 0xFFFFFFFF, false);
        g.drawString(this.font, trim(descStr, w - 36), bx + 28, top + 15, GuiStyle.DIM, false);
        int yy = top + 28;
        g.drawString(this.font, I18n.get("panoptic.gui.prev.criteria", a.criteria()), bx + 6, yy, GuiStyle.MUTED, false);
        if (parentH > 0) {
            g.drawString(this.font, trim("↑ " + a.parent(), w - 12), bx + 6, yy + 11, GuiStyle.DIM, false);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
    }

    private void renderBlockstate(GuiGraphics g, FRow row, PreviewParsers.Blockstate b, List<String> issues, int mouseX, int mouseY) {
        int total = b.entries().size();
        int visible = Math.min(total, 12);
        int maxScroll = Math.max(0, total - 12);
        int off = Mth.clamp((int) Math.round(previewScroll), 0, maxScroll);
        int lineH = 11;
        int fH = footerH(issues, 340);
        int[] cols = measCache.computeIfAbsent("bs|" + row.dir + row.name, k -> {
            int kw = 60;
            int mw = 80;
            for (PreviewParsers.BlockstateEntry e : b.entries()) {
                kw = Math.max(kw, this.font.width(e.variant()));
                mw = Math.max(mw, this.font.width(e.model()));
            }
            return new int[]{Math.min(kw, 220), mw};
        });
        int keyW = cols[0];
        int modelW = cols[1];
        String title = "blockstate  " + row.name + "  (" + total + ")";
        int w = fitW(6 + keyW + 10 + modelW + 6, title);
        int hh = 12 + 2 + Math.max(1, visible) * lineH + 2 + fH;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int bx = panelX;
        scis(g, bx + 1, top, bx + w - 1, top + 2 + visible * lineH);
        int yy = top + 2;
        for (int i = 0; i < visible; i++) {
            PreviewParsers.BlockstateEntry e = b.entries().get(off + i);
            if ((off + i) % 2 == 1) {
                g.fill(bx + 1, yy, bx + w - 1, yy + lineH, 0x10FFFFFF);
            }
            g.drawString(this.font, trim(e.variant(), keyW), bx + 6, yy + 2, GuiStyle.DIM, false);
            Icons.iconArrowRight(g, bx + 6 + keyW + 1, yy + 4, GuiStyle.ACCENT);
            g.drawString(this.font, trim(e.model(), w - 16 - keyW), bx + 6 + keyW + 10, yy + 2, GuiStyle.TEXT, false);
            yy += lineH;
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int trackTop = top + 2;
            int trackH = visible * lineH;
            g.fill(bx + w - 3, trackTop, bx + w - 1, trackTop + trackH, 0x33FFFFFF);
            int thumbH = Math.max(8, trackH * visible / total);
            int thumbY = trackTop + (trackH - thumbH) * off / maxScroll;
            g.fill(bx + w - 3, thumbY, bx + w - 1, thumbY + thumbH, row.color);
        }
        lintFooter(g, bx, panelY + hh, w, issues, mouseX, mouseY);
    }

    private int previewScrollMax(FRow row) {
        if (row == null || "texture".equals(row.kind) || "NBT".equals(row.ext)) {
            return 0;
        }
        if (jsonLike(row) && !Screen.hasAltDown()) {
            Object view = ensureParsed(row).view();
            if (view instanceof PreviewParsers.Loot l) {
                return Math.max(0, l.rows().size() - 12);
            }
            if (view instanceof PreviewParsers.Lang lang) {
                return Math.max(0, lang.entries().size() - 12);
            }
            if (view instanceof PreviewParsers.Blockstate b) {
                return Math.max(0, b.entries().size() - 12);
            }
            if (view instanceof PreviewParsers.Recipe || view instanceof PreviewParsers.Tag
                    || view instanceof PreviewParsers.Model || view instanceof PreviewParsers.Advancement) {
                return 0;
            }
        }
        List<String> lines = previewCache.computeIfAbsent(row.dir + row.name,
                k -> PreviewData.lines(row.file, row.dir, row.name, row.ext));
        return Math.max(0, lines.size() - PREVIEW_LINES);
    }

    private static boolean isTabDown() {
        return InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_KEY_TAB);
    }

    private void renderStructPreview(GuiGraphics g, FRow row, int mouseX, int mouseY) {
        StructPreview sp = structCache.computeIfAbsent(row.dir + row.name, k -> startStruct(row));
        if (sp == null || sp.failed) {
            previewPanel(g, row, PreviewData.nbt(row.file), false, false, List.of(), mouseX, mouseY);
            return;
        }
        int box = 150;
        String title = "NBT  " + row.name;
        int w = fitW(box + 12, title);
        int hh = 12 + 4 + box + 11;
        int top = frame(g, row, w, hh, mouseX, mouseY, title);
        int gx = panelX + (w - box) / 2;
        int gy = top + 3;
        int count = sp.count;
        if (count > 0) {
            scis(g, gx, gy, gx + box, gy + box);
            drawStructure3D(g, sp, count, gx + box / 2, gy + box / 2, box);
            g.disableScissor();
        } else {
            g.drawString(this.font, I18n.get("panoptic.gui.prev.loading_dots"),
                    gx + box / 2 - this.font.width(I18n.get("panoptic.gui.prev.loading_dots")) / 2, gy + box / 2 - 4, GuiStyle.MUTED, false);
        }
        String status = sp.done
                ? sp.sx + "×" + sp.sy + "×" + sp.sz + " · " + count
                : I18n.get("panoptic.gui.prev.loading", count, sp.total);
        g.drawString(this.font, trim(status, w - 10), panelX + 5, gy + box + 2, GuiStyle.MUTED, false);
    }

    private void drawStructure3D(GuiGraphics g, StructPreview sp, int count, int cx, int cy, int box) {
        int maxDim = Math.max(1, Math.max(sp.sx, Math.max(sp.sy, sp.sz)));
        float scale = box * 0.58F / maxDim;
        float spin = (System.currentTimeMillis() % 11000L) / 11000.0F * 360.0F;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 180.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(-sp.sx / 2.0F, -sp.sy / 2.0F, -sp.sz / 2.0F);

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        Lighting.setupForFlatItems();
        MultiBufferSource.BufferSource buf = this.minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher brd = this.minecraft.getBlockRenderer();
        for (int i = 0; i < count; i++) {
            pose.pushPose();
            pose.translate(sp.px[i], sp.py[i], sp.pz[i]);
            brd.renderSingleBlock(sp.states[i], pose, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buf.endBatch();
        Lighting.setupForFlatItems();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        pose.popPose();
    }

    private StructPreview startStruct(FRow row) {
        if (row.file == null) {
            return null;
        }
        StructPreview sp = new StructPreview();
        STRUCT_LOADER.submit(() -> loadStruct(row, sp));
        return sp;
    }

    private void loadStruct(FRow row, StructPreview sp) {
        try (InputStream is = Files.newInputStream(row.file)) {
            CompoundTag tag = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            if (!tag.contains("size", Tag.TAG_LIST) || !tag.contains("blocks", Tag.TAG_LIST)) {
                sp.failed = true;
                return;
            }
            ListTag size = tag.getList("size", Tag.TAG_INT);
            ListTag palette;
            if (tag.contains("palette", Tag.TAG_LIST)) {
                palette = tag.getList("palette", Tag.TAG_COMPOUND);
            } else if (tag.contains("palettes", Tag.TAG_LIST)) {
                ListTag ps = tag.getList("palettes", Tag.TAG_LIST);
                palette = ps.isEmpty() ? new ListTag() : ps.getList(0);
            } else {
                sp.failed = true;
                return;
            }
            BlockState[] states = new BlockState[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
            }
            ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
            int n = blocks.size();
            sp.sx = size.getInt(0);
            sp.sy = size.getInt(1);
            sp.sz = size.getInt(2);
            sp.total = n;
            int[] px = new int[n];
            int[] py = new int[n];
            int[] pz = new int[n];
            BlockState[] bs = new BlockState[n];
            sp.px = px;
            sp.py = py;
            sp.pz = pz;
            sp.states = bs;
            int c = 0;
            for (int i = 0; i < n; i++) {
                CompoundTag b = blocks.getCompound(i);
                int si = b.getInt("state");
                if (si < 0 || si >= states.length) {
                    continue;
                }
                BlockState st = states[si];
                if (st == null || st.isAir() || st.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }
                ListTag pos = b.getList("pos", Tag.TAG_INT);
                px[c] = pos.getInt(0);
                py[c] = pos.getInt(1);
                pz[c] = pos.getInt(2);
                bs[c] = st;
                c++;
                if ((c & 2047) == 0) {
                    sp.count = c;
                }
            }
            sp.count = c;
            sp.done = true;
        } catch (Throwable t) {
            sp.failed = true;
        }
    }

    private static final class StructPreview {
        int sx;
        int sy;
        int sz;
        int total;
        volatile int count;
        volatile boolean done;
        volatile boolean failed;
        int[] px;
        int[] py;
        int[] pz;
        BlockState[] states;
    }

    private ResourceLocation textureRL(FRow row) {
        String p = row.dir + row.name;
        if (!p.startsWith("assets/") || !p.endsWith(".png")) {
            return null;
        }
        String rest = p.substring(7);
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        return ResourceLocation.tryParse(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
    }

    private int[] readPngDims(ResourceLocation rl) {
        try {
            var res = this.minecraft.getResourceManager().getResource(rl);
            if (res.isEmpty()) {
                return new int[]{0, 0};
            }
            try (InputStream is = res.get().open()) {
                byte[] b = is.readNBytes(24);
                if (b.length < 24) {
                    return new int[]{0, 0};
                }
                int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
                int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
                return new int[]{w, h};
            }
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    private String resourceIdOf(FRow row) {
        return resourceIdOfPath(row.dir + row.name);
    }

    private String resourceIdOfPath(String p) {
        String rest;
        if (p.startsWith("assets/")) {
            rest = p.substring(7);
        } else if (p.startsWith("data/")) {
            rest = p.substring(5);
        } else {
            return p;
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? p : rest.substring(0, slash) + ":" + rest.substring(slash + 1);
    }

    private void copyAllFiles() {
        if (selected == null || selected.files.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (ResourceFiles.FileRec f : selected.files) {
            sb.append(f.path()).append('\n');
        }
        this.minecraft.keyboardHandler.setClipboard(sb.toString());
        copied = I18n.get("panoptic.gui.files", selected.files.size());
        copiedUntil = System.currentTimeMillis() + 1200;
        sound(1.5F);
    }

    private void buildKindChips() {
        kindChips.clear();
        nsChips.clear();
        kindCountMap.clear();
        nsCountMap.clear();
        extCountMap.clear();
        for (ResourceFiles.FileRec f : selected.files) {
            kindCountMap.computeIfAbsent(kindOf(f.path()), k -> new int[1])[0]++;
            nsCountMap.computeIfAbsent(namespaceOfPath(f.path()), k -> new int[1])[0]++;
            int dot = f.path().lastIndexOf('.');
            if (dot >= 0) {
                extCountMap.computeIfAbsent(f.path().substring(dot + 1).toLowerCase(Locale.ROOT), k -> new int[1])[0]++;
            }
        }
        String all = I18n.get("panoptic.gui.kind.all");
        kindChips.add(new KindChip(null, all, 0xFFACA188, this.font.width(all) + 10));
        for (Map.Entry<String, int[]> e : kindCountMap.entrySet()) {
            String label = I18n.get("panoptic.gui.kind." + e.getKey()) + " " + e.getValue()[0];
            kindChips.add(new KindChip(e.getKey(), label, kindColor(e.getKey()), this.font.width(label) + 10));
        }
        if (nsCountMap.size() > 1) {
            String allns = I18n.get("panoptic.gui.ns_all");
            nsChips.add(new KindChip(null, allns, 0xFFACA188, this.font.width(allns) + 10));
            for (Map.Entry<String, int[]> e : nsCountMap.entrySet()) {
                String name = e.getKey().isEmpty() ? "—" : e.getKey();
                String label = "@" + name + " " + e.getValue()[0];
                nsChips.add(new KindChip(e.getKey(), label, nsColor(name), this.font.width(label) + 10));
            }
        }
    }

    private static int nsColor(String ns) {
        int h = ns.hashCode();
        int r = 120 + (h & 0x3F);
        int gg = 120 + ((h >> 6) & 0x3F);
        int b = 150 + ((h >> 12) & 0x3F);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private void rebuildFileRows() {
        fileRows.clear();
        parseQuery();
        LinkedHashMap<String, List<ResourceFiles.FileRec>> bySrc = new LinkedHashMap<>();
        filesMatchCount = 0;
        for (ResourceFiles.FileRec f : selected.files) {
            if (fileMatches(f)) {
                bySrc.computeIfAbsent(f.source(), k -> new ArrayList<>()).add(f);
                filesMatchCount++;
            }
        }
        for (Map.Entry<String, List<ResourceFiles.FileRec>> e : bySrc.entrySet()) {
            boolean collapsed = filesCollapsed.contains(e.getKey());
            fileRows.add(FRow.header(e.getKey(), e.getValue().size(), collapsed));
            if (!collapsed) {
                for (ResourceFiles.FileRec f : e.getValue()) {
                    fileRows.add(FRow.file(f));
                }
            }
        }
        filesContentH = 0;
        for (FRow r : fileRows) {
            filesContentH += r.header ? F_HEADER_H : F_ROW_H;
        }
    }

    private void parseQuery() {
        qKinds = null;
        qNss = null;
        qExts = null;
        qPlains.clear();
        qIns.clear();
        for (String tok : filesFilter.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            if (tok.startsWith("kind:")) {
                qKinds = addFacet(qKinds, tok.substring(5));
            } else if (tok.startsWith("ns:")) {
                qNss = addFacet(qNss, tok.substring(3));
            } else if (tok.startsWith("ext:")) {
                qExts = addFacet(qExts, tok.substring(4));
            } else if (tok.startsWith("in:")) {
                if (!tok.substring(3).isEmpty()) {
                    qIns.add(tok.substring(3));
                }
            } else {
                qPlains.add(tok);
            }
        }
    }

    private static Set<String> addFacet(Set<String> set, String v) {
        if (v.isEmpty()) {
            return set;
        }
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(v);
        return set;
    }

    private boolean fileMatches(ResourceFiles.FileRec f) {
        String path = f.path();
        if (filesKind != null && !filesKind.equals(kindOf(path))) {
            return false;
        }
        if (filesNs != null && !filesNs.equals(namespaceOfPath(path))) {
            return false;
        }
        if (qKinds != null && !qKinds.contains(kindOf(path))) {
            return false;
        }
        if (qNss != null && !qNss.contains(namespaceOfPath(path))) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (qExts != null) {
            boolean any = false;
            for (String e : qExts) {
                if (lower.endsWith("." + e)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        for (String p : qPlains) {
            if (!lower.contains(p)) {
                return false;
            }
        }
        for (String in : qIns) {
            if (!fileContains(f, in)) {
                return false;
            }
        }
        return true;
    }

    private static String namespaceOfPath(String p) {
        String rest;
        if (p.startsWith("assets/")) {
            rest = p.substring(7);
        } else if (p.startsWith("data/")) {
            rest = p.substring(5);
        } else {
            return "";
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? "" : rest.substring(0, slash);
    }

    private boolean fileContains(ResourceFiles.FileRec f, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        String c = contentCache.computeIfAbsent(f.path(), k -> {
            String raw = readCapped(f, 262144);
            return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        });
        return c.contains(needle);
    }

    private String readCapped(ResourceFiles.FileRec f, int limit) {
        String e = f.path().toLowerCase(Locale.ROOT);
        if (e.endsWith(".png") || e.endsWith(".ogg") || e.endsWith(".nbt")) {
            return null;
        }
        if (f.file() == null) {
            return null;
        }
        try (InputStream is = Files.newInputStream(f.file())) {
            return new String(is.readNBytes(limit), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private List<ResourceFiles.FileRec> currentFilteredFiles() {
        parseQuery();
        List<ResourceFiles.FileRec> out = new ArrayList<>();
        for (ResourceFiles.FileRec f : selected.files) {
            if (fileMatches(f)) {
                out.add(f);
            }
        }
        return out;
    }

    private List<ResourceFiles.FileRec> copyTargetFiles() {
        if (filesSelected.isEmpty()) {
            return currentFilteredFiles();
        }
        List<ResourceFiles.FileRec> out = new ArrayList<>();
        for (ResourceFiles.FileRec f : selected.files) {
            if (filesSelected.contains(f.path())) {
                out.add(f);
            }
        }
        return out;
    }

    private void selectAllFiltered() {
        for (ResourceFiles.FileRec f : currentFilteredFiles()) {
            filesSelected.add(f.path());
        }
    }

    private void invertSelection() {
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (ResourceFiles.FileRec f : currentFilteredFiles()) {
            if (!filesSelected.contains(f.path())) {
                next.add(f.path());
            }
        }
        filesSelected.clear();
        filesSelected.addAll(next);
        lastFileSel = -1;
    }

    private void copyFiles(int mode) {
        List<ResourceFiles.FileRec> files = copyTargetFiles();
        if (files.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (mode == 3) {
            int n = 0;
            for (ResourceFiles.FileRec f : files) {
                if (n >= 300 || sb.length() > 600000) {
                    sb.append("// …\n");
                    break;
                }
                sb.append("// ").append(f.path()).append('\n');
                String raw = readCapped(f, 600000);
                sb.append(raw == null ? "// [binary]" : raw).append("\n\n");
                n++;
            }
        } else {
            for (ResourceFiles.FileRec f : files) {
                sb.append(mode == 2 ? resourceIdOfPath(f.path()) : f.path()).append('\n');
            }
        }
        this.minecraft.keyboardHandler.setClipboard(sb.toString());
        copied = I18n.get("panoptic.gui.copied_n", files.size());
        copiedUntil = System.currentTimeMillis() + 1400;
        sound(1.5F);
    }

    private void toggleCollapse(String src) {
        if (!filesCollapsed.remove(src)) {
            filesCollapsed.add(src);
        }
        rebuildFileRows();
        sound(1.0F);
    }

    private static String kindOf(String p) {
        if (p.endsWith(".png")) return "texture";
        if (p.endsWith(".ogg") || p.contains("/sounds")) return "sound";
        if (p.endsWith(".nbt") || p.contains("/structures/") || p.contains("/structure/")) return "structure";
        if (p.endsWith(".mcmeta")) return "meta";
        if (p.endsWith(".mcfunction") || p.contains("/functions/")) return "function";
        if (p.contains("/advancement")) return "advancement";
        if (p.contains("/models/")) return "model";
        if (p.contains("/blockstates/")) return "state";
        if (p.contains("/recipe")) return "recipe";
        if (p.contains("/loot_table")) return "loot";
        if (p.contains("/tags/")) return "tag";
        if (p.contains("/lang/")) return "lang";
        if (p.contains("/particles/")) return "particle";
        if (p.contains("/font/")) return "font";
        if (p.contains("/atlases/")) return "atlas";
        if (p.contains("/shaders/") || p.endsWith(".fsh") || p.endsWith(".vsh") || p.endsWith(".glsl")) return "shader";
        if (p.contains("/worldgen/")) return "worldgen";
        if (p.contains("/dimension")) return "dimension";
        return "other";
    }

    private static int kindColor(String k) {
        return switch (k) {
            case "texture" -> 0xFF7BD88F;
            case "model" -> 0xFF6CA6FF;
            case "state" -> 0xFFE0B341;
            case "recipe" -> 0xFFD98C5F;
            case "loot" -> 0xFFB57BE0;
            case "tag" -> 0xFF5FD0D9;
            case "lang" -> 0xFFE06C9C;
            case "sound" -> 0xFFD9A86C;
            case "structure" -> 0xFFB08D57;
            case "advancement" -> 0xFFC0C860;
            case "function" -> 0xFF8FBF6A;
            case "particle" -> 0xFF8AD6E0;
            case "font" -> 0xFFC0A0E0;
            case "atlas" -> 0xFFD0B070;
            case "shader" -> 0xFFE08585;
            case "worldgen" -> 0xFF9AD17A;
            case "dimension" -> 0xFF7AB0E0;
            default -> 0xFFACA188;
        };
    }

    private static final class KindChip {
        final String id;
        final String label;
        final int color;
        final int w;
        int x;
        int y;

        KindChip(String id, String label, int color, int w) {
            this.id = id;
            this.label = label;
            this.color = color;
            this.w = w;
        }
    }

    private void handleFilesClick(double mouseX, double mouseY) {
        filesSearchFocused = false;
        if (SoundFilePreview.barVisible()) {
            int bw = Math.min(280, fRight - fLeft - 40);
            int bx1 = (fLeft + fRight) / 2 - bw / 2;
            int bx2 = bx1 + bw;
            int by1 = soundBarY();
            if (mouseY >= by1 && mouseY <= by1 + 23 && mouseX >= bx1 && mouseX <= bx2) {
                if (mouseX >= bx2 - 33 && mouseX <= bx2 - 20) {
                    SoundFilePreview.togglePause();
                    sound(1.1F);
                    return;
                }
                if (mouseX >= bx2 - 17 && mouseX <= bx2 - 4) {
                    SoundFilePreview.closeBar();
                    sound(0.8F);
                    return;
                }
                int tlx1 = bx1 + 7;
                int tlx2 = bx2 - 40;
                if (mouseX >= tlx1 && mouseX <= tlx2 && mouseY >= by1 + 13) {
                    SoundFilePreview.seek((float) (mouseX - tlx1) / (tlx2 - tlx1));
                    sound(1.2F);
                }
                return;
            }
        }
        if (mouseX < fLeft || mouseX > fRight || mouseY < fTop || mouseY > fBottom) {
            filesOpen = false;
            SoundFilePreview.closeBar();
            sound(0.9F);
            return;
        }
        if (mouseX >= fRight - 18 && mouseX <= fRight - 5 && mouseY >= fTop + 3 && mouseY <= fTop + 17) {
            filesOpen = false;
            SoundFilePreview.closeBar();
            sound(0.9F);
            return;
        }
        if (mouseX >= fRight - 32 && mouseX <= fRight - 19 && mouseY >= fTop + 3 && mouseY <= fTop + 17) {
            copyAllFiles();
            return;
        }
        int sy = fTop + 22;
        if (!filesFilter.isEmpty() && mouseX >= fSearchRight - 13 && mouseX <= fSearchRight - 2 && mouseY >= sy && mouseY <= sy + 14) {
            filesFilter = "";
            filesCaret = 0;
            filesSearchFocused = true;
            rebuildFileRows();
            sound(0.9F);
            return;
        }
        if (mouseX >= fSearchLeft && mouseX <= fSearchRight && mouseY >= sy && mouseY <= sy + 14) {
            filesSearchFocused = true;
            placeCaret((int) mouseX);
            return;
        }
        if (!suggestHidden && !suggestions.isEmpty() && mouseX >= sugX && mouseX <= sugX + sugW
                && mouseY >= sugY && mouseY < sugY + suggestions.size() * sugRowH + 2) {
            int idx = (int) ((mouseY - sugY - 1) / sugRowH);
            if (idx >= 0 && idx < suggestions.size()) {
                acceptSuggestion(suggestions.get(idx));
            }
            return;
        }
        for (KindChip chip : kindChips) {
            if (mouseX >= chip.x && mouseX <= chip.x + chip.w && mouseY >= chip.y && mouseY <= chip.y + 12) {
                filesKind = chip.id;
                rebuildFileRows();
                sound(1.0F);
                return;
            }
        }
        for (KindChip chip : nsChips) {
            if (mouseX >= chip.x && mouseX <= chip.x + chip.w && mouseY >= chip.y && mouseY <= chip.y + 12) {
                filesNs = chip.id;
                rebuildFileRows();
                sound(1.0F);
                return;
            }
        }
        if (mouseY < fCTop || mouseY > fCBottom) {
            return;
        }
        int y = fCTop - (int) filesScroll;
        for (int i = 0; i < fileRows.size(); i++) {
            FRow row = fileRows.get(i);
            int rh = row.header ? F_HEADER_H : F_ROW_H;
            if (mouseY >= y && mouseY < y + rh) {
                if (row.header) {
                    toggleCollapse(row.src);
                } else if (mouseX >= fRight - 15) {
                    copy(row.dir + row.name);
                } else {
                    if (row.name != null && row.name.endsWith(".ogg")
                            && SoundFilePreview.play(row.dir + row.name)) {
                        sound(1.2F);
                    }
                    toggleFileSelect(i);
                    fileDragging = true;
                    fileDragAdd = filesSelected.contains(row.dir + row.name);
                    fileDragLast = i;
                }
                return;
            }
            y += rh;
        }
    }

    private void handleFilesMiddleClick(double mouseX, double mouseY) {
        if (mouseY < fCTop || mouseY > fCBottom) {
            return;
        }
        int y = fCTop - (int) filesScroll;
        for (FRow row : fileRows) {
            int rh = row.header ? F_HEADER_H : F_ROW_H;
            if (mouseY >= y && mouseY < y + rh) {
                if (!row.header && row.name != null && row.name.endsWith(".png")) {
                    boolean ok = row.file != null && PngClipboard.copy(row.file);
                    notice = I18n.get(ok ? "panoptic.gui.png_copied" : "panoptic.gui.png_failed");
                    noticeUntil = System.currentTimeMillis() + 2000;
                    sound(ok ? 1.4F : 0.7F);
                }
                return;
            }
            y += rh;
        }
    }

    private void handleFilesRightClick(double mouseX, double mouseY) {
        if (mouseY < fCTop || mouseY > fCBottom) {
            return;
        }
        int y = fCTop - (int) filesScroll;
        for (FRow row : fileRows) {
            int rh = row.header ? F_HEADER_H : F_ROW_H;
            if (mouseY >= y && mouseY < y + rh) {
                if (!row.header) {
                    copy(resourceIdOf(row));
                }
                return;
            }
            y += rh;
        }
    }

    private LivingEntity livingPreview(InspectEntry e) {
        if (e.typeEnum() != InspectType.ENTITY || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        if (!e.previewTried) {
            e.previewTried = true;
            try {
                ResourceLocation id = ResourceLocation.tryParse(e.id);
                EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
                Entity created = type == null ? null : type.create(this.minecraft.level);
                e.previewEntity = created;
            } catch (Throwable ignored) {
            }
        }
        return e.previewEntity instanceof LivingEntity le ? le : null;
    }

    private void scrollbar(GuiGraphics g, int x, int top, int bottom, int content, int scroll) {
        int trackH = bottom - top;
        if (content <= trackH) {
            return;
        }
        int max = content - trackH;
        g.fill(x, top, x + 4, bottom, 0x40000000);
        int thumbH = Math.max(20, trackH * trackH / content);
        int thumbY = top + (trackH - thumbH) * scroll / max;
        g.fill(x, thumbY, x + 4, thumbY + thumbH, GuiStyle.T(0xFF8C6C33));
    }

    private void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("…"))) + "…";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        mouseX *= uiK;
        mouseY *= uiK;
        if (!filesOpen && mouseY >= chipsY - 2 && mouseY <= chipsY + 16 && chipMaxScroll() > 0
                && mouseX >= panelLeft && mouseX <= panelRight) {
            chipScrollTarget = Mth.clamp(chipScrollTarget - delta * 48, 0, chipMaxScroll());
            return true;
        }

        int d = (int) (delta * ROW_H * 3);
        if (filesOpen) {
            if (previewRow != null) {
                int max = previewScrollMax(previewRow);
                if (max > 0) {
                    previewTarget = Mth.clamp(previewTarget - delta * 3, 0, max);
                    return true;
                }
            }
            int max = Math.max(0, filesContentH - (fCBottom - fCTop));
            filesTarget = Mth.clamp(filesTarget - d, 0, max);
            lastFilesListScroll = System.currentTimeMillis();
            return true;
        }
        if (modOpen && mouseX >= ddLeft && mouseX <= ddRight && mouseY >= ddTop && mouseY <= ddBottom) {
            int content = filteredModRows().size() * DD_ROW_H;
            int max = Math.max(0, content - (ddBottom - (ddTop + DD_HEADER_H + 14)));
            modScrollTarget = Mth.clamp(modScrollTarget - d, 0, max);
            return true;
        }
        if (mouseX < leftRight && mouseY >= contentTop && mouseY <= contentBottom) {
            int max = Math.max(0, list.size() * LIST_ROW_H - (contentBottom - contentTop));
            leftTarget = Mth.clamp(leftTarget - d, 0, max);
            return true;
        }
        if (mouseX >= rightLeft && mouseY >= detailTop && mouseY <= contentBottom) {
            int max = Math.max(0, detail.size() * ROW_H - (contentBottom - detailTop));
            rightTarget = Mth.clamp(rightTarget - d, 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, 0, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX *= uiK;
        mouseY *= uiK;

        if (filesOpen) {
            if (button == 0) {
                handleFilesClick(mouseX, mouseY);
            } else if (button == 1) {
                handleFilesRightClick(mouseX, mouseY);
            } else if (button == 2) {
                handleFilesMiddleClick(mouseX, mouseY);
            }
            return true;
        }
        if (inDropdown(mouseX, mouseY)) {
            if (button == 0) {
                handleModDropdownClick(mouseX, mouseY);
            }
            return true;
        }
        if (button == 0) {
            int tb = topButtonAt(mouseX, mouseY);
            if (tb != 0) {
                handleTopButton(tb);
                return true;
            }
            if (mouseX >= searchBoxX1 && mouseX <= searchBoxX2
                    && mouseY >= searchY - 2 && mouseY <= searchY + 15) {
                setFocused(search);
                search.setFocused(true);
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (modOpen) {
            modOpen = false;
            return true;
        }

        if (button == 2) {
            if (autoScroll != 0) {
                autoScroll = 0;
                return true;
            }
            int pane = 0;
            if (mouseY >= contentTop && mouseY <= contentBottom) {
                if (mouseX >= leftLeft - 2 && mouseX <= leftRight + 2) {
                    pane = 1;
                } else if (mouseX >= rightLeft - 2 && mouseX <= rightRight + 2) {
                    pane = 2;
                }
            }
            if (pane != 0) {
                autoScroll = pane;
                autoAnchorX = (int) mouseX;
                autoAnchorY = (int) mouseY;
                autoPressY = (int) mouseY;
                autoPressTime = System.currentTimeMillis();
                return true;
            }
            return false;
        }
        if (autoScroll != 0) {
            autoScroll = 0;
            return true;
        }
        if (button != 0) return false;

        if (!marked.isEmpty() && mouseX >= leftLeft && mouseX <= leftRight
                && mouseY >= contentBottom - 16 && mouseY <= contentBottom) {
            deleteMarked();
            return true;
        }

        int leftContent = list.size() * LIST_ROW_H;
        if (leftContent > contentBottom - contentTop && mouseX >= leftRight - 1 && mouseX <= leftRight + 4
                && mouseY >= contentTop && mouseY <= contentBottom) {
            beginDrag(1, (int) mouseY);
            return true;
        }
        int rightContent = detail.size() * ROW_H;
        if (selected != null && rightContent > contentBottom - detailTop && mouseX >= rightRight - 1 && mouseX <= rightRight + 4
                && mouseY >= detailTop && mouseY <= contentBottom) {
            beginDrag(2, (int) mouseY);
            return true;
        }

        if (mouseY >= chipsY && mouseY <= chipsY + 14) {
            for (Chip c : chips()) {
                if (mouseX >= c.x && mouseX <= c.x + c.w) {
                    typeFilter = c.type;
                    InspectPrefs.setLastTab(c.type);
                    leftTarget = 0;
                    leftScroll = 0;
                    listDirty = true;
                    sound(1.0F);
                    return true;
                }
            }
        }

        if (selected != null && mouseX >= delLeft && mouseX <= delRight
                && mouseY >= delTop && mouseY <= delBottom) {
            InspectStore.remove(selected);
            selected = null;
            listDirty = true;
            detailDirty = true;
            sound(0.7F);
            return true;
        }

        if (mouseX < leftRight && mouseY >= contentTop && mouseY <= contentBottom) {
            int index = (int) ((mouseY - contentTop + leftScroll) / LIST_ROW_H);
            if (index >= 0 && index < list.size()) {
                InspectEntry clicked = list.get(index);
                if (mouseX >= leftRight - 14 && mouseX <= leftRight - 4) {
                    if (hasShiftDown() && lastMarkIndex >= 0) {
                        markRange(lastMarkIndex, index);
                    } else {
                        if (!marked.add(clicked)) {
                            marked.remove(clicked);
                        }
                        lastMarkIndex = index;
                        dragMarking = true;
                        dragMarkAdd = marked.contains(clicked);
                        dragMarkLast = index;
                    }
                    sound(1.2F);
                } else {
                    dragMarkAnchor = index;
                    if (clicked != selected) {
                        selected = clicked;
                        rightScroll = 0;
                        rightTarget = 0;
                        detailDirty = true;
                        InspectPrefs.setLastSelected(clicked.capturedAt);
                        sound(1.4F);
                    }
                }
            }
            return true;
        }

        if (selected != null && mouseX >= rightLeft && mouseX <= rightRight
                && mouseY >= detailTop && mouseY <= contentBottom) {
            int index = (int) ((mouseY - detailTop + rightScroll) / ROW_H);
            if (index >= 0 && index < detail.size()) {
                Row row = detail.get(index);
                switch (row.kind) {
                    case FIELD -> {
                        String label0 = I18n.get(row.field.label) + ": ";
                        int vx0 = rightLeft + 6 + this.font.width(label0);
                        boolean overflow = row.field.value != null
                                && this.font.width(row.field.value) > rightRight - 24 - vx0;
                        if (overflow && mouseX >= rightRight - 27 && mouseX <= rightRight - 15) {
                            if (!expandedProps.add(row.field.label)) {
                                expandedProps.remove(row.field.label);
                            }
                            detailDirty = true;
                            sound(1.1F);
                        } else if (row.field.copyable) {
                            copy(row.field.value);
                        }
                    }
                    case WRAP -> copy(row.value);
                    case LANG_TOGGLE -> {
                        selected.langsExpanded = !selected.langsExpanded;
                        detailDirty = true;
                        sound(1.1F);
                    }
                    case LANG -> copy(row.value);
                    case FILES_TOGGLE -> openFiles();
                }
            }
            return true;
        }
        return false;
    }

    private void beginDrag(int which, int mouseY) {
        dragging = which;
        int top = which == 1 ? contentTop : detailTop;
        int content = which == 1 ? list.size() * LIST_ROW_H : detail.size() * ROW_H;
        double scroll = which == 1 ? leftScroll : rightScroll;
        int trackH = contentBottom - top;
        int max = content - trackH;
        int thumbH = Math.max(20, trackH * trackH / content);
        int thumbY = top + (max > 0 ? (int) ((trackH - thumbH) * scroll / max) : 0);
        if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
            dragOffset = mouseY - thumbY;
        } else {
            dragOffset = thumbH / 2;
            dragTo(mouseY);
        }
    }

    private void dragTo(int mouseY) {
        int top = dragging == 1 ? contentTop : detailTop;
        int content = dragging == 1 ? list.size() * LIST_ROW_H : detail.size() * ROW_H;
        int trackH = contentBottom - top;
        int max = content - trackH;
        if (max <= 0) {
            return;
        }
        int thumbH = Math.max(20, trackH * trackH / content);
        int denom = trackH - thumbH;
        if (denom <= 0) {
            return;
        }
        double v = Mth.clamp((double) (mouseY - dragOffset - top) * max / denom, 0, max);
        if (dragging == 1) {
            leftScroll = v;
            leftTarget = v;
        } else {
            rightScroll = v;
            rightTarget = v;
        }
    }

    private void deleteMarked() {
        if (marked.isEmpty()) {
            return;
        }
        if (selected != null && marked.contains(selected)) {
            selected = null;
        }
        InspectStore.removeAll(marked);
        marked.clear();
        lastMarkIndex = -1;
        listDirty = true;
        detailDirty = true;
        sound(0.7F);
    }

    private void markRange(int a, int b) {
        int lo = Math.max(0, Math.min(a, b));
        int hi = Math.min(list.size() - 1, Math.max(a, b));
        for (int i = lo; i <= hi; i++) {
            marked.add(list.get(i));
        }
        lastMarkIndex = b;
    }

    private void handleFilesKey(int keyCode, int scanCode) {
        if (ModBinds.matchesNow(ModBinds.Bind.COPY_PATHS, keyCode)) {
            copyFiles(1);
        } else if (ModBinds.matchesNow(ModBinds.Bind.COPY_IDS, keyCode)) {
            copyFiles(2);
        } else if (ModBinds.matchesNow(ModBinds.Bind.COPY_CODE, keyCode)) {
            copyFiles(3);
        } else if (ModBinds.matchesNow(ModBinds.Bind.SELECT_ALL, keyCode)) {
            selectAllFiltered();
            sound(1.2F);
        } else if (ModBinds.matchesNow(ModBinds.Bind.INVERT_SEL, keyCode)) {
            invertSelection();
            sound(1.2F);
        } else if (ModBinds.matchesNow(ModBinds.Bind.CLEAR_SEL, keyCode)) {
            filesSelected.clear();
            lastFileSel = -1;
            sound(0.9F);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX *= uiK;
        mouseY *= uiK;
        dragX *= uiK;
        dragY *= uiK;

        if (filesOpen) {
            if (fileDragging && button == 0) {
                int my = Mth.clamp((int) mouseY, fCTop, fCBottom - 1);
                fileDragTo(fileRowAt(my));
            }
            return true;
        }
        if (dragging != 0) {
            dragTo((int) mouseY);
            return true;
        }
        if ((dragMarking || dragMarkAnchor >= 0) && mouseX < leftRight
                && mouseY >= contentTop && mouseY <= contentBottom) {
            int index = (int) ((mouseY - contentTop + leftScroll) / LIST_ROW_H);
            index = Mth.clamp(index, 0, Math.max(0, list.size() - 1));
            if (!dragMarking && index != dragMarkAnchor) {
                dragMarking = true;
                dragMarkLast = dragMarkAnchor;
                if (dragMarkAnchor >= 0 && dragMarkAnchor < list.size()) {
                    InspectEntry a = list.get(dragMarkAnchor);
                    dragMarkAdd = !marked.contains(a);
                    if (dragMarkAdd) {
                        marked.add(a);
                    } else {
                        marked.remove(a);
                    }
                } else {
                    dragMarkAdd = true;
                }
            }
            if (dragMarking && index != dragMarkLast && dragMarkLast >= 0) {
                int lo = Math.min(dragMarkLast, index);
                int hi = Math.max(dragMarkLast, index);
                for (int i = lo; i <= hi && i < list.size(); i++) {
                    if (dragMarkAdd) {
                        marked.add(list.get(i));
                    } else {
                        marked.remove(list.get(i));
                    }
                }
                dragMarkLast = index;
                lastMarkIndex = index;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX *= uiK;
        mouseY *= uiK;

        if (fileDragging) {
            fileDragging = false;
            fileDragLast = -1;
            return true;
        }
        if (button == 2 && autoScroll != 0) {
            boolean moved = Math.abs((int) mouseY - autoPressY) > 4;
            boolean held = System.currentTimeMillis() - autoPressTime > 300;
            if (moved || held) {
                autoScroll = 0;
            }
            return true;
        }
        if (dragging != 0) {
            dragging = 0;
            return true;
        }
        if (dragMarking || dragMarkAnchor >= 0) {
            dragMarking = false;
            dragMarkAnchor = -1;
            dragMarkLast = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (filesOpen) {
            if (!filesSearchFocused && ModBinds.matchesNow(ModBinds.Bind.SEARCH, keyCode)) {
                filesSearchFocused = true;
                sound(1.2F);
                return true;
            }
            if (filesSearchFocused) {
                computeSuggestions();
                if (!suggestHidden && !suggestions.isEmpty()) {
                    switch (keyCode) {
                        case 264 -> {
                            suggestSel = (Math.max(0, suggestSel) + 1) % suggestions.size();
                            return true;
                        }
                        case 265 -> {
                            suggestSel = (Math.max(0, suggestSel) - 1 + suggestions.size()) % suggestions.size();
                            return true;
                        }
                        case 257, 335, 258 -> {
                            acceptSuggestion(suggestions.get(Math.max(0, suggestSel)));
                            return true;
                        }
                        case 256 -> {
                            suggestHidden = true;
                            sound(0.9F);
                            return true;
                        }
                        default -> {
                        }
                    }
                }
            }
            if (keyCode == 256) {
                if (filesSearchFocused) {
                    filesSearchFocused = false;
                } else {
                    filesOpen = false;
                    SoundFilePreview.closeBar();
                }
                sound(0.9F);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.SELECT_ALL, keyCode)
                    || ModBinds.matchesNow(ModBinds.Bind.INVERT_SEL, keyCode)
                    || ModBinds.matchesNow(ModBinds.Bind.CLEAR_SEL, keyCode)
                    || ModBinds.matchesNow(ModBinds.Bind.COPY_PATHS, keyCode)
                    || ModBinds.matchesNow(ModBinds.Bind.COPY_IDS, keyCode)
                    || ModBinds.matchesNow(ModBinds.Bind.COPY_CODE, keyCode)) {
                handleFilesKey(keyCode, scanCode);
                return true;
            }
            if (filesSearchFocused) {
                TextOps.Res r = TextOps.key(filesFilter, filesCaret, filesSel, keyCode, modifiers, 256);
                if (r.handled) {
                    filesFilter = r.text;
                    filesCaret = r.caret;
                    filesSel = r.sel;
                    if (r.changed) {
                        rebuildFileRows();
                    }
                    return true;
                }
            }
            return true;
        }
        if (modOpen) {
            if (keyCode == 256) {
                if (!modSearch.isEmpty()) {
                    modSearch = "";
                    modSearchCaret = 0;
                    modSearchSel = -1;
                } else {
                    modOpen = false;
                }
                return true;
            }
            TextOps.Res r = TextOps.key(modSearch, modSearchCaret, modSearchSel, keyCode, modifiers, 64);
            if (r.handled) {
                modSearch = r.text;
                modSearchCaret = r.caret;
                modSearchSel = r.sel;
                if (r.changed) {
                    modScrollTarget = 0;
                    modScroll = 0;
                }
                return true;
            }
        }
        if (search != null && search.isFocused()) {
            if (keyCode == 256 || keyCode == 257 || keyCode == 335) {
                search.setFocused(false);
                setFocused(null);
                sound(0.9F);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (ModBinds.matchesNow(ModBinds.Bind.SEARCH, keyCode) && search != null && !searchHidden) {
            setFocused(search);
            search.setFocused(true);
            sound(1.2F);
            return true;
        }
        if (ModBinds.matchesNow(ModBinds.Bind.MARK_ALL, keyCode)) {
            marked.addAll(list);
            lastMarkIndex = list.isEmpty() ? -1 : list.size() - 1;
            sound(1.2F);
            return true;
        }
        if (ModBinds.matchesNow(ModBinds.Bind.MARK_CLEAR, keyCode)) {
            marked.clear();
            lastMarkIndex = -1;
            sound(0.9F);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modOpen) {
            TextOps.Res r = TextOps.type(modSearch, modSearchCaret, modSearchSel, codePoint, 64);
            if (r.handled) {
                modSearch = r.text;
                modSearchCaret = r.caret;
                modSearchSel = r.sel;
                modScrollTarget = 0;
                modScroll = 0;
            }
            return true;
        }
        if (filesOpen) {
            if (filesSearchFocused) {
                TextOps.Res r = TextOps.type(filesFilter, filesCaret, filesSel, codePoint, 256);
                if (r.handled) {
                    filesFilter = r.text;
                    filesCaret = r.caret;
                    filesSel = r.sel;
                    if (r.changed) {
                        rebuildFileRows();
                    }
                }
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void copy(String value) {
        if (value == null || value.isEmpty()) return;
        this.minecraft.keyboardHandler.setClipboard(value);
        copied = value.length() > 40 ? value.substring(0, 40) + "…" : value;
        copiedUntil = System.currentTimeMillis() + 1200;
        sound(1.6F);
    }

    private void copyAll() {
        if (selected == null) return;
        StringBuilder sb = new StringBuilder(selected.title).append('\n');
        for (InspectField f : selected.fields) {
            sb.append(I18n.get(f.label)).append(": ").append(f.value).append('\n');
        }
        this.minecraft.keyboardHandler.setClipboard(sb.toString());
        copied = I18n.get("panoptic.gui.whole_entry");
        copiedUntil = System.currentTimeMillis() + 1200;
        sound(1.5F);
    }

    private void openScanConfirm() {
        if (scanJob != null) {
            return;
        }
        String category = typeFilter != null ? I18n.get(typeFilter.labelKey()) : I18n.get("panoptic.gui.all");
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    this.minecraft.setScreen(this);
                    if (confirmed) {
                        startScan();
                    }
                },
                Component.translatable("panoptic.gui.scan_confirm_title", category),
                Component.translatable("panoptic.gui.scan_confirm_msg")));
    }

    private void startScan() {
        if (scanJob != null) {
            return;
        }
        List<InspectType> types = typeFilter != null
                ? List.of(typeFilter)
                : Arrays.asList(InspectType.values());

        RegistryAccess clientAccess = this.minecraft.level != null ? this.minecraft.level.registryAccess() : null;
        MinecraftServer integrated = this.minecraft.getSingleplayerServer();
        RegistryAccess serverAccess = integrated != null ? integrated.registryAccess() : null;

        List<Supplier<InspectEntry>> builders = new ArrayList<>();
        boolean structureViaPacket = false;
        for (InspectType t : types) {
            switch (t) {
                case BIOME -> builders.addAll(Inspectors.scanUnits(t, clientAccess));
                case STRUCTURE -> {
                    if (serverAccess != null) {
                        builders.addAll(Inspectors.scanUnits(t, serverAccess));
                    } else {
                        structureViaPacket = true;
                    }
                }
                default -> builders.addAll(Inspectors.scanUnits(t, null));
            }
        }
        if (structureViaPacket) {
            ClientPlayNetworking.send(new AllStructuresPacket());
        }
        scanJob = new ScanJob(builders);
        sound(0.9F);
    }

    private void sound(float pitch) {
        if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    private enum Kind { FIELD, LANG_TOGGLE, LANG, FILES_TOGGLE, WRAP }

    private static final class FRow {
        boolean header;
        boolean collapsed;
        String text;
        String src;
        int count;
        String ext;
        String kind;
        int color;
        String dir;
        String name;
        Path file;

        static FRow header(String source, int count, boolean collapsed) {
            FRow r = new FRow();
            r.header = true;
            r.text = source;
            r.src = source;
            r.count = count;
            r.collapsed = collapsed;
            return r;
        }

        static FRow file(ResourceFiles.FileRec rec) {
            FRow r = new FRow();
            String path = rec.path();
            r.text = path;
            r.src = rec.source();
            r.file = rec.file();
            int slash = path.lastIndexOf('/');
            r.dir = slash < 0 ? "" : path.substring(0, slash + 1);
            r.name = slash < 0 ? path : path.substring(slash + 1);
            int dot = r.name.lastIndexOf('.');
            r.ext = dot < 0 ? "?" : r.name.substring(dot + 1).toUpperCase(Locale.ROOT);
            r.kind = kindOf(path);
            r.color = kindColor(r.kind);
            return r;
        }
    }

    private static final class ModRow {
        final String namespace;
        final String display;
        final int count;

        ModRow(String namespace, String display, int count) {
            this.namespace = namespace;
            this.display = display;
            this.count = count;
        }
    }

    private static final class Chip {
        final InspectType type;
        final String label;
        final int x;
        final int w;

        Chip(InspectType type, String label, int x, int w) {
            this.type = type;
            this.label = label;
            this.x = x;
            this.w = w;
        }
    }

    private static final class Row {
        Kind kind;
        InspectField field;
        String langCode;
        String value;
        int count;

        static Row field(InspectField f) {
            Row r = new Row();
            r.kind = Kind.FIELD;
            r.field = f;
            return r;
        }

        static Row wrap(String value) {
            Row r = new Row();
            r.kind = Kind.WRAP;
            r.value = value;
            return r;
        }

        static Row filesToggle(int count) {
            Row r = new Row();
            r.kind = Kind.FILES_TOGGLE;
            r.count = count;
            return r;
        }

        static Row langToggle(int count) {
            Row r = new Row();
            r.kind = Kind.LANG_TOGGLE;
            r.count = count;
            return r;
        }

        static Row lang(String code, String value) {
            Row r = new Row();
            r.kind = Kind.LANG;
            r.langCode = code;
            r.value = value;
            return r;
        }
    }
}