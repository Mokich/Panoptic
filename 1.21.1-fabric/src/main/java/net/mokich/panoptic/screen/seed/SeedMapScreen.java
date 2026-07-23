package net.mokich.panoptic.screen.seed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.TextOps;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.data.seed.ClientWorldgen;
import net.mokich.panoptic.data.seed.SeedMap;
import net.mokich.panoptic.api.util.Disc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeedMapScreen extends Screen implements TextTyping {
    @Override
    public boolean gmtTyping() {
        return seedFocused;
    }
    private static final int MARGIN = 14;
    private static final int TOP_H = 40;
    private static final int BOT_H = 17;
    private static final int MAP_BG = 0xFF15120C;

    private final Screen parent;
    private boolean helpHover;
    private final TileLayer tileLayer = new TileLayer();
    private SeedMap map;
    private double camX, camZ;
    private double zoom = 1.0;
    private double zoomTarget = 1.0;
    private double zoomAnchorX = -1;
    private double zoomAnchorY = -1;
    private long animNs;
    private double animDt;
    private String seedText = "";
    private int seedCaret;
    private volatile int generation;
    private ExecutorService pool;

    private int mLeft, mTop, mRight, mBottom;
    private boolean dragging;
    private boolean moved;
    private double pressX, pressY;
    private double lastDragX, lastDragY;
    private long copiedUntil;
    private String notice;
    private volatile Holder<Biome> hoverBiome;
    private long hoverReqKey = Long.MIN_VALUE;

    private final StructWorker structs = new StructWorker(() -> generation);
    private final Random slimeRnd = new Random();

    private int okX1, okX2, wX1, wX2, fldX1, fldX2, seedY;
    private int seedSel = -1;
    private int searchTextSel = -1;
    private int searchCat;
    private ResourceKey<Level> mapDim = Level.OVERWORLD;
    private final List<ResourceKey<Level>> dims = new ArrayList<>();
    private boolean dimListOpen;
    private int dimChipX1, dimChipY1, dimChipX2, dimChipY2;
    private int dimScroll;
    private double dimScrollF;
    private int dimRowsVis;
    private int dimListW;
    private ExecutorService structPool;
    private boolean searchOpen;
    private String searchText = "";
    private int searchCaret;
    private int searchSel;
    private List<SearchItem> allSearchItems;
    private final List<ListRow> sugg = new ArrayList<>();
    private final HashSet<String> expandedMods = new HashSet<>();
    private SearchItem detailItem;
    private List<String> detailInfo = List.of();
    private List<String> detailLines = List.of();

    private record ListRow(SearchItem item, String ns, String nsName, int count, boolean nsHidden) {
    }
    private void toggleFilter(ResourceLocation id) {
        if (!SeedPrefs.FILTERS.remove(id)) {
            SeedPrefs.FILTERS.add(id);
        }
        SeedPrefs.saveFilters();
        if (SeedPrefs.updateEff()) {
            invalidateStructs();
        }
        sound(1.2F);
    }
    private volatile boolean pendingFoundDone;
    private volatile BlockPos pendingFound;
    private volatile String pendingFoundName;
    private long worldgenRetryAt;
    private BlockPos foundPos;
    private long foundUntil;
    private int schX1, schY1, schX2, schY2;
    private int fchX1, fchY1, fchX2, fchY2;
    private int panX1, panY1, panX2, panY2;
    private int searchScroll;
    private double searchScrollF;
    private boolean seedFocused;
    private boolean histOpen;
    private int histScroll;
    private double histScrollF;
    private int histRows;
    private int histY1, histY2;
    private int hbX1, hbX2;
    private final List<String> histView = new ArrayList<>();
    private void invalidateStructs() {
        structs.invalidate();
    }

    private record SearchItem(boolean isBiome, ResourceLocation id, String name, Object holder) {
    }

    private void toggleHidden(SearchItem it) {
        if (it.isBiome()) {
            return;
        }
        if (SeedPrefs.HIDDEN.remove(it.id())) {
            SeedPrefs.SHOWN.add(it.id());
        } else {
            SeedPrefs.HIDDEN.add(it.id());
            SeedPrefs.SHOWN.remove(it.id());
        }
        SeedPrefs.saveHidden();
        SeedPrefs.saveShown();
        if (SeedPrefs.updateEff()) {
            invalidateStructs();
        }
        sound(1.0F);
    }

    public SeedMapScreen(Screen parent) {
        super(Component.translatable("panoptic.seed.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (pool == null) {
            int n = Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 2, 5);
            pool = Executors.newFixedThreadPool(n, r -> {
                Thread t = new Thread(r, "gmt-seedmap");
                t.setDaemon(true);
                return t;
            });
        }
        if (structPool == null) {
            structPool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "gmt-seedmap-struct");
                t.setDaemon(true);
                return t;
            });
        }
        SeedPrefs.loadAll();
        if (dims.isEmpty()) {
            IntegratedServer server = this.minecraft.getSingleplayerServer();
            if (server != null) {
                for (ResourceKey<Level> k : server.levelKeys()) {
                    ServerLevel l = server.getLevel(k);
                    if (l != null && l.getChunkSource().getGenerator() != null
                            && l.getChunkSource().getGenerator().getBiomeSource() != null) {
                        dims.add(k);
                    }
                }
            } else if (this.minecraft.player != null) {
                List<ResourceKey<Level>> known =
                        new ArrayList<>(this.minecraft.player.connection.levels());
                known.sort(Comparator
                        .<ResourceKey<Level>>comparingInt(k -> k == Level.OVERWORLD ? 0
                                : k == Level.NETHER ? 1 : k == Level.END ? 2 : 3)
                        .thenComparing(k -> k.location().toString()));
                dims.addAll(known);
            }
            if (dims.isEmpty()) {
                dims.add(Level.OVERWORLD);
            }
        }
        buildStructHome();
        if (map == null) {
            if (this.minecraft.player != null && dims.contains(this.minecraft.player.level().dimension())) {
                mapDim = this.minecraft.player.level().dimension();
            } else if (!dims.contains(mapDim)) {
                mapDim = dims.get(0);
            }
            long s = SeedMap.worldSeed();
            seedText = String.valueOf(s);
            seedCaret = seedText.length();
            rebuild(s);
            if (map != null && map.currentWorld && this.minecraft.player != null
                    && this.minecraft.player.level().dimension() == map.dim) {
                camX = this.minecraft.player.getX();
                camZ = this.minecraft.player.getZ();
            }
        }
    }

    private void rebuild(long seed) {
        generation++;
        if (map != null) {
            map.closed = true;
        }
        tileLayer.invalidate();
        structs.reset();
        map = SeedMap.create(seed, mapDim);
        if (map != null) {
            map.hiddenFilter = SeedPrefs::structHidden;
        }
    }

    private void tickAnim() {
        long now = System.nanoTime();
        animDt = animNs == 0 ? 0 : Math.min((now - animNs) / 1.0E9, 0.1);
        animNs = now;
        if (zoom != zoomTarget) {
            double k = animDt <= 0 ? 1.0 : 1.0 - Math.exp(-animDt * 14.0);
            double nz = zoom + (zoomTarget - zoom) * k;
            if (Math.abs(nz - zoomTarget) < zoomTarget * 0.002) {
                nz = zoomTarget;
            }
            double ax = zoomAnchorX < 0 ? mcx() : zoomAnchorX;
            double ay = zoomAnchorY < 0 ? mcy() : zoomAnchorY;
            double wx = camX + (ax - mcx()) / zoom;
            double wz = camZ + (ay - mcy()) / zoom;
            zoom = nz;
            camX = wx - (ax - mcx()) / zoom;
            camZ = wz - (ay - mcy()) / zoom;
        }
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (pendingFoundDone) {
            pendingFoundDone = false;
            BlockPos p = pendingFound;
            if (p != null) {
                camX = p.getX();
                camZ = p.getZ();
                if (zoomTarget < 0.8) {
                    zoomTarget = 0.8;
                    zoomAnchorX = -1;
                    zoomAnchorY = -1;
                }
                foundPos = p;
                foundUntil = System.currentTimeMillis() + 6000L;
                notice = I18nGet("panoptic.seed.found_at") + " " + pendingFoundName + " (" + p.getX() + " " + p.getZ() + ")";
                copiedUntil = System.currentTimeMillis() + 4000L;
            } else {
                notice = I18nGet("panoptic.seed.not_found");
                copiedUntil = System.currentTimeMillis() + 2500L;
            }
        }
        hoverTip = null;
        this.renderTransparentBackground(g);
        int w = this.width - MARGIN * 2;
        int h = this.height - MARGIN * 2;
        int left = MARGIN;
        int top = MARGIN;
        int right = left + w;
        int bottom = top + h;
        mLeft = left + 1;
        mTop = top + TOP_H;
        mRight = right - 1;
        mBottom = bottom - BOT_H;
        tickAnim();

        g.fill(left + 3, top + 4, right + 3, bottom + 4, 0x66000000);
        GuiStyle.box(g, left, top, right, bottom);

        scis(g, mLeft, mTop, mRight, mBottom);
        g.fill(mLeft, mTop, mRight, mBottom, MAP_BG);
        if (map != null) {
            tileLayer.draw(g, map, pool, camX, camZ, zoom, mLeft, mTop, mRight, mBottom);
            drawSlimeChunks(g);
            drawGrid(g);
            drawStructures(g, mouseX, mouseY);
            drawMarkers(g);
            drawFoundPulse(g);
            drawDimSelector(g, mouseX, mouseY);
            drawSearch(g, mouseX, mouseY);
        } else {
            boolean preparing = ClientWorldgen.loading()
                    || (!ClientWorldgen.ready() && !ClientWorldgen.failed()
                    && this.minecraft.getSingleplayerServer() == null);
            if (preparing && ClientWorldgen.ready()) {
                preparing = false;
            }
            if ((ClientWorldgen.ready() || ClientWorldgen.failed())
                    && System.currentTimeMillis() > worldgenRetryAt) {
                worldgenRetryAt = System.currentTimeMillis() + 500L;
                rebuild(SeedMap.parseSeed(seedText));
            }
            g.drawCenteredString(this.font, Component.translatable(
                            preparing ? "panoptic.seed.preparing" : "panoptic.seed.unavailable"),
                    (mLeft + mRight) / 2, (mTop + mBottom) / 2 - 4, GuiStyle.MUTED);
            drawDimSelector(g, mouseX, mouseY);
        }
        drawCrosshair(g, mouseX, mouseY);
        g.disableScissor();

        renderTopBar(g, left, top, right, mouseX, mouseY);
        renderBottomBar(g, left, bottom, right, mouseX, mouseY);
        if (histOpen) {
            renderHistory(g, mouseX, mouseY);
        }
        boolean noticeActive = notice != null && System.currentTimeMillis() < copiedUntil;
        if (noticeActive) {
            int tw = this.font.width(notice);
            int nx = (mLeft + mRight) / 2;
            int ny = mBottom - 22;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 500.0F);
            g.fill(nx - tw / 2 - 7, ny - 5, nx + tw / 2 + 7, ny + 12, GuiStyle.T(0xE81A150D));
            GuiStyle.rect(g, nx - tw / 2 - 7, ny - 5, nx + tw / 2 + 7, ny + 12, GuiStyle.ACCENT);
            g.drawCenteredString(this.font, notice, nx, ny, GuiStyle.TEXT);
            g.pose().popPose();
        }
        if (hoverTip != null) {
            g.renderTooltip(this.font, Component.literal(hoverTip), mouseX, mouseY);
        }
        SeedMap.Placed hov = hoverTip != null || searchOpen ? null : structUnder(mouseX, mouseY);
        if (hov != null) {
            String sid = SeedText.structId(hov.structure());
            String ns = sid.contains(":") ? sid.substring(0, sid.indexOf(':')) : "minecraft";
            g.renderComponentTooltip(this.font, List.of(
                    Component.literal(SeedText.structName(hov.structure())),
                    Component.literal("§6" + SeedText.modDisplay(ns)),
                    Component.literal("§8" + sid),
                    Component.literal("§7X " + hov.x() + "   Z " + hov.z())), mouseX, mouseY);
        }
        drawHelp(g);
    }

    private void drawHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.seed.help_b1"));
        bullets.add(Component.translatable("panoptic.seed.help_b2"));
        bullets.add(Component.translatable("panoptic.seed.help_b3"));
        bullets.add(Component.translatable("panoptic.seed.help_b4"));
        List<HelpCard.KeyHint> keys = List.of(
                ModBinds.hint(ModBinds.Bind.SEARCH, "panoptic.seed.help_search"),
                ModBinds.hint(ModBinds.Bind.CENTER, "panoptic.seed.help_center"));
        HelpCard.render(g, this.font, this.width, this.height, helpHover,
                Component.translatable("panoptic.seed.help_title"),
                Component.translatable("panoptic.seed.help_sum"), bullets, keys);
    }

    private void drawDimSelector(GuiGraphics g, int mouseX, int mouseY) {
        String label = SeedText.dimName(mapDim);
        int w = this.font.width(label) + 26;
        dimChipX1 = mLeft + 6;
        dimChipY1 = mTop + 6;
        dimChipX2 = dimChipX1 + w;
        dimChipY2 = dimChipY1 + 13;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        boolean hov = mouseX >= dimChipX1 && mouseX <= dimChipX2 && mouseY >= dimChipY1 && mouseY <= dimChipY2;
        g.fill(dimChipX1, dimChipY1, dimChipX2, dimChipY2, hov ? GuiStyle.T(0xE0241E12) : GuiStyle.T(0xD01A150D));
        GuiStyle.rect(g, dimChipX1, dimChipY1, dimChipX2, dimChipY2, hov ? GuiStyle.ACCENT : GuiStyle.T(0xFF6A5630));
        Icons.iconGlobe(g, dimChipX1 + 3, dimChipY1 + 3, GuiStyle.ACCENT);
        g.drawString(this.font, label, dimChipX1 + 14, dimChipY1 + 3, GuiStyle.TEXT, false);
        Icons.iconArrow(g, dimChipX2 - 9, dimChipY1 + 4, GuiStyle.MUTED, dimListOpen);
        if (!dimListOpen) {
            tip(dimChipX1, dimChipY1, dimChipX2, dimChipY2, mouseX, mouseY, "panoptic.seed.tip.dim");
        }
        if (dimListOpen) {
            int lw = w;
            for (ResourceKey<Level> k : dims) {
                lw = Math.max(lw, this.font.width(SeedText.dimName(k)) + 26);
            }
            dimListW = lw;
            int maxRows = Math.max(3, (mBottom - dimChipY2 - 18) / 13);
            int rows = Math.min(dims.size(), maxRows);
            dimRowsVis = rows;
            dimScroll = Mth.clamp(dimScroll, 0, Math.max(0, dims.size() - rows));
            double dk = animDt <= 0 ? 1.0 : 1.0 - Math.exp(-animDt * 16.0);
            dimScrollF += (dimScroll - dimScrollF) * dk;
            if (Math.abs(dimScroll - dimScrollF) < 0.01) {
                dimScrollF = dimScroll;
            }
            int listTop = dimChipY2 + 2;
            int di0 = (int) Math.floor(dimScrollF);
            int dyoff = (int) Math.round((dimScrollF - di0) * 13.0);
            scis(g, dimChipX1, listTop, dimChipX1 + lw, listTop + rows * 13);
            int y = listTop - dyoff;
            for (int i = di0; i <= di0 + rows && i < dims.size(); i++) {
                ResourceKey<Level> k = dims.get(i);
                boolean rowHov = mouseX >= dimChipX1 && mouseX <= dimChipX1 + lw && mouseY >= y && mouseY <= y + 12;
                boolean cur = k == mapDim;
                g.fill(dimChipX1, y, dimChipX1 + lw, y + 12, rowHov ? GuiStyle.T(0xE0332A18) : 0xE01A150D);
                GuiStyle.rect(g, dimChipX1, y, dimChipX1 + lw, y + 12, GuiStyle.T(0xFF4A3D24));
                g.drawString(this.font, this.font.plainSubstrByWidth(SeedText.dimName(k), lw - 18),
                        dimChipX1 + 14, y + 2, cur ? GuiStyle.ACCENT : GuiStyle.TEXT, false);
                if (cur) {
                    g.drawString(this.font, "•", dimChipX1 + 5, y + 2, GuiStyle.ACCENT, false);
                }
                y += 13;
            }
            g.disableScissor();
            if (dims.size() > rows) {
                int my = listTop + rows * 13;
                String more = (dimScroll + 1) + "-" + Math.min(dims.size(), dimScroll + rows) + " / " + dims.size();
                g.fill(dimChipX1, my, dimChipX1 + lw, my + 11, 0xE01A150D);
                GuiStyle.rect(g, dimChipX1, my, dimChipX1 + lw, my + 11, GuiStyle.T(0xFF4A3D24));
                g.drawString(this.font, more, dimChipX1 + (lw - this.font.width(more)) / 2, my + 2, GuiStyle.DIM, false);
            }
        }
        g.pose().popPose();
    }

    private boolean clickDimSelector(double mouseX, double mouseY) {
        if (mouseX >= dimChipX1 && mouseX <= dimChipX2 && mouseY >= dimChipY1 && mouseY <= dimChipY2) {
            dimListOpen = !dimListOpen;
            if (dimListOpen) {
                dimScroll = Math.max(0, dims.indexOf(mapDim) - 2);
                dimScrollF = dimScroll;
                histOpen = false;
            }
            sound(1.0F);
            return true;
        }
        if (dimListOpen) {
            int listTop = dimChipY2 + 2;
            if (mouseX >= dimChipX1 && mouseX <= dimChipX1 + dimListW
                    && mouseY >= listTop && mouseY < listTop + dimRowsVis * 13) {
                int di0 = (int) Math.floor(dimScrollF);
                int dyoff = (int) Math.round((dimScrollF - di0) * 13.0);
                int y = listTop - dyoff;
                for (int i = di0; i <= di0 + dimRowsVis && i < dims.size(); i++) {
                    if (mouseY >= y && mouseY <= y + 12) {
                        ResourceKey<Level> k = dims.get(i);
                        dimListOpen = false;
                        if (k != mapDim) {
                            mapDim = k;
                            apply();
                        }
                        return true;
                    }
                    y += 13;
                }
                return true;
            }
            if (dims.size() > dimRowsVis && mouseX >= dimChipX1 && mouseX <= dimChipX1 + dimListW
                    && mouseY >= listTop + dimRowsVis * 13 && mouseY <= listTop + dimRowsVis * 13 + 11) {
                return true;
            }
            dimListOpen = false;
        }
        return false;
    }

    private final Map<ResourceLocation, ResourceKey<Level>> structHome = new HashMap<>();

    private void buildSearchItems() {
        if (allSearchItems != null || map == null) {
            return;
        }
        List<SearchItem> out = new ArrayList<>();
        try {
            for (Holder.Reference<Structure> h : map.structureList()) {
                out.add(new SearchItem(false, h.key().location(), SeedText.structName(h), h));
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Holder.Reference<Biome> h : map.access.registryOrThrow(
                    Registries.BIOME).holders().toList()) {
                out.add(new SearchItem(true, h.key().location(), SeedText.biomeName(h), h));
            }
        } catch (Throwable ignored) {
        }
        allSearchItems = out;
    }

    private void buildStructHome() {
        if (!structHome.isEmpty()) {
            return;
        }
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        boolean changed = false;
        for (ResourceKey<Level> k : dims) {
            ServerLevel l = server.getLevel(k);
            if (l == null) {
                continue;
            }
            try {
                for (Holder<StructureSet> setH
                        : l.getChunkSource().getGeneratorState().possibleStructureSets()) {
                    StructureSet set = setH.value();
                    boolean dense = set.placement() instanceof
                            RandomSpreadStructurePlacement rsp
                            && rsp.spacing() <= 5;
                    for (StructureSet.StructureSelectionEntry e
                            : set.structures()) {
                        Optional<ResourceKey<Structure>> sk = e.structure().unwrapKey();
                        if (sk.isEmpty()) {
                            continue;
                        }
                        ResourceLocation id = sk.get().location();
                        structHome.putIfAbsent(id, k);
                        if ((dense || id.getPath().contains("fossil")) && !SeedPrefs.SHOWN.contains(id) && SeedPrefs.HIDDEN.add(id)) {
                            changed = true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (changed) {
            SeedPrefs.saveHidden();
        }
        if (SeedPrefs.updateEff()) {
            invalidateStructs();
        }
    }

    private void computeSearchSuggestions() {
        sugg.clear();
        searchSel = 0;
        searchScroll = 0;
        searchScrollF = 0;
        if (allSearchItems == null) {
            return;
        }
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        String nsFilter = null;
        if (q.startsWith("@")) {
            int sp = q.indexOf(' ');
            nsFilter = sp > 0 ? q.substring(1, sp) : q.substring(1);
            q = sp > 0 ? q.substring(sp + 1).trim() : "";
        }
        List<SearchItem> pool = new ArrayList<>();
        for (SearchItem it : allSearchItems) {
            if (catSkip(it)) {
                continue;
            }
            if (nsFilter != null && !it.id().getNamespace().contains(nsFilter)
                    && !SeedText.modDisplay(it.id().getNamespace()).toLowerCase(Locale.ROOT).contains(nsFilter)) {
                continue;
            }
            pool.add(it);
        }
        if (!q.isEmpty()) {
            List<SearchItem> starts = new ArrayList<>();
            List<SearchItem> contains = new ArrayList<>();
            List<SearchItem> ids = new ArrayList<>();
            for (SearchItem it : pool) {
                String n = it.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(q)) {
                    starts.add(it);
                } else if (n.contains(q)) {
                    contains.add(it);
                } else if (it.id().toString().contains(q)) {
                    ids.add(it);
                }
            }
            for (SearchItem it : starts) {
                sugg.add(new ListRow(it, null, null, 0, false));
            }
            for (SearchItem it : contains) {
                sugg.add(new ListRow(it, null, null, 0, false));
            }
            for (SearchItem it : ids) {
                sugg.add(new ListRow(it, null, null, 0, false));
            }
            return;
        }
        TreeMap<String, List<SearchItem>> byNs = new TreeMap<>();
        for (SearchItem it : pool) {
            byNs.computeIfAbsent(it.id().getNamespace(), k -> new ArrayList<>()).add(it);
        }
        Comparator<SearchItem> byName = Comparator.comparing(SearchItem::name, String.CASE_INSENSITIVE_ORDER);
        boolean force = nsFilter != null;
        for (Map.Entry<String, List<SearchItem>> e : byNs.entrySet()) {
            String ns = e.getKey();
            List<SearchItem> items = e.getValue();
            items.sort(byName);
            boolean allHidden = true;
            for (SearchItem it : items) {
                if (it.isBiome() || !SeedPrefs.HIDDEN.contains(it.id())) {
                    allHidden = false;
                    break;
                }
            }
            sugg.add(new ListRow(null, ns, SeedText.modDisplay(ns), items.size(), allHidden));
            if (force || searchCat == 3 || expandedMods.contains(ns)) {
                for (SearchItem it : items) {
                    sugg.add(new ListRow(it, null, null, 0, false));
                }
            }
        }
    }

    private boolean catSkip(SearchItem it) {
        if (searchCat == 3) {
            return it.isBiome() || !(SeedPrefs.FILTERS.contains(it.id()) || SeedPrefs.HIDDEN.contains(it.id()));
        }
        return searchCat == 1 && it.isBiome() || searchCat == 2 && !it.isBiome();
    }

    private void computeSearchSuggestionsKeepScroll() {
        int sc = searchScroll;
        int sl = searchSel;
        computeSearchSuggestions();
        searchScroll = Mth.clamp(sc, 0, Math.max(0, sugg.size() - 1));
        searchSel = Mth.clamp(sl, 0, Math.max(0, sugg.size() - 1));
    }

    private void toggleHiddenMod(String ns) {
        if (allSearchItems == null) {
            return;
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (SearchItem it : allSearchItems) {
            if (!it.isBiome() && it.id().getNamespace().equals(ns)) {
                ids.add(it.id());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        boolean allHidden = SeedPrefs.HIDDEN.containsAll(ids);
        if (allHidden) {
            ids.forEach(SeedPrefs.HIDDEN::remove);
            SeedPrefs.SHOWN.addAll(ids);
        } else {
            SeedPrefs.HIDDEN.addAll(ids);
            ids.forEach(SeedPrefs.SHOWN::remove);
        }
        SeedPrefs.saveHidden();
        SeedPrefs.saveShown();
        if (SeedPrefs.updateEff()) {
            invalidateStructs();
        }
        sound(1.0F);
    }

    private void drawSearch(GuiGraphics g, int mouseX, int mouseY) {
        String lbl = I18nGet("panoptic.seed.search");
        int w = this.font.width(lbl) + 22;
        schX1 = dimChipX2 + 4;
        schY1 = dimChipY1;
        schX2 = schX1 + w;
        schY2 = schY1 + 13;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        boolean hov = mouseX >= schX1 && mouseX <= schX2 && mouseY >= schY1 && mouseY <= schY2;
        g.fill(schX1, schY1, schX2, schY2, hov || searchOpen ? GuiStyle.T(0xE0241E12) : GuiStyle.T(0xD01A150D));
        GuiStyle.rect(g, schX1, schY1, schX2, schY2, hov || searchOpen ? GuiStyle.ACCENT : GuiStyle.T(0xFF6A5630));
        Icons.searchIcon(g, schX1 + 4, schY1 + 3, GuiStyle.ACCENT);
        g.drawString(this.font, lbl, schX1 + 14, schY1 + 3, hov || searchOpen ? GuiStyle.TEXT : GuiStyle.MUTED, false);
        if (!searchOpen) {
            tip(schX1, schY1, schX2, schY2, mouseX, mouseY, "panoptic.seed.tip.search");
        }
        if (!SeedPrefs.FILTERS.isEmpty()) {
            String fl = I18nGet("panoptic.seed.filter_n") + " " + SeedPrefs.FILTERS.size();
            int fw = this.font.width(fl) + 28;
            fchX1 = schX2 + 4;
            fchY1 = schY1;
            fchX2 = fchX1 + fw;
            fchY2 = schY2;
            g.fill(fchX1, fchY1, fchX2, fchY2, GuiStyle.T(0xE0332A18));
            GuiStyle.rect(g, fchX1, fchY1, fchX2, fchY2, GuiStyle.ACCENT);
            Icons.iconFilter(g, fchX1 + 4, fchY1 + 3, GuiStyle.ACCENT);
            g.drawString(this.font, fl, fchX1 + 14, fchY1 + 3, GuiStyle.TEXT, false);
            Icons.iconHide(g, fchX2 - 10, fchY1 + 4, 0xFFFF7B6B);
            tip(fchX1, fchY1, fchX2, fchY2, mouseX, mouseY, "panoptic.seed.tip.clearfilter");
        } else {
            fchX1 = fchX2 = fchY1 = fchY2 = 0;
        }
        if (searchOpen && detailItem != null) {
            drawDetailCard(g, mouseX, mouseY);
        } else if (searchOpen) {
            g.fill(mLeft, mTop, mRight, mBottom, 0x90000000);
            int pw = Math.min(460, mRight - mLeft - 24);
            panX1 = (mLeft + mRight - pw) / 2;
            panX2 = panX1 + pw;
            panY1 = mTop + 22;
            int maxRows = Math.max(3, (mBottom - panY1 - 77) / 19);
            int rows = Math.min(maxRows, sugg.size());
            searchScroll = Mth.clamp(searchScroll, 0, Math.max(0, sugg.size() - maxRows));
            double sk = animDt <= 0 ? 1.0 : 1.0 - Math.exp(-animDt * 16.0);
            searchScrollF += (searchScroll - searchScrollF) * sk;
            if (Math.abs(searchScroll - searchScrollF) < 0.01) {
                searchScrollF = searchScroll;
            }
            panY2 = panY1 + 43 + Math.max(rows, 0) * 19 + 15;
            g.fill(panX1 + 3, panY1 + 4, panX2 + 3, panY2 + 4, 0x66000000);
            GuiStyle.panel(g, panX1, panY1, panX2, panY2);
            int fx1 = panX1 + 6;
            int fy1 = panY1 + 5;
            int fx2 = panX2 - 6;
            g.fill(fx1, fy1, fx2, fy1 + 16, GuiStyle.SEARCH_BG);
            GuiStyle.rect(g, fx1, fy1, fx2, fy1 + 16, GuiStyle.ACCENT);
            Icons.searchIcon(g, fx1 + 5, fy1 + 4, GuiStyle.ACCENT);
            if (!searchText.isEmpty()) {
                g.drawString(this.font, searchText, fx1 + 16, fy1 + 4, GuiStyle.TEXT, false);
            }
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cw = this.font.width(searchText.substring(0, Mth.clamp(searchCaret, 0, searchText.length())));
                g.fill(fx1 + 16 + cw, fy1 + 3, fx1 + 17 + cw, fy1 + 13, GuiStyle.ACCENT);
            }
            int chipY = fy1 + 20;
            int chipX = fx1;
            for (int ci = 0; ci < 4; ci++) {
                String cl = I18nGet(ci == 0 ? "panoptic.seed.cat.all" : ci == 1 ? "panoptic.seed.cat.structs"
                        : ci == 2 ? "panoptic.seed.cat.biomes" : "panoptic.seed.cat.marked");
                int cwd = this.font.width(cl) + 12;
                boolean act = searchCat == ci;
                boolean chHov = mouseX >= chipX && mouseX <= chipX + cwd && mouseY >= chipY && mouseY <= chipY + 13;
                if (act) {
                    g.fill(chipX, chipY, chipX + cwd, chipY + 13, GuiStyle.T(0xD0101117));
                    GuiStyle.rect(g, chipX, chipY, chipX + cwd, chipY + 13, GuiStyle.ACCENT);
                    g.fill(chipX + 1, chipY + 11, chipX + cwd - 1, chipY + 13, GuiStyle.ACCENT);
                } else {
                    g.fill(chipX, chipY, chipX + cwd, chipY + 13, chHov ? 0x33FFFFFF : 0x22FFFFFF);
                    GuiStyle.rect(g, chipX, chipY, chipX + cwd, chipY + 13, GuiStyle.BORDER);
                }
                g.drawString(this.font, cl, chipX + 6, chipY + 3, act ? GuiStyle.ACCENT : GuiStyle.MUTED, false);
                chipX += cwd + 4;
            }
            String rl = I18nGet("panoptic.seed.reset");
            int rw = this.font.width(rl) + 12;
            int rx1 = fx2 - rw;
            boolean anyMod = !SeedPrefs.FILTERS.isEmpty() || !SeedPrefs.HIDDEN.isEmpty();
            boolean rHov = mouseX >= rx1 && mouseX <= fx2 && mouseY >= chipY && mouseY <= chipY + 13;
            g.fill(rx1, chipY, fx2, chipY + 13, rHov ? 0x40FF6B5B : 0x22FFFFFF);
            GuiStyle.rect(g, rx1, chipY, fx2, chipY + 13, !SeedPrefs.FILTERS.isEmpty() ? 0xFFB05040 : GuiStyle.BORDER);
            g.drawString(this.font, rl, rx1 + 6, chipY + 3, !SeedPrefs.FILTERS.isEmpty() ? 0xFFFF9B8B : anyMod ? GuiStyle.MUTED : GuiStyle.DIM, false);
            if (rHov) {
                tip(rx1, chipY, fx2, chipY + 13, mouseX, mouseY, "panoptic.seed.tip.reset");
            }
            int listTop = fy1 + 37;
            int i0 = (int) Math.floor(searchScrollF);
            int yoff = (int) Math.round((searchScrollF - i0) * 19.0);
            scis(g, panX1 + 3, listTop, panX2 - 3, listTop + rows * 19);
            int y = listTop - yoff;
            for (int i = i0; i <= i0 + rows && i < sugg.size(); i++) {
                ListRow row = sugg.get(i);
                boolean sel = i == searchSel;
                boolean rowHov = mouseX >= panX1 + 3 && mouseX <= panX2 - 3 && mouseY >= y && mouseY <= y + 18;
                if (row.item() == null) {
                    g.fill(panX1 + 3, y + 1, panX2 - 3, y + 17, sel || rowHov ? GuiStyle.T(0xE01E1A10) : GuiStyle.T(0xE0171309));
                    GuiStyle.rect(g, panX1 + 3, y + 1, panX2 - 3, y + 17, sel ? GuiStyle.ACCENT : GuiStyle.T(0xFF4A3D24));
                    Icons.iconArrow(g, panX1 + 8, y + 6, GuiStyle.MUTED, expandedMods.contains(row.ns()));
                    g.drawString(this.font, this.font.plainSubstrByWidth(row.nsName(), pw - 120), panX1 + 20, y + 5,
                            row.nsHidden() ? GuiStyle.DIM : GuiStyle.TEXT, false);
                    String cnt = String.valueOf(row.count());
                    g.drawString(this.font, cnt, panX2 - 34 - this.font.width(cnt), y + 5, GuiStyle.DIM, false);
                    boolean eHov = rowHov && mouseX >= panX2 - 26 && mouseX <= panX2 - 10;
                    if (row.nsHidden()) {
                        Icons.iconEye(g, panX2 - 22, y + 5, GuiStyle.ACCENT);
                    } else {
                        Icons.iconHide(g, panX2 - 22, y + 5, eHov ? 0xFFFF7B6B : GuiStyle.DIM);
                    }
                    if (eHov) {
                        tip(panX2 - 26, y, panX2 - 10, y + 18, mouseX, mouseY,
                                row.nsHidden() ? "panoptic.seed.tip.showmod" : "panoptic.seed.tip.hidemod");
                    }
                    y += 19;
                    continue;
                }
                SearchItem it = row.item();
                boolean hidden = !it.isBiome() && SeedPrefs.HIDDEN.contains(it.id());
                if (sel || rowHov) {
                    g.fill(panX1 + 3, y, panX2 - 3, y + 18, sel ? GuiStyle.T(0x33E8C06C) : GuiStyle.ROWHOVER);
                }
                if (it.isBiome()) {
                    g.fill(panX1 + 8, y + 4, panX1 + 18, y + 14, map.color((Holder<Biome>) it.holder()));
                    GuiStyle.rect(g, panX1 + 8, y + 4, panX1 + 18, y + 14, 0x66000000);
                } else {
                    g.pose().pushPose();
                    g.pose().translate(panX1 + 6, y + 1, 0.0F);
                    g.renderFakeItem(StructVisuals.icon((Holder<Structure>) it.holder()), 0, 0);
                    g.pose().popPose();
                }
                int nameCol = hidden ? GuiStyle.DIM : GuiStyle.TEXT;
                g.drawString(this.font, this.font.plainSubstrByWidth(it.name(), pw - 150), panX1 + 26, y + 5, nameCol, false);
                if (it.isBiome()) {
                    String tag = I18nGet("panoptic.seed.tag_biome");
                    g.drawString(this.font, tag, panX2 - 10 - this.font.width(tag), y + 5, GuiStyle.DIM, false);
                } else {
                    ResourceKey<Level> home = structHome.get(it.id());
                    if (!structHome.isEmpty()) {
                        String dtag = home == null ? "—" : SeedText.dimName(home);
                        int dcol = home == null ? GuiStyle.DIM : home == mapDim ? GuiStyle.MUTED : 0xFFC08A5A;
                        g.drawString(this.font, dtag, panX2 - 50 - this.font.width(dtag), y + 5, dcol, false);
                    }
                    boolean fActive = SeedPrefs.FILTERS.contains(it.id());
                    boolean fHov = rowHov && mouseX >= panX2 - 46 && mouseX <= panX2 - 30;
                    Icons.iconFilter(g, panX2 - 42, y + 5, fActive ? GuiStyle.ACCENT : fHov ? GuiStyle.TEXT : GuiStyle.DIM);
                    if (fHov) {
                        tip(panX2 - 46, y, panX2 - 30, y + 18, mouseX, mouseY,
                                fActive ? "panoptic.seed.tip.clearfilter" : "panoptic.seed.tip.filter");
                    }
                    boolean hHov = rowHov && mouseX >= panX2 - 26 && mouseX <= panX2 - 10;
                    if (hidden) {
                        Icons.iconEye(g, panX2 - 22, y + 5, GuiStyle.ACCENT);
                    } else {
                        Icons.iconHide(g, panX2 - 22, y + 5, hHov ? 0xFFFF7B6B : GuiStyle.DIM);
                    }
                    if (hHov) {
                        tip(panX2 - 26, y, panX2 - 10, y + 18, mouseX, mouseY,
                                hidden ? "panoptic.seed.tip.restore" : "panoptic.seed.tip.hide");
                    }
                }
                y += 19;
            }
            g.disableScissor();
            if (sugg.size() > rows) {
                String more = (searchScroll + 1) + "-" + Math.min(sugg.size(), searchScroll + rows) + " / " + sugg.size();
                g.drawString(this.font, more, panX2 - 8 - this.font.width(more), panY2 - 12, GuiStyle.DIM, false);
            }
        }
        g.pose().popPose();
    }

    private SearchItem searchItemFor(Holder<Structure> h) {
        buildSearchItems();
        ResourceLocation id = h.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null || allSearchItems == null) {
            return null;
        }
        for (SearchItem it : allSearchItems) {
            if (!it.isBiome() && it.id().equals(id)) {
                return it;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void openDetail(SearchItem it) {
        detailItem = it;
        List<String> info = new ArrayList<>();
        if (map != null && !it.isBiome()) {
            try {
                info.addAll(map.structureInfo((Holder<Structure>) it.holder()));
            } catch (Throwable ignored) {
            }
            map.ensureDiskCounts(structPool);
        }
        detailInfo = info;
    }

    private void drawDetailCard(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(mLeft, mTop, mRight, mBottom, 0x90000000);
        int pw = Math.min(460, mRight - mLeft - 24);
        panX1 = (mLeft + mRight - pw) / 2;
        panX2 = panX1 + pw;
        panY1 = mTop + 22;
        SearchItem it = detailItem;
        List<String> lines = new ArrayList<>();
        lines.add("§8" + it.id());
        String ns = it.id().getNamespace();
        lines.add("§7" + SeedText.modDisplay(ns));
        ResourceKey<Level> home = structHome.get(it.id());
        if (!structHome.isEmpty()) {
            lines.add("§7" + I18nGet("panoptic.seed.card.dim") + " §f" + (home == null ? "—" : SeedText.dimName(home)));
        }
        Map<String, Integer> dc = map == null ? null : map.diskCountsNow();
        int gen = dc == null ? -1 : dc.getOrDefault(it.id().toString(), 0);
        String cnt = gen < 0 ? I18nGet("panoptic.seed.card.counting") : String.valueOf(gen);
        lines.add("§7" + I18nGet("panoptic.seed.card.inworld") + " §f" + cnt);
        lines.add("§7" + I18nGet("panoptic.seed.card.onmap") + " §f" + structs.loadedCountFor(it.id()));
        lines.addAll(detailInfo);
        if (detailInfo.isEmpty()) {
            lines.add("§8" + I18nGet("panoptic.seed.card.nosets"));
        }
        detailLines = lines;
        panY2 = panY1 + 26 + lines.size() * 11 + 24;
        g.fill(panX1 + 3, panY1 + 4, panX2 + 3, panY2 + 4, 0x66000000);
        GuiStyle.panel(g, panX1, panY1, panX2, panY2);
        g.pose().pushPose();
        g.pose().translate(panX1 + 6, panY1 + 5, 0.0F);
        g.renderFakeItem(StructVisuals.icon((Holder<Structure>) (Object) it.holder()), 0, 0);
        g.pose().popPose();
        g.drawString(this.font, this.font.plainSubstrByWidth(it.name(), pw - 60), panX1 + 26, panY1 + 9, GuiStyle.ACCENT, false);
        int y = panY1 + 26;
        for (String l : lines) {
            boolean lHov = mouseX >= panX1 + 6 && mouseX <= panX2 - 6 && mouseY >= y - 1 && mouseY < y + 10;
            if (lHov) {
                g.fill(panX1 + 6, y - 1, panX2 - 6, y + 10, GuiStyle.ROWHOVER);
                hoverTip = I18nGet("panoptic.seed.tip.copyline");
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(l, pw - 18), panX1 + 8, y, GuiStyle.TEXT, false);
            Icons.iconCopy(g, panX2 - 15, y, lHov ? GuiStyle.ACCENT : GuiStyle.T(0x55E8C06C));
            y += 11;
        }
        int by = panY2 - 18;
        boolean hidden = SeedPrefs.HIDDEN.contains(it.id());
        boolean fActive = SeedPrefs.FILTERS.contains(it.id());
        cardBtn(g, panX1 + 6, by, 60, I18nGet("panoptic.seed.card.find"), 0xFFB6E08A, mouseX, mouseY);
        tip(panX1 + 6, by, panX1 + 66, by + 14, mouseX, mouseY, "panoptic.seed.tip.find");
        cardBtn(g, panX1 + 70, by, 18, "", 0, mouseX, mouseY);
        Icons.iconFilter(g, panX1 + 75, by + 4, fActive ? GuiStyle.ACCENT : GuiStyle.MUTED);
        tip(panX1 + 70, by, panX1 + 88, by + 14, mouseX, mouseY, fActive ? "panoptic.seed.tip.clearfilter" : "panoptic.seed.tip.filter");
        cardBtn(g, panX1 + 92, by, 18, "", 0, mouseX, mouseY);
        if (hidden) {
            Icons.iconEye(g, panX1 + 97, by + 4, GuiStyle.ACCENT);
        } else {
            Icons.iconHide(g, panX1 + 98, by + 4, 0xFFFF7B6B);
        }
        tip(panX1 + 92, by, panX1 + 110, by + 14, mouseX, mouseY, hidden ? "panoptic.seed.tip.restore" : "panoptic.seed.tip.hide");
        cardBtn(g, panX1 + 114, by, 18, "", 0, mouseX, mouseY);
        Icons.iconCopy(g, panX1 + 119, by + 3, GuiStyle.MUTED);
        tip(panX1 + 114, by, panX1 + 132, by + 14, mouseX, mouseY, "panoptic.seed.tip.copy");
        cardBtn(g, panX2 - 28, by, 22, "", 0, mouseX, mouseY);
        Icons.iconBack(g, panX2 - 22, by + 3, GuiStyle.MUTED);
        tip(panX2 - 28, by, panX2 - 6, by + 14, mouseX, mouseY, "panoptic.seed.tip.back");
    }

    private void cardBtn(GuiGraphics g, int x, int y, int w, String label, int color, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 14;
        g.fill(x, y, x + w, y + 14, hov ? GuiStyle.ROWHOVER : 0x14FFFFFF);
        GuiStyle.rect(g, x, y, x + w, y + 14, hov ? GuiStyle.ACCENT : GuiStyle.BORDER);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 3, color, false);
    }

    private String hoverTip;

    private void tip(int x1, int y1, int x2, int y2, int mouseX, int mouseY, String key) {
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2) {
            hoverTip = I18nGet(key);
        }
    }

    private boolean clickDetail(double mouseX, double mouseY) {
        SearchItem it = detailItem;
        int by = panY2 - 18;
        if (mouseY >= by && mouseY <= by + 14) {
            if (mouseX >= panX1 + 6 && mouseX <= panX1 + 66) {
                detailItem = null;
                activateSearch(it, false);
                return true;
            }
            if (mouseX >= panX1 + 70 && mouseX <= panX1 + 88) {
                toggleFilter(it.id());
                return true;
            }
            if (mouseX >= panX1 + 92 && mouseX <= panX1 + 110) {
                toggleHidden(it);
                return true;
            }
            if (mouseX >= panX1 + 114 && mouseX <= panX1 + 132) {
                copy(it.id().toString());
                return true;
            }
            if (mouseX >= panX2 - 28 && mouseX <= panX2 - 6) {
                detailItem = null;
                sound(0.9F);
                return true;
            }
        }
        int ly = panY1 + 26;
        if (mouseX >= panX1 + 6 && mouseX <= panX2 - 6 && mouseY >= ly && mouseY < ly + detailLines.size() * 11) {
            int idx = (int) ((mouseY - ly) / 11);
            if (idx >= 0 && idx < detailLines.size()) {
                copy(detailLines.get(idx).replaceAll("§.", "").trim());
                return true;
            }
        }
        if (mouseX >= panX1 && mouseX <= panX2 && mouseY >= panY1 && mouseY <= panY2) {
            return true;
        }
        detailItem = null;
        searchOpen = false;
        return true;
    }

    private boolean clickSearch(double mouseX, double mouseY) {
        if (mouseX >= schX1 && mouseX <= schX2 && mouseY >= schY1 && mouseY <= schY2) {
            searchOpen = !searchOpen;
            if (searchOpen) {
                buildSearchItems();
                computeSearchSuggestions();
                dimListOpen = false;
            }
            sound(1.0F);
            return true;
        }
        if (!SeedPrefs.FILTERS.isEmpty() && mouseX >= fchX1 && mouseX <= fchX2 && mouseY >= fchY1 && mouseY <= fchY2) {
            SeedPrefs.FILTERS.clear();
            SeedPrefs.saveFilters();
            if (SeedPrefs.updateEff()) {
                invalidateStructs();
            }
            sound(0.9F);
            return true;
        }
        if (searchOpen && detailItem != null) {
            return clickDetail(mouseX, mouseY);
        }
        if (searchOpen) {
            if (mouseX >= panX1 && mouseX <= panX2 && mouseY >= panY1 && mouseY <= panY2) {
                int chipY = panY1 + 25;
                if (mouseY >= chipY && mouseY <= chipY + 13) {
                    String rl = I18nGet("panoptic.seed.reset");
                    int rw = this.font.width(rl) + 12;
                    if (mouseX >= panX2 - 6 - rw && mouseX <= panX2 - 6) {
                        if (!SeedPrefs.FILTERS.isEmpty()) {
                            SeedPrefs.FILTERS.clear();
                            SeedPrefs.saveFilters();
                            if (SeedPrefs.updateEff()) {
                                invalidateStructs();
                            }
                        }
                        computeSearchSuggestionsKeepScroll();
                        sound(0.9F);
                        return true;
                    }
                    int chipX = panX1 + 6;
                    for (int ci = 0; ci < 4; ci++) {
                        String cl = I18nGet(ci == 0 ? "panoptic.seed.cat.all" : ci == 1 ? "panoptic.seed.cat.structs"
                                : ci == 2 ? "panoptic.seed.cat.biomes" : "panoptic.seed.cat.marked");
                        int cwd = this.font.width(cl) + 12;
                        if (mouseX >= chipX && mouseX <= chipX + cwd) {
                            if (searchCat != ci) {
                                searchCat = ci;
                                computeSearchSuggestions();
                                sound(1.0F);
                            }
                            return true;
                        }
                        chipX += cwd + 4;
                    }
                    return true;
                }
                int maxRows = Math.max(3, (mBottom - panY1 - 77) / 19);
                int vrows = Math.min(maxRows, sugg.size());
                int listTop = panY1 + 42;
                if (mouseY >= listTop + vrows * 19) {
                    return true;
                }
                int i0 = (int) Math.floor(searchScrollF);
                int yoff = (int) Math.round((searchScrollF - i0) * 19.0);
                int y = listTop - yoff;
                for (int i = i0; i <= i0 + vrows && i < sugg.size(); i++) {
                    if (mouseY >= y && mouseY <= y + 18) {
                        ListRow row = sugg.get(i);
                        if (row.item() == null) {
                            if (mouseX >= panX2 - 26 && mouseX <= panX2 - 10) {
                                toggleHiddenMod(row.ns());
                                computeSearchSuggestionsKeepScroll();
                                return true;
                            }
                            if (expandedMods.contains(row.ns())) {
                                expandedMods.remove(row.ns());
                            } else {
                                expandedMods.add(row.ns());
                            }
                            computeSearchSuggestionsKeepScroll();
                            sound(1.0F);
                            return true;
                        }
                        SearchItem it = row.item();
                        if (!it.isBiome() && mouseX >= panX2 - 46 && mouseX <= panX2 - 30) {
                            toggleFilter(it.id());
                            return true;
                        }
                        if (!it.isBiome() && mouseX >= panX2 - 26 && mouseX <= panX2 - 10) {
                            toggleHidden(it);
                            computeSearchSuggestionsKeepScroll();
                            return true;
                        }
                        if (hasShiftDown()) {
                            activateSearch(it, true);
                            return true;
                        }
                        if (it.isBiome()) {
                            activateSearch(it, false);
                        } else {
                            openDetail(it);
                            sound(1.0F);
                        }
                        return true;
                    }
                    y += 19;
                }
                return true;
            }
            searchOpen = false;
            return true;
        }
        return false;
    }

    private void ensureSearchSelVisible() {
        int maxRows = Math.max(3, (mBottom - (mTop + 22) - 77) / 19);
        if (searchSel < searchScroll) {
            searchScroll = searchSel;
        } else if (searchSel >= searchScroll + maxRows) {
            searchScroll = searchSel - maxRows + 1;
        }
        searchScroll = Mth.clamp(searchScroll, 0, Math.max(0, sugg.size() - maxRows));
    }

    @SuppressWarnings("unchecked")
    private void activateSearch(SearchItem it, boolean filterOnly) {
        if (!it.isBiome() && filterOnly) {
            toggleFilter(it.id());
            searchOpen = false;
            return;
        }
        searchOpen = false;
        if (!it.isBiome() && map != null && map.currentWorld && !structHome.isEmpty()
                && !map.canGenerateHere((Holder<Structure>) it.holder())) {
            ResourceKey<Level> home = structHome.get(it.id());
            if (home == null) {
                notice = I18nGet("panoptic.seed.nowhere");
                copiedUntil = System.currentTimeMillis() + 3500L;
                sound(0.8F);
                return;
            }
            if (home != mapDim) {
                mapDim = home;
                apply();
            }
        }
        notice = I18nGet("panoptic.seed.searching");
        copiedUntil = System.currentTimeMillis() + 60000L;
        sound(1.1F);
        SeedMap m = map;
        int g = generation;
        int cx = (int) camX;
        int cz = (int) camZ;
        String nm = it.name();
        structPool.submit(() -> {
            BlockPos p;
            try {
                p = it.isBiome()
                        ? m.locateBiome((Holder<Biome>) it.holder(), new BlockPos(cx, 64, cz))
                        : structs.locateOnMap(m, it.id(), cx, cz, g);
            } catch (Throwable t) {
                p = null;
            }
            if (g != generation) {
                return;
            }
            pendingFound = p;
            pendingFoundName = nm;
            pendingFoundDone = true;
        });
    }

    private boolean searchKeyPressed(int keyCode) {
        if (detailItem != null) {
            if (keyCode == 256) {
                detailItem = null;
            } else if (keyCode == 257 || keyCode == 335) {
                SearchItem it = detailItem;
                detailItem = null;
                activateSearch(it, false);
            }
            return true;
        }
        switch (keyCode) {
            case 256 -> searchOpen = false;
            case 264 -> {
                searchSel = Math.min(Math.max(0, sugg.size() - 1), searchSel + 1);
                ensureSearchSelVisible();
            }
            case 265 -> {
                searchSel = Math.max(0, searchSel - 1);
                ensureSearchSelVisible();
            }
            case 257, 335 -> {
                if (!sugg.isEmpty()) {
                    ListRow row = sugg.get(Mth.clamp(searchSel, 0, sugg.size() - 1));
                    if (row.item() == null) {
                        if (expandedMods.contains(row.ns())) {
                            expandedMods.remove(row.ns());
                        } else {
                            expandedMods.add(row.ns());
                        }
                        computeSearchSuggestionsKeepScroll();
                    } else {
                        activateSearch(row.item(), hasShiftDown());
                    }
                }
            }
            default -> {
                TextOps.Res r = TextOps.key(searchText, searchCaret, searchTextSel, keyCode,
                        (hasControlDown() ? 2 : 0) | (hasShiftDown() ? 1 : 0), 256);
                if (r.handled) {
                    searchText = r.text;
                    searchCaret = r.caret;
                    searchTextSel = r.sel;
                    if (r.changed) {
                        computeSearchSuggestions();
                    }
                }
            }
        }
        return true;
    }

    private void drawFoundPulse(GuiGraphics g) {
        if (foundPos == null || System.currentTimeMillis() > foundUntil) {
            return;
        }
        int sx = (int) Math.round(mcx() + (foundPos.getX() - camX) * zoom);
        int sy = (int) Math.round(mcy() + (foundPos.getZ() - camZ) * zoom);
        long t = System.currentTimeMillis() % 900L;
        int r = 6 + (int) (t * 16L / 900L);
        ringDisc(g, sx, sy, r, 0xFFE8C06C);
        ringDisc(g, sx, sy, 4, 0xFFFFFFFF);
    }

    private void drawSlimeChunks(GuiGraphics g) {
        if (zoom < 2.0 || map == null || map.dim != Level.OVERWORLD) {
            return;
        }
        int cx0 = Mth.floor(camX + (mLeft - mcx()) / zoom) >> 4;
        int cx1 = Mth.floor(camX + (mRight - mcx()) / zoom) >> 4;
        int cz0 = Mth.floor(camZ + (mTop - mcy()) / zoom) >> 4;
        int cz1 = Mth.floor(camZ + (mBottom - mcy()) / zoom) >> 4;
        if ((long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1) > 6000) {
            return;
        }
        int s = (int) Math.round(16 * zoom);
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (!slimeChunk(cx, cz)) {
                    continue;
                }
                int sx = (int) Math.round(mcx() + ((double) (cx << 4) - camX) * zoom);
                int sy = (int) Math.round(mcy() + ((double) (cz << 4) - camZ) * zoom);
                g.fill(sx, sy, sx + s, sy + s, 0x3340D848);
                GuiStyle.rect(g, sx, sy, sx + s, sy + s, 0x5540D848);
            }
        }
    }

    private boolean slimeChunk(int cx, int cz) {
        slimeRnd.setSeed(map.seed + (long) (cx * cx * 4987142) + (long) (cx * 5947611)
                + (long) (cz * cz) * 4392871L + (long) (cz * 389711) ^ 0x3ad8025fL);
        return slimeRnd.nextInt(10) == 0;
    }

    private void drawGrid(GuiGraphics g) {
        if (zoom < 0.45) {
            return;
        }
        int step = 256;
        double wl = camX + (mLeft - mcx()) / zoom;
        double wr = camX + (mRight - mcx()) / zoom;
        double wt = camZ + (mTop - mcy()) / zoom;
        double wb = camZ + (mBottom - mcy()) / zoom;
        for (int x = Mth.floor(wl / step) * step; x <= wr; x += step) {
            int sx = (int) Math.round(mcx() + (x - camX) * zoom);
            g.fill(sx, mTop, sx + 1, mBottom, x == 0 ? GuiStyle.T(0x55E8C06C) : 0x22000000);
        }
        for (int z = Mth.floor(wt / step) * step; z <= wb; z += step) {
            int sy = (int) Math.round(mcy() + (z - camZ) * zoom);
            g.fill(mLeft, sy, mRight, sy + 1, z == 0 ? GuiStyle.T(0x55E8C06C) : 0x22000000);
        }
    }

    private void drawStructures(GuiGraphics g, int mouseX, int mouseY) {
        structs.collect(map, structPool, camX, camZ, zoom, mLeft, mTop, mRight, mBottom);
        drawStructProgress(g);
        if (zoom < ModSettings.get(ModSettings.STRUCT_MIN_ZOOM)) {
            return;
        }
        boolean small = structs.visible().size() > 220 || zoom < 0.6;
        int drawn = 0;
        for (SeedMap.Placed p : structs.visible()) {
            if (drawn > 12000) {
                break;
            }
            ResourceLocation pid = p.structure().unwrapKey().map(ResourceKey::location).orElse(null);
            if (pid != null && SeedPrefs.HIDDEN.contains(pid) && !SeedPrefs.FILTERS.contains(pid)) {
                continue;
            }
            if (!SeedPrefs.FILTERS.isEmpty() && !SeedPrefs.FILTERS.contains(pid)) {
                continue;
            }
            int sx = (int) Math.round(mcx() + (p.x() + 0.5 - camX) * zoom);
            int sy = (int) Math.round(mcy() + (p.z() + 0.5 - camZ) * zoom);
            if (sx < mLeft - 9 || sx > mRight + 9 || sy < mTop - 9 || sy > mBottom + 9) {
                continue;
            }
            drawn++;
            int c = StructVisuals.color(p.structure());
            boolean hov = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy) <= 36;
            if (small && !hov) {
                g.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF15100A);
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, c);
            } else {
                drawPlate(g, sx, sy, c, hov, StructVisuals.icon(p.structure()));
            }
        }
    }

    private void drawStructProgress(GuiGraphics g) {
        int structWant = structs.want();
        int structLoaded = structs.loaded();
        if (structWant <= 0 || structLoaded >= structWant) {
            return;
        }
        String s = I18n.get("panoptic.seed.structs_loading", structLoaded, structWant);
        int tw = this.font.width(s);
        int x2 = mRight - 6;
        int x1 = x2 - tw - 16;
        int y1 = mTop + 6;
        int y2 = y1 + 13;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        g.fill(x1, y1, x2, y2, GuiStyle.T(0xD01A150D));
        GuiStyle.rect(g, x1, y1, x2, y2, GuiStyle.T(0xFF6A5630));
        int phase = (int) ((System.currentTimeMillis() / 250L) % 4L);
        String spin = switch (phase) {
            case 0 -> "◐";
            case 1 -> "◓";
            case 2 -> "◑";
            default -> "◒";
        };
        g.drawString(this.font, spin, x1 + 4, y1 + 3, GuiStyle.ACCENT, false);
        g.drawString(this.font, s, x1 + 13, y1 + 3, GuiStyle.MUTED, false);
        g.pose().popPose();
    }

    private void drawPlate(GuiGraphics g, int sx, int sy, int c, boolean hov, ItemStack icon) {
        int r = hov ? 9 : 8;
        fillDisc(g, sx + 1, sy + 1, r, 0x44000000);
        fillDisc(g, sx, sy, r, 0xFF14100A);
        fillDisc(g, sx, sy, r - 1, 0xFF2A2216);
        ringDisc(g, sx, sy, r - 1, c);
        if (hov) {
            ringDisc(g, sx, sy, r, 0xFFFFFFFF);
        }
        g.pose().pushPose();
        g.pose().translate(sx - 5.5F, sy - 5.5F, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderFakeItem(icon, 0, 0);
        g.pose().popPose();
    }

    private void fillDisc(GuiGraphics g, int cx, int cy, int r, int color) {
        int[] spans = Disc.spans(r);
        for (int dy = -r; dy <= r; dy++) {
            int half = spans[dy + r];
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private void ringDisc(GuiGraphics g, int cx, int cy, int r, int color) {
        int[] spans = Disc.spans(r);
        for (int dy = -r; dy <= r; dy++) {
            int half = spans[dy + r];
            g.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, color);
            g.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private SeedMap.Placed structUnder(int mouseX, int mouseY) {
        if (map == null || zoom < ModSettings.get(ModSettings.STRUCT_MIN_ZOOM)
                || mouseX < mLeft || mouseX > mRight || mouseY < mTop || mouseY > mBottom) {
            return null;
        }
        SeedMap.Placed best = null;
        int bd = 30;
        for (SeedMap.Placed p : structs.visible()) {
            ResourceLocation pid = p.structure().unwrapKey().map(ResourceKey::location).orElse(null);
            if (pid != null && SeedPrefs.HIDDEN.contains(pid) && !SeedPrefs.FILTERS.contains(pid)) {
                continue;
            }
            if (!SeedPrefs.FILTERS.isEmpty() && !SeedPrefs.FILTERS.contains(pid)) {
                continue;
            }
            int sx = (int) Math.round(mcx() + (p.x() + 0.5 - camX) * zoom);
            int sy = (int) Math.round(mcy() + (p.z() + 0.5 - camZ) * zoom);
            int d = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy);
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void drawMarkers(GuiGraphics g) {
        if (this.minecraft.level != null && map.dim == Level.OVERWORLD) {
            BlockPos sp = this.minecraft.level.getSharedSpawnPos();
            int sx = (int) Math.round(mcx() + (sp.getX() - camX) * zoom);
            int sy = (int) Math.round(mcy() + (sp.getZ() - camZ) * zoom);
            g.drawString(this.font, "✦", sx - 3, sy - 4, 0xFFFFE08A, true);
        }
        if (map.currentWorld && this.minecraft.player != null
                && this.minecraft.player.level().dimension() == map.dim) {
            int px = (int) Math.round(mcx() + (this.minecraft.player.getX() - camX) * zoom);
            int py = (int) Math.round(mcy() + (this.minecraft.player.getZ() - camZ) * zoom);
            long t = System.currentTimeMillis() % 1500L;
            int pr = 7 + (int) (t * 8L / 1500L);
            int pa = 0x70 - (int) (t * 0x70 / 1500L);
            ringDisc(g, px, py, pr, (pa << 24) | 0x35D2F2);
            for (int i = -5; i <= 5; i++) {
                int half = 5 - Math.abs(i);
                g.fill(px - half, py + i, px + half + 1, py + i + 1, 0xFF06131A);
            }
            for (int i = -3; i <= 3; i++) {
                int half = 3 - Math.abs(i);
                g.fill(px - half, py + i, px + half + 1, py + i + 1, 0xFF35D2F2);
            }
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
        }
    }

    private void drawCrosshair(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX < mLeft || mouseX > mRight || mouseY < mTop || mouseY > mBottom) {
            return;
        }
        g.fill(mouseX, mTop, mouseX + 1, mBottom, 0x18FFFFFF);
        g.fill(mLeft, mouseY, mRight, mouseY + 1, 0x18FFFFFF);
    }

    private void renderTopBar(GuiGraphics g, int left, int top, int right, int mouseX, int mouseY) {
        g.fill(left + 1, top + 1, right - 1, top + TOP_H - 1, GuiStyle.TITLE);
        g.fill(left + 1, top + TOP_H - 1, right - 1, top + TOP_H, GuiStyle.T(0x3DE8C06C));
        g.drawString(this.font, "◎", left + 7, top + 6, GuiStyle.ACCENT, false);
        g.drawString(this.font, Component.translatable("panoptic.seed.title"), left + 19, top + 6, GuiStyle.TEXT, false);
        helpHover = HelpCard.icon(g, this.font,
                left + 19 + this.font.width(Component.translatable("panoptic.seed.title")) + 6, top + 3, mouseX, mouseY);

        boolean closeHov = mouseX >= right - 18 && mouseX <= right - 5 && mouseY >= top + 4 && mouseY <= top + 17;
        if (closeHov) {
            g.fill(right - 18, top + 4, right - 5, top + 17, 0x40FF5B5B);
        }
        Icons.iconCross(g, right - 14, top + 7, closeHov ? 0xFFFF6B6B : GuiStyle.MUTED);
        boolean centerHov = mouseX >= right - 36 && mouseX <= right - 21 && mouseY >= top + 4 && mouseY <= top + 17;
        if (centerHov) {
            g.fill(right - 36, top + 4, right - 21, top + 17, GuiStyle.ROWHOVER);
        }
        Icons.iconCenter(g, right - 33, top + 6, centerHov ? GuiStyle.ACCENT : GuiStyle.MUTED);
        tip(right - 36, top + 4, right - 21, top + 17, mouseX, mouseY, "panoptic.seed.tip.center");

        seedY = top + 20;
        int sLeft = left + 7;
        int labelW = this.font.width(Component.translatable("panoptic.seed.label")) + 4;
        g.drawString(this.font, Component.translatable("panoptic.seed.label"), sLeft, seedY + 4, GuiStyle.MUTED, false);
        okX2 = right - 6;
        okX1 = okX2 - 22;
        wX2 = okX1 - 4;
        wX1 = wX2 - 54;
        hbX2 = wX1 - 4;
        hbX1 = hbX2 - 16;
        fldX1 = sLeft + labelW;
        fldX2 = hbX1 - 4;

        g.fill(fldX1, seedY, fldX2, seedY + 14, GuiStyle.SEARCH_BG);
        GuiStyle.rect(g, fldX1, seedY, fldX2, seedY + 14, seedFocused ? GuiStyle.ACCENT : GuiStyle.T(0xFF6A5630));
        int caretPx = this.font.width(seedText.substring(0, Mth.clamp(seedCaret, 0, seedText.length())));
        int tx = fldX1 + 5;
        int scx = Math.max(0, caretPx - (fldX2 - tx - 4) + 2);
        scis(g, fldX1 + 1, seedY, fldX2 - 1, seedY + 14);
        if (seedFocused) {
            TextOps.drawSel(g, this.font, seedText, seedCaret, seedSel, tx - scx, seedY + 2, seedY + 12);
        }
        g.drawString(this.font, seedText, tx - scx, seedY + 4, GuiStyle.TEXT, false);
        if (seedFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cxp = tx - scx + caretPx;
            g.fill(cxp, seedY + 3, cxp + 1, seedY + 12, GuiStyle.ACCENT);
        }
        g.disableScissor();

        topButton(g, wX1, wX2, mouseX, mouseY, I18nGet("panoptic.seed.world"), GuiStyle.MUTED, GuiStyle.BORDER, false);
        topButton(g, okX1, okX2, mouseX, mouseY, I18nGet("panoptic.seed.go"), 0xFFB6E08A, 0xFF8FBF6A, true);
        boolean hHov = mouseX >= hbX1 && mouseX <= hbX2 && mouseY >= seedY && mouseY <= seedY + 14;
        g.fill(hbX1, seedY, hbX2, seedY + 14, hHov || histOpen ? GuiStyle.ROWHOVER : 0x14FFFFFF);
        GuiStyle.rect(g, hbX1, seedY, hbX2, seedY + 14, hHov || histOpen ? GuiStyle.ACCENT : GuiStyle.BORDER);
        Icons.iconClock(g, hbX1 + 4, seedY + 3, histOpen ? GuiStyle.ACCENT : GuiStyle.MUTED);
        if (!histOpen) {
            tip(hbX1, seedY, hbX2, seedY + 14, mouseX, mouseY, "panoptic.seed.tip.history");
        }
    }

    private void rebuildHistView() {
        histView.clear();
        for (Map.Entry<String, Boolean> e : SeedPrefs.HISTORY.entrySet()) {
            if (e.getValue()) {
                histView.add(e.getKey());
            }
        }
        for (Map.Entry<String, Boolean> e : SeedPrefs.HISTORY.entrySet()) {
            if (!e.getValue()) {
                histView.add(e.getKey());
            }
        }
    }

    private void renderHistory(GuiGraphics g, int mouseX, int mouseY) {
        rebuildHistView();
        int x1 = fldX1;
        int x2 = wX2;
        int y1 = seedY + 16;
        int rows = Math.max(1, Math.min(histView.isEmpty() ? 1 : histView.size(), (mBottom - y1 - 6) / 13));
        histRows = rows;
        histScroll = Mth.clamp(histScroll, 0, Math.max(0, histView.size() - rows));
        double hk = animDt <= 0 ? 1.0 : 1.0 - Math.exp(-animDt * 16.0);
        histScrollF += (histScroll - histScrollF) * hk;
        if (Math.abs(histScroll - histScrollF) < 0.01) {
            histScrollF = histScroll;
        }
        int y2 = y1 + rows * 13 + 4;
        histY1 = y1;
        histY2 = y2;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 460.0F);
        g.fill(x1 + 3, y1 + 3, x2 + 3, y2 + 3, 0x66000000);
        GuiStyle.panel(g, x1, y1, x2, y2);
        if (histView.isEmpty()) {
            g.drawCenteredString(this.font, I18nGet("panoptic.seed.hist.empty"), (x1 + x2) / 2, y1 + 5, GuiStyle.DIM);
        }
        int hi0 = (int) Math.floor(histScrollF);
        int hyoff = (int) Math.round((histScrollF - hi0) * 13.0);
        scis(g, x1 + 1, y1 + 2, x2 - 1, y1 + 2 + rows * 13);
        int y = y1 + 2 - hyoff;
        String histTip = null;
        for (int i = hi0; i <= hi0 + rows && i < histView.size(); i++) {
            String s = histView.get(i);
            boolean fav = Boolean.TRUE.equals(SeedPrefs.HISTORY.get(s));
            boolean rowHov = mouseX >= x1 && mouseX <= x2 && mouseY >= y && mouseY <= y + 12;
            if (rowHov) {
                g.fill(x1 + 1, y, x2 - 1, y + 13, GuiStyle.ROWHOVER);
            }
            g.drawString(this.font, "★", x1 + 4, y + 3,
                    fav ? 0xFFE8C06C : rowHov ? GuiStyle.MUTED : GuiStyle.DIM, false);
            g.drawString(this.font, this.font.plainSubstrByWidth(s, x2 - x1 - 34), x1 + 16, y + 3,
                    s.equals(seedText) ? GuiStyle.ACCENT : GuiStyle.TEXT, false);
            boolean delHov = rowHov && mouseX >= x2 - 14;
            Icons.iconCross(g, x2 - 11, y + 3, delHov ? 0xFFFF6B6B : GuiStyle.DIM);
            if (rowHov && mouseX <= x1 + 14) {
                histTip = I18nGet(fav ? "panoptic.seed.tip.unfav" : "panoptic.seed.tip.fav");
            } else if (delHov) {
                histTip = I18nGet("panoptic.seed.tip.delete");
            }
            y += 13;
        }
        g.disableScissor();
        if (histTip != null) {
            int tw = this.font.width(histTip);
            int tx = Mth.clamp(mouseX + 8, mLeft, mRight - tw - 8);
            int ty = mouseY - 14;
            g.fill(tx - 3, ty - 2, tx + tw + 3, ty + 10, GuiStyle.T(0xF01A150D));
            GuiStyle.rect(g, tx - 3, ty - 2, tx + tw + 3, ty + 10, GuiStyle.ACCENT);
            g.drawString(this.font, histTip, tx, ty, GuiStyle.TEXT, false);
        }
        g.pose().popPose();
    }

    private boolean clickHistory(double mouseX, double mouseY) {
        int x1 = fldX1;
        int x2 = wX2;
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= histY1 && mouseY <= histY2) {
            int hi0 = (int) Math.floor(histScrollF);
            int hyoff = (int) Math.round((histScrollF - hi0) * 13.0);
            int y = histY1 + 2 - hyoff;
            for (int i = hi0; i <= hi0 + histRows && i < histView.size(); i++) {
                if (mouseY >= y && mouseY <= y + 12) {
                    String s = histView.get(i);
                    if (mouseX <= x1 + 14) {
                        SeedPrefs.HISTORY.put(s, !Boolean.TRUE.equals(SeedPrefs.HISTORY.get(s)));
                        SeedPrefs.saveHistory();
                        sound(1.2F);
                        return true;
                    }
                    if (mouseX >= x2 - 14) {
                        SeedPrefs.HISTORY.remove(s);
                        SeedPrefs.saveHistory();
                        sound(0.9F);
                        return true;
                    }
                    seedText = s;
                    seedCaret = s.length();
                    seedSel = -1;
                    apply();
                    return true;
                }
                y += 13;
            }
            return true;
        }
        histOpen = false;
        return true;
    }

    private boolean topButton(GuiGraphics g, int x1, int x2, int mouseX, int mouseY, String label, int fg, int border, boolean go) {
        boolean hov = mouseX >= x1 && mouseX <= x2 && mouseY >= seedY && mouseY <= seedY + 14;
        g.fill(x1, seedY, x2, seedY + 14, hov ? (go ? 0x4400FF66 : GuiStyle.ROWHOVER) : 0x14FFFFFF);
        GuiStyle.rect(g, x1, seedY, x2, seedY + 14, hov ? border : GuiStyle.BORDER);
        g.drawString(this.font, label, x1 + (x2 - x1 - this.font.width(label)) / 2, seedY + 4, hov ? fg : GuiStyle.MUTED, false);
        return hov;
    }

    private void renderBottomBar(GuiGraphics g, int left, int bottom, int right, int mouseX, int mouseY) {
        int by = bottom - BOT_H;
        g.fill(left + 1, by, right - 1, bottom - 1, GuiStyle.TITLE);
        g.fill(left + 1, by, right - 1, by + 1, GuiStyle.T(0x3DE8C06C));
        if (map == null) {
            return;
        }
        int wx = (int) Math.floor(camX + (mouseX - mcx()) / zoom);
        int wz = (int) Math.floor(camZ + (mouseY - mcy()) / zoom);
        boolean over = mouseX >= mLeft && mouseX <= mRight && mouseY >= mTop && mouseY <= mBottom;
        String coords = over ? ("X " + wx + "   Z " + wz) : "—";
        g.drawString(this.font, coords, left + 7, by + 5, GuiStyle.TEXT, false);
        if (over) {
            long hk = TileLayer.key(0, wx >> 2, wz >> 2);
            if (hk != hoverReqKey) {
                hoverReqKey = hk;
                SeedMap m = map;
                int fwx = wx;
                int fwz = wz;
                pool.submit(() -> {
                    if (m != null) {
                        hoverBiome = m.biome(fwx, fwz);
                    }
                });
            }
            Holder<Biome> b = hoverBiome;
            if (b != null) {
                int bx = left + 7 + this.font.width(coords) + 12;
                g.fill(bx, by + 4, bx + 8, by + 12, map.color(b));
                GuiStyle.rect(g, bx, by + 4, bx + 8, by + 12, 0x66000000);
                g.drawString(this.font, SeedText.biomeName(b), bx + 12, by + 5, GuiStyle.MUTED, false);
            }
        }
        int scaleLeft = drawScaleBar(g, right, by + 5);
        String hint = I18nGet(map.currentWorld ? "panoptic.seed.click_tp" : "panoptic.seed.click_copy");
        g.drawString(this.font, hint, scaleLeft - 10 - this.font.width(hint), by + 5, GuiStyle.DIM, false);
    }

    private int drawScaleBar(GuiGraphics g, int right, int y) {
        double blocks = 100.0 / zoom;
        double mag = Math.pow(10, Math.floor(Math.log10(blocks)));
        double norm = blocks / mag;
        double nice = norm >= 5 ? 5 : norm >= 2 ? 2 : 1;
        int units = (int) Math.max(1, nice * mag);
        int px = Math.max(10, (int) Math.round(units * zoom));
        int bx2 = right - 10;
        int bx1 = bx2 - px;
        String lbl = scaleLabel(units);
        int lblX = bx1 - 6 - this.font.width(lbl);
        int cy = y + 4;
        g.fill(bx1, cy, bx2, cy + 1, GuiStyle.ACCENT);
        g.fill(bx1, cy - 3, bx1 + 1, cy + 4, GuiStyle.ACCENT);
        g.fill(bx2 - 1, cy - 3, bx2, cy + 4, GuiStyle.ACCENT);
        if (px >= 40) {
            g.fill(bx1 + px / 2, cy - 1, bx1 + px / 2 + 1, cy + 2, GuiStyle.ACCENT);
        }
        g.drawString(this.font, lbl, lblX, y, GuiStyle.MUTED, false);
        return lblX;
    }

    private static String scaleLabel(int units) {
        if (units >= 1000 && units % 1000 == 0) {
            return I18n.get("panoptic.seed.scale.thousand", units / 1000);
        }
        String key = units == 1 ? "panoptic.seed.scale.one" : units == 2 ? "panoptic.seed.scale.two" : "panoptic.seed.scale.many";
        return I18n.get(key, units);
    }

    private double mcx() {
        return (mLeft + mRight) / 2.0;
    }

    private double mcy() {
        return (mTop + mBottom) / 2.0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int right = this.width - MARGIN;
        int top = MARGIN;
        if (mouseY >= top + 4 && mouseY <= top + 17) {
            if (mouseX >= right - 18 && mouseX <= right - 5) {
                onClose();
                return true;
            }
            if (mouseX >= right - 36 && mouseX <= right - 21) {
                centerOnPlayer();
                return true;
            }
        }
        if (histOpen) {
            if (mouseY >= seedY && mouseY <= seedY + 14 && mouseX >= hbX1 && mouseX <= hbX2) {
                histOpen = false;
                sound(0.9F);
                return true;
            }
            seedFocused = false;
            return clickHistory(mouseX, mouseY);
        }
        seedFocused = false;
        if (mouseY >= seedY && mouseY <= seedY + 14) {
            if (mouseX >= hbX1 && mouseX <= hbX2) {
                histOpen = true;
                histScroll = 0;
                histScrollF = 0;
                dimListOpen = false;
                searchOpen = false;
                sound(1.0F);
                return true;
            }
            if (mouseX >= fldX1 && mouseX <= fldX2) {
                seedFocused = true;
                seedSel = -1;
                placeSeedCaret((int) mouseX, fldX1 + 5);
                return true;
            }
            if (mouseX >= wX1 && mouseX <= wX2) {
                seedText = String.valueOf(SeedMap.worldSeed());
                seedCaret = seedText.length();
                apply();
                return true;
            }
            if (mouseX >= okX1 && mouseX <= okX2) {
                apply();
                return true;
            }
        }
        if (mouseX >= mLeft && mouseX <= mRight && mouseY >= mTop && mouseY <= mBottom) {
            if (button == 0 && clickSearch(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && clickDimSelector(mouseX, mouseY)) {
                return true;
            }
            if (button == 0) {
                dragging = true;
                moved = false;
                pressX = mouseX;
                pressY = mouseY;
                lastDragX = mouseX;
                lastDragY = mouseY;
                return true;
            }
            if (button == 1) {
                SeedMap.Placed s = structUnder((int) mouseX, (int) mouseY);
                if (s != null) {
                    SearchItem it = searchItemFor(s.structure());
                    if (it != null) {
                        searchOpen = true;
                        dimListOpen = false;
                        openDetail(it);
                        sound(1.0F);
                        return true;
                    }
                }
                copy(Mth.floor(camX + (mouseX - mcx()) / zoom) + " " + Mth.floor(camZ + (mouseY - mcy()) / zoom));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            if (Math.abs(mouseX - pressX) > 2 || Math.abs(mouseY - pressY) > 2) {
                moved = true;
            }
            camX -= (mouseX - lastDragX) / zoom;
            camZ -= (mouseY - lastDragY) / zoom;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            if (!moved && mouseX >= mLeft && mouseX <= mRight && mouseY >= mTop && mouseY <= mBottom) {
                SeedMap.Placed s = structUnder((int) mouseX, (int) mouseY);
                if (s != null && hasShiftDown()) {
                    ResourceLocation pid = s.structure().unwrapKey().map(ResourceKey::location).orElse(null);
                    if (pid != null) {
                        toggleFilter(pid);
                    }
                } else if (s != null) {
                    clickAt(s.x(), s.z());
                } else {
                    clickAt((int) Math.floor(camX + (mouseX - mcx()) / zoom),
                            (int) Math.floor(camZ + (mouseY - mcy()) / zoom));
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (histOpen && mouseX >= fldX1 && mouseX <= wX2 && mouseY >= histY1 && mouseY <= histY2) {
            histScroll = Mth.clamp(histScroll - (int) Math.signum(delta), 0,
                    Math.max(0, histView.size() - histRows));
            return true;
        }
        if (searchOpen) {
            int maxRows = Math.max(3, (mBottom - (mTop + 22) - 77) / 19);
            searchScroll = Mth.clamp(searchScroll - (int) Math.signum(delta) * 3, 0, Math.max(0, sugg.size() - maxRows));
            return true;
        }
        if (dimListOpen && mouseX >= dimChipX1 && mouseX <= dimChipX1 + dimListW
                && mouseY >= dimChipY2 && mouseY <= dimChipY2 + 2 + dimRowsVis * 13 + 12) {
            dimScroll = Mth.clamp(dimScroll - (int) Math.signum(delta), 0,
                    Math.max(0, dims.size() - dimRowsVis));
            return true;
        }
        if (mouseX >= mLeft && mouseX <= mRight && mouseY >= mTop && mouseY <= mBottom) {
            zoomTarget = Mth.clamp(zoomTarget * (delta > 0 ? 1.25 : 0.8), ModSettings.mapMinZoom(), ModSettings.mapMaxZoom());
            zoomAnchorX = mouseX;
            zoomAnchorY = mouseY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, 0, delta);
    }

    private void clickAt(int wx, int wz) {
        if (map != null && map.currentWorld && this.minecraft.player != null) {
            if (this.minecraft.player.level().dimension() == map.dim) {
                this.minecraft.player.connection.sendCommand("tp " + wx + " ~ " + wz);
            } else {
                this.minecraft.player.connection.sendCommand(
                        "execute in " + map.dim.location() + " run tp @s " + wx + " ~ " + wz);
            }
            sound(1.5F);
        } else {
            copy(wx + " " + wz);
        }
    }

    private void centerOnPlayer() {
        if (this.minecraft.player != null) {
            camX = this.minecraft.player.getX();
            camZ = this.minecraft.player.getZ();
            sound(1.0F);
        }
    }

    private void apply() {
        seedFocused = false;
        histOpen = false;
        long s = SeedMap.parseSeed(seedText);
        SeedPrefs.histAdd(String.valueOf(s));
        rebuild(s);
        sound(1.2F);
    }

    private void placeSeedCaret(int mouseX, int tx) {
        int target = mouseX - tx;
        int idx = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i <= seedText.length(); i++) {
            int d = Math.abs(this.font.width(seedText.substring(0, i)) - target);
            if (d < best) {
                best = d;
                idx = i;
            }
        }
        seedCaret = idx;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (histOpen && keyCode == 256) {
            histOpen = false;
            return true;
        }
        if (!seedFocused && ModBinds.matchesNow(ModBinds.Bind.SEARCH, keyCode)) {
            searchOpen = !searchOpen;
            if (searchOpen) {
                buildSearchItems();
                computeSearchSuggestions();
                dimListOpen = false;
                seedFocused = false;
            }
            return true;
        }
        if (searchOpen) {
            return searchKeyPressed(keyCode);
        }
        if (!seedFocused && !histOpen && ModBinds.matchesNow(ModBinds.Bind.CENTER, keyCode)) {
            centerOnPlayer();
            return true;
        }
        if (seedFocused) {
            if (keyCode == 256) {
                seedFocused = false;
                seedSel = -1;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                seedSel = -1;
                apply();
                return true;
            }
            TextOps.Res r = TextOps.key(seedText, seedCaret, seedSel, keyCode, modifiers, 64);
            if (r.handled) {
                seedText = r.text;
                seedCaret = r.caret;
                seedSel = r.sel;
            }
            return true;
        }
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (ModBinds.key(ModBinds.Bind.SEEDMAP).code == keyCode && ModBinds.key(ModBinds.Bind.SEEDMAP).code != -1) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c >= ' ' && c != 127) {
            if (searchOpen) {
                TextOps.Res r = TextOps.type(searchText, searchCaret, searchTextSel, c, 256);
                if (r.handled) {
                    searchText = r.text;
                    searchCaret = r.caret;
                    searchTextSel = r.sel;
                    computeSearchSuggestions();
                }
                return true;
            }
            if (seedFocused) {
                TextOps.Res r = TextOps.type(seedText, seedCaret, seedSel, c, 64);
                if (r.handled) {
                    seedText = r.text;
                    seedCaret = r.caret;
                    seedSel = r.sel;
                }
                return true;
            }
        }
        return super.charTyped(c, modifiers);
    }

    private void copy(String s) {
        this.minecraft.keyboardHandler.setClipboard(s);
        notice = I18nGet("panoptic.seed.copied") + " " + s;
        copiedUntil = System.currentTimeMillis() + 1200;
        sound(1.6F);
    }

    private void sound(float pitch) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private static String I18nGet(String key) {
        return I18n.get(key);
    }

    private void scis(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void onClose() {
        generation++;
        if (map != null) {
            map.closed = true;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        if (structPool != null) {
            structPool.shutdownNow();
            structPool = null;
        }
        tileLayer.clear();
        this.minecraft.setScreen(parent);
    }

}