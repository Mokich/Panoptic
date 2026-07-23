package net.mokich.panoptic.screen.trade;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.data.trade.VillagerBook;
import net.mokich.panoptic.data.trade.VillagerBook.Prof;
import net.mokich.panoptic.data.trade.VillagerBook.TradeRow;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.Scroll;
import net.mokich.panoptic.api.ui.TextOps;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.config.Perms;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.mokich.panoptic.network.SpawnVillagerPacket;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VillagerBrowserScreen extends Screen implements TextTyping {
    @Override
    public boolean gmtTyping() {
        return queryFocused;
    }
    private static final int HEADER_H = 13;
    private static final int ROW_H = 22;
    private static final int TRADE_H = 24;
    private static final int SUGG_ROW_H = 18;

    private record ItemRef(Prof prof, TradeRow row) {}
    private record ItemEntry(String key, ItemStack icon, String name, String id, List<ItemRef> refs, String blob) {}

    private final Screen parent;
    private static List<Prof> CACHE;
    private static String CACHE_LANG;
    private static volatile boolean BUILDING;
    private boolean loading;
    private List<Prof> all = List.of();
    private List<ItemEntry> allItems = List.of();
    private List<Object> rows = new ArrayList<>();
    private Prof selected;
    private ItemEntry selectedItem;
    private int mode;

    private String query = "";
    private int caret;
    private int querySel = -1;
    private boolean queryFocused;

    private final Set<TradeRow> expanded = new HashSet<>();
    private final Set<Integer> levelFilter = new HashSet<>();
    private int dirFilter;

    private double scrollL;
    private double targetL;
    private double scrollR;
    private double targetR;
    private double scrollS;
    private double targetS;
    private long lastNano;
    private boolean dragL;
    private boolean dragR;
    private boolean dragS;
    private int leftView;
    private int rightView;
    private int rightViewTop;
    private int suggView;
    private int suggViewTop;
    private int suggContentH;
    private final List<Object[]> itemZones = new ArrayList<>();
    private int spawnBtnX1, spawnBtnX2, spawnBtnY1, spawnBtnY2;

    private int leftX1;
    private int leftX2;
    private int rightX1;
    private int rightX2;
    private int topY;
    private int listY1;
    private int botY;

    private final Map<ResourceLocation, LivingEntity> previews = new HashMap<>();
    private ItemStack hoverStack = ItemStack.EMPTY;
    private String hoverTip;
    private boolean helpHover;
    private final List<int[]> chipZones = new ArrayList<>();
    private final List<Object[]> refZones = new ArrayList<>();
    private final List<ItemEntry> sugg = new ArrayList<>();
    private int suggSel;
    private boolean suggHidden;
    private String suggComputedFor = "";
    private String lastSugToken = "\u0000";

    public VillagerBrowserScreen(Screen parent) {
        super(Component.translatable("panoptic.vill.title"));
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
        int m = 8;
        topY = m + 20;
        botY = this.height - m;
        leftX1 = m;
        leftX2 = m + Math.min(250, this.width / 3);
        rightX1 = leftX2 + 6;
        rightX2 = this.width - m;
        listY1 = topY + 22;
        ensureData();
        rebuildRows();
    }

    private void ensureData() {
        if (!all.isEmpty()) {
            return;
        }
        String lang = this.minecraft.options.languageCode;
        if (CACHE != null && lang.equals(CACHE_LANG)) {
            applyData(CACHE);
            return;
        }
        CACHE = null;
        loading = true;
        if (BUILDING) {
            return;
        }
        BUILDING = true;
        Thread t = new Thread(() -> {
            List<Prof> built;
            try {
                built = VillagerBook.build();
            } catch (Throwable x) {
                built = List.of();
            }
            List<Prof> result = built;
            this.minecraft.execute(() -> {
                CACHE = result;
                CACHE_LANG = lang;
                BUILDING = false;
            });
        }, "panoptic-villager-build");
        t.setDaemon(true);
        t.start();
    }

    private void applyData(List<Prof> data) {
        all = data;
        buildItemIndex();
        for (Prof p : all) {
            if (!p.wandering() && !p.trades().isEmpty()) {
                selected = p;
                break;
            }
        }
        if (selected == null && !all.isEmpty()) {
            selected = all.get(0);
        }
        loading = false;
        rebuildRows();
    }

    private void drawActionButtons(GuiGraphics g, int mouseX, int mouseY) {
        int gap = 6;
        String all = I18n.get("panoptic.vill.copy_all");
        String copy = I18n.get("panoptic.vill.copy");
        String back = I18n.get("panoptic.vill.back");
        int wBack = this.font.width(back) + 16;
        int wCopy = this.font.width(copy) + 16;
        int wAll = this.font.width(all) + 16;
        int xBack = rightX2 - 2 - wBack;
        int xCopy = xBack - gap - wCopy;
        int xAll = xCopy - gap - wAll;
        actionButton(g, 202, xAll, 6, wAll, all, "panoptic.vill.copy_all_tip", mouseX, mouseY);
        actionButton(g, 201, xCopy, 6, wCopy, copy, "panoptic.vill.copy_tip", mouseX, mouseY);
        actionButton(g, 200, xBack, 6, wBack, back, null, mouseX, mouseY);
    }

    private void drawHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.vill.help2"));
        bullets.add(Component.translatable("panoptic.vill.help3"));
        bullets.add(Component.translatable("panoptic.vill.help4"));
        bullets.add(Component.translatable("panoptic.vill.help5"));
        List<HelpCard.KeyHint> keys = List.of(ModBinds.hint(ModBinds.Bind.SEARCH, "panoptic.vill.help_search"));
        HelpCard.render(g, this.font, this.width, this.height, helpHover,
                Component.translatable("panoptic.vill.help_title"), Component.translatable("panoptic.vill.help1"), bullets, keys);
    }

    private void actionButton(GuiGraphics g, int id, int x, int y, int w, String label,
                              String tipKey, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        GuiStyle.button(g, this.font, x, y, x + w, y + 16, label, hov, true);
        if (hov && tipKey != null) {
            hoverTip = I18n.get(tipKey);
        }
        chipZones.add(new int[]{x, y, x + w, y + 16, id});
    }

    private void buildItemIndex() {
        Map<String, ItemEntry> map = new LinkedHashMap<>();
        for (Prof p : all) {
            for (TradeRow t : p.trades()) {
                if (t.variants == null) {
                    indexRow(map, p, t);
                } else {
                    for (TradeRow v : t.variants) {
                        indexRow(map, p, v);
                    }
                }
            }
        }
        List<ItemEntry> list = new ArrayList<>(map.values());
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        allItems = list;
    }

    private void indexRow(Map<String, ItemEntry> map, Prof p, TradeRow t) {
        for (ItemStack s : new ItemStack[]{t.costA, t.costB, t.result}) {
            if (s == null || s.isEmpty() || s.is(Items.EMERALD)) {
                continue;
            }
            ItemEntry e = map.computeIfAbsent(stackKey(s), key -> makeEntry(s));
            boolean dup = false;
            for (ItemRef r : e.refs()) {
                if (r.row() == t) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                e.refs().add(new ItemRef(p, t));
            }
        }
    }

    private static String stackKey(ItemStack s) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String tag = s.getComponentsPatch().isEmpty() ? "" : s.getComponentsPatch().toString();
        return id + "#" + tag;
    }

    private static ItemEntry makeEntry(ItemStack src) {
        ItemStack icon = src.copy();
        icon.setCount(1);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(icon.getItem());
        String idStr = id == null ? "?" : id.toString();
        String name = icon.getHoverName().getString();
        StringBuilder blob = new StringBuilder();
        blob.append(name.toLowerCase(Locale.ROOT)).append(' ').append(idStr).append(' ');
        String suffix = "";
        var ench = EnchantmentHelper.getEnchantmentsForCrafting(icon);
        if (!ench.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (var en : ench.entrySet()) {
                ResourceLocation ek = en.getKey().unwrapKey().map(k -> k.location()).orElse(null);
                String eName = en.getKey().value().description().getString();
                int lv = en.getIntValue();
                String lvl = I18n.exists("enchantment.level." + lv)
                        ? I18n.get("enchantment.level." + lv) : String.valueOf(lv);
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(eName).append(' ').append(lvl);
                blob.append(eName.toLowerCase(Locale.ROOT)).append(' ');
                if (ek != null) {
                    blob.append(ek).append(' ').append(ek.getPath()).append(' ');
                }
            }
            suffix = " §7(" + names + "§7)";
        }
        if (!icon.getComponentsPatch().isEmpty()) {
            blob.append(icon.getComponentsPatch().toString().toLowerCase(Locale.ROOT)).append(' ');
        }
        String ns = id == null ? "" : id.getNamespace();
        String modName = FabricLoader.getInstance().getModContainer(ns)
                .map(c -> c.getMetadata().getName()).orElse(ns);
        blob.append(ns).append(' ').append(modName.toLowerCase(Locale.ROOT)).append(' ');
        return new ItemEntry(stackKey(icon), icon, name + suffix, idStr, new ArrayList<>(), blob.toString());
    }

    private void rebuildRows() {
        rows = new ArrayList<>();
        String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (mode == 0) {
            String lastMod = null;
            for (Prof p : all) {
                if (!matchesAll(p.searchBlob(), terms)) {
                    continue;
                }
                if (!p.modName().equals(lastMod)) {
                    rows.add(p.modName());
                    lastMod = p.modName();
                }
                rows.add(p);
            }
        } else {
            for (ItemEntry e : allItems) {
                if (matchesAll(e.blob(), terms)) {
                    rows.add(e);
                }
            }
        }
        targetL = 0;
        scrollL = 0;
    }

    private static boolean matchesAll(String blob, String[] terms) {
        for (String t : terms) {
            if (!t.isEmpty() && !blob.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private void computeSuggestions() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (!q.equals(lastSugToken)) {
            lastSugToken = q;
            suggSel = 0;
            suggHidden = false;
            targetS = 0;
            scrollS = 0;
            suggComputedFor = "";
        }
        if (suggHidden || q.isEmpty()) {
            sugg.clear();
            suggComputedFor = "";
            return;
        }
        if (q.equals(suggComputedFor)) {
            return;
        }
        sugg.clear();
        String[] terms = q.split("\\s+");
        for (ItemEntry e : allItems) {
            if (sugg.size() >= 200) {
                break;
            }
            if (matchesAll(e.blob(), terms)) {
                sugg.add(e);
            }
        }
        if (suggSel >= sugg.size()) {
            suggSel = Math.max(0, sugg.size() - 1);
        }
        suggComputedFor = q;
    }

    private void ensureSuggVisible() {
        if (suggView <= 0) {
            return;
        }
        int selTop = suggSel * SUGG_ROW_H;
        int selBot = selTop + SUGG_ROW_H;
        if (selTop < targetS) {
            targetS = selTop;
        } else if (selBot > targetS + suggView) {
            targetS = selBot - suggView;
        }
        targetS = Mth.clamp(targetS, 0, Math.max(0, suggContentH - suggView));
    }

    private void acceptSuggestion() {
        if (sugg.isEmpty() || suggSel < 0 || suggSel >= sugg.size()) {
            return;
        }
        ItemEntry pick = sugg.get(suggSel);
        mode = 1;
        selectedItem = pick;
        targetR = 0;
        scrollR = 0;
        suggHidden = true;
        queryFocused = false;
        lastSugToken = "\u0000";
        rebuildRows();
        sound(1.2F);
    }

    private void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        if (sugg.isEmpty() || !queryFocused) {
            return;
        }
        int rowH = SUGG_ROW_H;
        int w = leftX2 - leftX1;
        int sx1 = leftX1;
        int sy1 = topY + 18;
        int maxRows = Math.max(3, (botY - sy1 - 4) / rowH);
        int visN = Math.min(sugg.size(), maxRows);
        int h = visN * rowH + 3;
        suggViewTop = sy1 + 2;
        suggView = visN * rowH;
        suggContentH = sugg.size() * rowH;
        targetS = Mth.clamp(targetS, 0, Math.max(0, suggContentH - suggView));
        int off = (int) Math.round(scrollS);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        GuiStyle.plate(g, sx1, sy1, sx1 + w, sy1 + h, GuiStyle.T(0xFF2A2317), GuiStyle.T(0xFF17120B), GuiStyle.BORDER_B);
        g.enableScissor(sx1, suggViewTop, sx1 + w, suggViewTop + suggView);
        for (int i = 0; i < sugg.size(); i++) {
            int ry = suggViewTop + i * rowH - off;
            if (ry + rowH < suggViewTop || ry > suggViewTop + suggView) {
                continue;
            }
            ItemEntry e = sugg.get(i);
            boolean hov = mouseX >= sx1 && mouseX < sx1 + w - 6 && mouseY >= ry && mouseY < ry + rowH
                    && mouseY >= suggViewTop && mouseY < suggViewTop + suggView;
            if (hov) {
                suggSel = i;
            }
            if (i == suggSel) {
                GuiStyle.row(g, sx1 + 1, ry, sx1 + w - 1, ry + rowH, false, true);
            }
            g.renderFakeItem(e.icon(), sx1 + 4, ry + 1);
            String cnt = String.valueOf(e.refs().size());
            int cw = this.font.width(cnt);
            g.drawString(this.font, trimTip(e.name(), w - 36 - cw, hov), sx1 + 23, ry + 4,
                    i == suggSel ? GuiStyle.ACCENT : GuiStyle.TEXT);
            g.drawString(this.font, cnt, sx1 + w - 12 - cw, ry + 4, GuiStyle.DIM);
        }
        g.disableScissor();
        Scroll.bar(g, sx1 + w - 6, suggViewTop, suggViewTop + suggView, suggContentH, scrollS, dragS, mouseX, mouseY);
        g.pose().popPose();
    }

    private boolean rowMatchesQuery(TradeRow t) {
        if (query.trim().isEmpty()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        appendBlob(sb, t);
        if (t.variants != null) {
            for (TradeRow v : t.variants) {
                appendBlob(sb, v);
            }
        }
        String blob = sb.toString();
        for (String term : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!term.isEmpty() && blob.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static void appendBlob(StringBuilder sb, TradeRow t) {
        sb.append(stackBlob(t.costA)).append(stackBlob(t.costB)).append(stackBlob(t.result));
        if (t.note != null) {
            sb.append(t.note.toLowerCase(Locale.ROOT)).append(' ');
        }
    }

    private static String stackBlob(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return (key == null ? "" : key + " ") + s.getHoverName().getString().toLowerCase(Locale.ROOT) + " ";
    }

    private boolean passesFilters(TradeRow t) {
        if (!levelFilter.isEmpty() && !levelFilter.contains(t.level)) {
            return false;
        }
        if (dirFilter == 1 && !t.sellsToPlayer()) {
            return false;
        }
        if (dirFilter == 2 && t.sellsToPlayer()) {
            return false;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        animate();
        if (loading && CACHE != null && all.isEmpty()) {
            applyData(CACHE);
        }
        VillagerBook.resolvePending(selected);
        renderTransparentBackground(g);
        hoverStack = ItemStack.EMPTY;
        hoverTip = null;
        chipZones.clear();
        refZones.clear();
        itemZones.clear();

        g.drawString(this.font, Component.translatable("panoptic.vill.title").getString(), leftX1, 10, GuiStyle.ACCENT);
        helpHover = HelpCard.icon(g, this.font,
                leftX1 + this.font.width(I18n.get("panoptic.vill.title")) + 6, 7, mouseX, mouseY);
        drawTabs(g, mouseX, mouseY);
        drawActionButtons(g, mouseX, mouseY);
        drawSearch(g, mouseX, mouseY);
        if (queryFocused) {
            computeSuggestions();
        } else {
            sugg.clear();
            suggComputedFor = "";
        }
        drawList(g, mouseX, mouseY);
        if (mode == 0) {
            drawDetail(g, mouseX, mouseY);
        } else {
            drawItemDetail(g, mouseX, mouseY);
        }

        renderSuggestions(g, mouseX, mouseY);
        if (loading) {
            g.drawCenteredString(this.font, I18n.get("panoptic.vill.loading"),
                    (leftX1 + rightX2) / 2, botY / 2, GuiStyle.ACCENT);
        }
        super.render(g, mouseX, mouseY, partial);

        if (!hoverStack.isEmpty()) {
            g.renderTooltip(this.font, hoverStack, mouseX, mouseY);
        } else if (hoverTip != null) {
            g.renderTooltip(this.font, Component.literal(hoverTip), mouseX, mouseY);
        }
        drawHelp(g);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftX1 + this.font.width(I18n.get("panoptic.vill.title")) + 14 + HelpCard.SIZE + 6;
        for (int i = 0; i < 2; i++) {
            String label = I18n.get(i == 0 ? "panoptic.vill.tab.villagers" : "panoptic.vill.tab.items");
            int w = this.font.width(label) + 14;
            boolean active = mode == i;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= 6 && mouseY < 20;
            int top = active ? GuiStyle.T(0xFF3A2F1B) : hov ? GuiStyle.T(0xFF2E2618) : GuiStyle.T(0xFF241E14);
            int bot = active ? GuiStyle.T(0xFF241D11) : hov ? GuiStyle.T(0xFF1C170E) : GuiStyle.T(0xFF17120B);
            GuiStyle.plate(g, x, 6, x + w, 20, top, bot, active ? GuiStyle.ACCENT : GuiStyle.BORDER);
            if (active) {
                g.fill(x + 1, 18, x + w - 1, 20, GuiStyle.ACCENT);
            }
            g.drawString(this.font, label, x + 7, 9, active ? GuiStyle.TEXT : GuiStyle.MUTED);
            chipZones.add(new int[]{x, 7, x + w, 20, 100 + i});
            x += w + 4;
        }
    }

    private void animate() {
        long now = System.nanoTime();
        if (lastNano == 0) {
            lastNano = now;
        }
        double dt = Math.min((now - lastNano) / 1.0e9, 0.1);
        lastNano = now;
        scrollL = Scroll.ease(scrollL, targetL, dt, 16.0);
        scrollR = Scroll.ease(scrollR, targetR, dt, 16.0);
        scrollS = Scroll.ease(scrollS, targetS, dt, 16.0);
    }


    private void drawSearch(GuiGraphics g, int mouseX, int mouseY) {
        int y1 = topY;
        int y2 = topY + 18;
        g.fill(leftX1, y1, leftX2, y2, GuiStyle.SEARCH_BG);
        GuiStyle.rect(g, leftX1, y1, leftX2, y2, queryFocused ? GuiStyle.ACCENT : GuiStyle.BORDER);
        g.fill(leftX1 + 1, y1 + 1, leftX2 - 1, y1 + 2, 0x30000000);
        Icons.searchIcon(g, leftX1 + 6, y1 + 5, queryFocused ? GuiStyle.ACCENT : GuiStyle.MUTED);
        int tx = leftX1 + 18;
        g.enableScissor(tx, y1, leftX2 - 4, y2);
        if (queryFocused) {
            TextOps.drawSel(g, this.font, query, caret, querySel, tx, y1 + 3, y2 - 3);
        }
        g.drawString(this.font, query, tx, y1 + 5, GuiStyle.TEXT);
        if (queryFocused && System.currentTimeMillis() / 500 % 2 == 0) {
            int cx = tx + this.font.width(query.substring(0, Math.min(caret, query.length())));
            g.fill(cx, y1 + 4, cx + 1, y2 - 4, GuiStyle.ACCENT);
        }
        g.disableScissor();
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        GuiStyle.panel(g, leftX1, listY1, leftX2, botY);
        leftView = botY - 2 - (listY1 + 2);
        int leftContentH = listContentHeight();
        targetL = Mth.clamp(targetL, 0, Math.max(0, leftContentH - leftView));
        g.enableScissor(leftX1 + 2, listY1 + 2, leftX2 - 2, botY - 2);
        int y = listY1 + 3 - (int) Math.round(scrollL);
        for (Object o : rows) {
            int h = o instanceof String ? HEADER_H : ROW_H;
            if (y + h >= listY1 && y <= botY) {
                boolean hov = mouseX >= leftX1 && mouseX < leftX2 && mouseY >= y && mouseY < y + h
                        && mouseY >= listY1 && mouseY <= botY;
                if (o instanceof String mod) {
                    GuiStyle.rect(g, leftX1 + 1, y, leftX2 - 1, y + h - 1, GuiStyle.HEADER);
                    g.drawString(this.font, mod, leftX1 + 6, y + 3, GuiStyle.MUTED);
                } else if (o instanceof Prof p) {
                    drawProfRow(g, p, y, hov);
                } else if (o instanceof ItemEntry e) {
                    boolean sel = e == selectedItem;
                    if (sel) {
                        GuiStyle.rect(g, leftX1 + 1, y, leftX2 - 1, y + ROW_H - 1, GuiStyle.T(0x33E8C06C));
                        GuiStyle.rect(g, leftX1 + 1, y, leftX1 + 3, y + ROW_H - 1, GuiStyle.ACCENT);
                    } else if (hov) {
                        GuiStyle.rect(g, leftX1 + 1, y, leftX2 - 1, y + ROW_H - 1, GuiStyle.ROWHOVER);
                    }
                    g.renderFakeItem(e.icon(), leftX1 + 6, y + 3);
                    g.drawString(this.font, trimTip(e.name(), leftX2 - leftX1 - 60, hov), leftX1 + 26, y + 3, GuiStyle.TEXT);
                    String cnt = String.valueOf(e.refs().size());
                    g.drawString(this.font, cnt, leftX2 - 8 - this.font.width(cnt), y + 3, GuiStyle.DIM);
                    g.drawString(this.font, trimTip(e.id(), leftX2 - leftX1 - 34, hov), leftX1 + 26, y + 12, GuiStyle.DIM);
                }
            }
            y += h;
        }
        g.disableScissor();
        Scroll.bar(g, leftX2 - 6, listY1 + 2, botY - 2, leftContentH, scrollL, dragL, mouseX, mouseY);
    }

    private void drawProfRow(GuiGraphics g, Prof p, int y, boolean hov) {
        boolean sel = p == selected;
        if (sel) {
            GuiStyle.rect(g, leftX1 + 1, y, leftX2 - 1, y + ROW_H - 1, GuiStyle.T(0x33E8C06C));
            GuiStyle.rect(g, leftX1 + 1, y, leftX1 + 3, y + ROW_H - 1, GuiStyle.ACCENT);
        } else if (hov) {
            GuiStyle.rect(g, leftX1 + 1, y, leftX2 - 1, y + ROW_H - 1, GuiStyle.ROWHOVER);
        }
        List<ItemStack> ws = p.workstations();
        ItemStack icon = ws.isEmpty() ? new ItemStack(Items.EMERALD)
                : ws.get((int) (System.currentTimeMillis() / 1100L % ws.size()));
        g.renderFakeItem(icon, leftX1 + 6, y + 3);
        if (ws.size() > 1) {
            String n = String.valueOf(ws.size());
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(this.font, n, leftX1 + 21 - this.font.width(n), y + 13, GuiStyle.ACCENT, true);
            g.pose().popPose();
        }
        g.drawString(this.font, trimTip(p.name(), leftX2 - leftX1 - 60, hov), leftX1 + 26, y + 3, GuiStyle.TEXT);
        String cnt = String.valueOf(p.trades().size());
        g.drawString(this.font, cnt, leftX2 - 8 - this.font.width(cnt), y + 3, GuiStyle.DIM);
        g.drawString(this.font, trimTip(p.id().toString(), leftX2 - leftX1 - 34, hov), leftX1 + 26, y + 12, GuiStyle.DIM);
    }

    private String trim(String s, int w) {
        if (this.font.width(s) <= w) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, w - 6) + "…";
    }

    private String trimTip(String s, int w, boolean hov) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= w) {
            return s;
        }
        if (hov && hoverTip == null) {
            hoverTip = s;
        }
        return this.font.plainSubstrByWidth(s, w - 6) + "…";
    }

    private boolean isHover(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    private int detailContentHeight(Prof p) {
        int content = 4;
        int lastLevel = Integer.MIN_VALUE;
        for (TradeRow t : visibleTrades(p)) {
            if (t.level != lastLevel) {
                lastLevel = t.level;
                content += HEADER_H;
            }
            content += TRADE_H;
            if (t.variants != null && expanded.contains(t)) {
                content += t.variants.size() * TRADE_H;
            }
        }
        return content;
    }

    private int rightContentNow() {
        if (mode == 0 && selected != null) {
            return detailContentHeight(selected);
        }
        if (mode == 1 && selectedItem != null) {
            return 4 + selectedItem.refs().size() * TRADE_H;
        }
        return 0;
    }

    private int listContentHeight() {
        int h = 6;
        for (Object o : rows) {
            h += o instanceof String ? HEADER_H : ROW_H;
        }
        return h;
    }

    private void drawDetail(GuiGraphics g, int mouseX, int mouseY) {
        GuiStyle.panel(g, rightX1, topY, rightX2, botY);
        if (selected == null) {
            return;
        }
        Prof p = selected;
        int headH = 74;
        GuiStyle.rect(g, rightX1 + 1, topY + 1, rightX2 - 1, topY + headH, GuiStyle.PANEL2);
        drawPreview(g, p, rightX1 + 40, topY + headH - 6);

        int infoX = rightX1 + 80;
        g.drawString(this.font, trimTip(p.name(), rightX2 - infoX - 90, isHover(mouseX, mouseY, infoX, topY + 6, rightX2 - 90, topY + 18)), infoX, topY + 8, GuiStyle.ACCENT);
        String idStr = p.id().toString();
        boolean idHov = isHover(mouseX, mouseY, infoX, topY + 19, infoX + this.font.width(idStr) + 10, topY + 29);
        g.drawString(this.font, idStr, infoX, topY + 20, idHov ? GuiStyle.ACCENT : GuiStyle.MUTED);
        Icons.iconCopy(g, infoX + this.font.width(idStr) + 3, topY + 20, idHov ? GuiStyle.ACCENT : GuiStyle.DIM);
        if (idHov) {
            hoverTip = I18n.get("panoptic.vill.copy_id");
        }
        boolean modHov = isHover(mouseX, mouseY, infoX, topY + 30, infoX + this.font.width(p.modName()) + 10, topY + 40);
        g.drawString(this.font, p.modName(), infoX, topY + 31, modHov ? GuiStyle.ACCENT : GuiStyle.DIM);
        Icons.iconCopy(g, infoX + this.font.width(p.modName()) + 3, topY + 31, modHov ? GuiStyle.ACCENT : GuiStyle.DIM);
        if (modHov) {
            hoverTip = I18n.get("panoptic.vill.copy_mod", p.id().getNamespace());
        }
        if (!p.workstations().isEmpty()) {
            g.drawString(this.font, I18n.get("panoptic.vill.workstation"), infoX, topY + 46, GuiStyle.MUTED);
            int wx = infoX + this.font.width(I18n.get("panoptic.vill.workstation")) + 6;
            for (ItemStack ws : p.workstations()) {
                GuiStyle.slot(g, wx - 1, topY + 41);
                g.renderFakeItem(ws, wx, topY + 42);
                boolean wsHov = mouseX >= wx - 1 && mouseX < wx + 17 && mouseY >= topY + 41 && mouseY < topY + 59;
                if (wsHov) {
                    g.fill(wx, topY + 42, wx + 16, topY + 58, 0x30E8C06C);
                    hoverStack = ws;
                }
                itemZones.add(new Object[]{wx - 1, topY + 41, ws});
                wx += 19;
            }
        } else if (p.wandering()) {
            g.drawString(this.font, I18n.get("panoptic.vill.wandering_note"), infoX, topY + 46, GuiStyle.DIM);
        }

        String spawn = I18n.get("panoptic.vill.spawn");
        int swp = this.font.width(spawn) + 16;
        spawnBtnX1 = rightX2 - 6 - swp;
        spawnBtnX2 = rightX2 - 6;
        spawnBtnY1 = topY + headH - 20;
        spawnBtnY2 = topY + headH - 4;
        boolean canSpawn = this.minecraft != null && Perms.allowed(Perms.Feature.TRADE_SPAWN)
                && (this.minecraft.getSingleplayerServer() != null || Perms.serverSynced());
        boolean spHov = canSpawn && isHover(mouseX, mouseY, spawnBtnX1, spawnBtnY1, spawnBtnX2, spawnBtnY2);
        GuiStyle.button(g, this.font, spawnBtnX1, spawnBtnY1, spawnBtnX2, spawnBtnY2, spawn, spHov, canSpawn);
        if (spHov) {
            hoverTip = I18n.get("panoptic.vill.spawn_tip");
        } else if (!canSpawn && isHover(mouseX, mouseY, spawnBtnX1, spawnBtnY1, spawnBtnX2, spawnBtnY2)) {
            hoverTip = I18n.get("panoptic.vill.spawn_sp");
        }

        int chipY = topY + headH + 3;
        drawFilterChips(g, chipY, mouseX, mouseY);

        int viewY1 = chipY + 16;
        rightViewTop = viewY1;
        rightView = botY - 1 - viewY1;
        int rc = detailContentHeight(p);
        targetR = Mth.clamp(targetR, 0, Math.max(0, rc - rightView));
        g.enableScissor(rightX1 + 1, viewY1, rightX2 - 1, botY - 1);
        int y = viewY1 + 2 - (int) Math.round(scrollR);
        List<TradeRow> visible = visibleTrades(p);
        if (visible.isEmpty()) {
            g.drawString(this.font, I18n.get("panoptic.vill.no_trades"), rightX1 + 8, y + 4, GuiStyle.DIM);
        }
        int lastLevel = Integer.MIN_VALUE;
        for (TradeRow t : visible) {
            if (t.level != lastLevel) {
                lastLevel = t.level;
                if (y + HEADER_H >= viewY1 && y <= botY) {
                    g.fillGradient(rightX1 + 2, y, rightX2 - 2, y + HEADER_H - 1, GuiStyle.T(0xFF3A2F1B), GuiStyle.T(0xFF241D11));
                    g.fill(rightX1 + 2, y, rightX2 - 2, y + 1, 0x30FFE7B0);
                    g.fill(rightX1 + 5, y + 3, rightX1 + 7, y + HEADER_H - 4, GuiStyle.ACCENT);
                    g.drawString(this.font, levelName(p, t.level), rightX1 + 11, y + 3, GuiStyle.ACCENT);
                }
                y += HEADER_H;
            }
            if (y + TRADE_H >= viewY1 && y <= botY) {
                drawTrade(g, t, rightX1 + 8, y, mouseX, mouseY, viewY1, true);
            }
            refZones.add(new Object[]{y, t, null});
            y += TRADE_H;
            if (t.variants != null && expanded.contains(t)) {
                for (TradeRow v : t.variants) {
                    if (y + TRADE_H >= viewY1 && y <= botY) {
                        drawTrade(g, v, rightX1 + 24, y, mouseX, mouseY, viewY1, false);
                        g.fill(rightX1 + 14, y + 2, rightX1 + 16, y + TRADE_H - 4, GuiStyle.BORDER_B);
                    }
                    y += TRADE_H;
                }
            }
        }
        g.disableScissor();
        Scroll.bar(g, rightX2 - 6, viewY1, botY - 1, rc, scrollR, dragR, mouseX, mouseY);
    }

    private List<TradeRow> visibleTrades(Prof p) {
        List<TradeRow> out = new ArrayList<>();
        for (TradeRow t : p.trades()) {
            if (passesFilters(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private void drawFilterChips(GuiGraphics g, int y, int mouseX, int mouseY) {
        int x = rightX1 + 8;
        for (int lvl = 1; lvl <= 5; lvl++) {
            x = filterChip(g, x, y, String.valueOf(lvl), levelFilter.contains(lvl), mouseX, mouseY, lvl);
        }
        x += 8;
        x = filterChip(g, x, y, I18n.get("panoptic.vill.dir.sell"), dirFilter == 1, mouseX, mouseY, 11);
        filterChip(g, x, y, I18n.get("panoptic.vill.dir.buy"), dirFilter == 2, mouseX, mouseY, 12);
    }

    private int filterChip(GuiGraphics g, int x, int y, String label, boolean active,
                           int mouseX, int mouseY, int id) {
        int w = this.font.width(label) + 10;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
        g.fillGradient(x, y, x + w, y + 12, active ? GuiStyle.T(0x55E8C06C) : hov ? 0x26FFFFFF : 0x12FFFFFF,
                active ? GuiStyle.T(0x22E8C06C) : hov ? 0x14FFFFFF : 0x07FFFFFF);
        if (active) {
            g.renderOutline(x, y, w, 12, GuiStyle.ACCENT);
        }
        g.drawCenteredString(this.font, label, x + w / 2, y + 2, active ? GuiStyle.TEXT : GuiStyle.MUTED);
        chipZones.add(new int[]{x, y, x + w, y + 12, id});
        return x + w + 4;
    }

    private String levelName(Prof p, int level) {
        if (p.wandering()) {
            return I18n.get(level <= 1 ? "panoptic.vill.wander_common" : "panoptic.vill.wander_rare");
        }
        String base = I18n.exists("merchant.level." + level) ? I18n.get("merchant.level." + level) : "";
        return I18n.get("panoptic.vill.level", level) + (base.isEmpty() ? "" : " — " + base);
    }

    private void drawTrade(GuiGraphics g, TradeRow t, int px, int y, int mouseX, int mouseY,
                           int viewY1, boolean interactive) {
        boolean hl = rowMatchesQuery(t);
        boolean inView = mouseY >= viewY1 && mouseY <= botY;
        boolean rowHov = inView && mouseX >= rightX1 + 2 && mouseX < rightX2 - 2
                && mouseY >= y && mouseY < y + TRADE_H - 1;
        int plateTop = hl ? GuiStyle.T(0xFF3A2F1B) : rowHov ? GuiStyle.T(0xFF2E2618) : GuiStyle.T(0xFF241E14);
        int plateBot = hl ? GuiStyle.T(0xFF241D11) : rowHov ? GuiStyle.T(0xFF1C170E) : GuiStyle.T(0xFF17120B);
        GuiStyle.plate(g, rightX1 + 2, y, rightX2 - 2, y + TRADE_H - 2, plateTop, plateBot,
                hl ? GuiStyle.ACCENT : GuiStyle.BORDER);
        int dirColor = t.sellsToPlayer() ? 0xFF6CBF7C : 0xFF6C9BDF;
        g.fill(rightX1 + 3, y + 1, rightX1 + 5, y + TRADE_H - 3, dirColor);
        int x = px;
        if (t.costA.isEmpty() && t.result.isEmpty()) {
            String bn = t.note == null ? "" : t.note;
            g.drawString(this.font, "§c! " + trimTip(bn, rightX2 - x - 90, rowHov), x, y + 8, 0xFFE08A6C);
            if (rowHov && t.note != null && hoverTip == null) {
                hoverTip = t.note.replaceAll("§.", "");
            }
            return;
        }
        x = drawStack(g, t.costA, x, y, mouseX, mouseY, inView);
        if (!t.costB.isEmpty()) {
            g.drawString(this.font, "+", x + 2, y + 8, GuiStyle.MUTED);
            x = drawStack(g, t.costB, x + 11, y, mouseX, mouseY, inView);
        }
        Icons.iconArrowRight(g, x + 4, y + 8, GuiStyle.ACCENT);
        x = drawStack(g, t.result, x + 17, y, mouseX, mouseY, inView);
        StringBuilder info = new StringBuilder();
        if (t.sourceMod != null) {
            info.append("§6").append(t.sourceMod).append("§r · ");
        }
        if (t.variants != null && interactive) {
            info.append(I18n.get("panoptic.vill.variants", t.variants.size())).append("  ");
        } else {
            if (t.random && t.variants == null) {
                info.append(I18n.get("panoptic.vill.random")).append(" · ");
            }
            info.append(I18n.get("panoptic.vill.uses", t.maxUses)).append(" · ").append(t.xp).append(" XP");
        }
        String infoS = info.toString();
        int iw = this.font.width(infoS);
        int infoX = rightX2 - 8 - iw;
        if (t.note != null) {
            int avail = infoX - 6 - (x + 4);
            if (avail > 8) {
                String note = trimTip(t.note, avail, rowHov);
                g.drawString(this.font, note, x + 4, y + 8, GuiStyle.MUTED);
            } else if (rowHov && hoverTip == null) {
                hoverTip = t.note.replaceAll("§.", "");
            }
        }
        if (infoX > px + 4) {
            boolean varHov = t.variants != null && interactive && inView
                    && mouseX >= infoX && mouseX <= rightX2 - 8 && mouseY >= y && mouseY < y + TRADE_H;
            int ic = t.variants != null && interactive ? (varHov ? GuiStyle.ACCENT : GuiStyle.MUTED) : GuiStyle.DIM;
            g.drawString(this.font, infoS, infoX, y + 8, ic);
            if (t.variants != null && interactive) {
                if (expanded.contains(t)) {
                    Icons.iconTriDown(g, rightX2 - 14, y + 10, ic);
                } else {
                    Icons.iconTriRight(g, rightX2 - 13, y + 9, ic);
                }
            }
        }
        if (t.sourceMod != null && t.variants != null && interactive && inView
                && mouseX >= px && mouseX < infoX && mouseY >= y && mouseY < y + TRADE_H && hoverTip == null) {
            hoverTip = I18n.get("panoptic.vill.from", t.sourceMod);
        }
    }

    private int drawStack(GuiGraphics g, ItemStack s, int x, int y, int mouseX, int mouseY, boolean inView) {
        if (s.isEmpty()) {
            return x;
        }
        int sy = y + 2;
        GuiStyle.slot(g, x, sy);
        boolean hov = inView && mouseX >= x && mouseX < x + 18 && mouseY >= sy && mouseY < sy + 18;
        if (hov) {
            g.fill(x + 1, sy + 1, x + 17, sy + 17, 0x30E8C06C);
            hoverStack = s;
        }
        g.renderFakeItem(s, x + 1, sy + 1);
        g.renderItemDecorations(this.font, s, x + 1, sy + 1);
        if (!s.is(Items.EMERALD)) {
            itemZones.add(new Object[]{x, sy, s});
        }
        return x + 20;
    }

    private void drawItemDetail(GuiGraphics g, int mouseX, int mouseY) {
        GuiStyle.panel(g, rightX1, topY, rightX2, botY);
        if (selectedItem == null) {
            g.drawString(this.font, I18n.get("panoptic.vill.pick_item"), rightX1 + 10, topY + 10, GuiStyle.DIM);
            return;
        }
        ItemEntry e = selectedItem;
        int headH = 40;
        GuiStyle.rect(g, rightX1 + 1, topY + 1, rightX2 - 1, topY + headH, GuiStyle.PANEL2);
        g.pose().pushPose();
        g.pose().translate(rightX1 + 24, topY + 20, 0);
        g.pose().scale(1.8F, 1.8F, 1.0F);
        g.renderFakeItem(e.icon(), -8, -8);
        g.pose().popPose();
        g.drawString(this.font, e.name(), rightX1 + 46, topY + 9, GuiStyle.ACCENT);
        String id = e.id();
        g.drawString(this.font, id, rightX1 + 46, topY + 21, GuiStyle.MUTED);
        if (mouseX >= rightX1 + 46 && mouseX <= rightX1 + 46 + this.font.width(id)
                && mouseY >= topY + 20 && mouseY <= topY + 29) {
            hoverTip = I18n.get("panoptic.vill.copy_id");
        }

        int viewY1 = topY + headH + 2;
        rightViewTop = viewY1;
        rightView = botY - 1 - viewY1;
        int rc = 4 + e.refs().size() * TRADE_H;
        targetR = Mth.clamp(targetR, 0, Math.max(0, rc - rightView));
        g.enableScissor(rightX1 + 1, viewY1, rightX2 - 1, botY - 1);
        int y = viewY1 + 2 - (int) Math.round(scrollR);
        for (ItemRef ref : e.refs()) {
            if (y + TRADE_H >= viewY1 && y <= botY) {
                boolean hov = mouseX >= rightX1 + 1 && mouseX < rightX2 - 1
                        && mouseY >= y && mouseY < y + TRADE_H && mouseY >= viewY1 && mouseY <= botY;
                if (hov) {
                    hoverTip = I18n.get("panoptic.vill.goto");
                }
                TradeRow t = ref.row();
                GuiStyle.plate(g, rightX1 + 2, y, rightX2 - 2, y + TRADE_H - 2,
                        hov ? GuiStyle.T(0xFF2E2618) : GuiStyle.T(0xFF241E14), hov ? GuiStyle.T(0xFF1C170E) : GuiStyle.T(0xFF17120B), GuiStyle.BORDER);
                int dirColor = t.sellsToPlayer() ? 0xFF6CBF7C : 0xFF6C9BDF;
                g.fill(rightX1 + 3, y + 1, rightX1 + 5, y + TRADE_H - 3, dirColor);
                boolean refHov = mouseX >= rightX1 + 2 && mouseX < rightX2 - 2 && mouseY >= y && mouseY < y + TRADE_H;
                String tag = trimTip(ref.prof().name(), 110, refHov) + " §8" + t.level;
                g.drawString(this.font, tag, rightX1 + 9, y + 8, GuiStyle.TEXT);
                int x = rightX1 + 130;
                x = drawStack(g, t.costA, x, y, mouseX, mouseY, true);
                if (!t.costB.isEmpty()) {
                    g.drawString(this.font, "+", x + 2, y + 8, GuiStyle.MUTED);
                    x = drawStack(g, t.costB, x + 11, y, mouseX, mouseY, true);
                }
                Icons.iconArrowRight(g, x + 4, y + 8, GuiStyle.ACCENT);
                x = drawStack(g, t.result, x + 17, y, mouseX, mouseY, true);
                if (t.note != null) {
                    g.drawString(this.font, trimTip(t.note, rightX2 - x - 12, refHov), x + 4, y + 8, GuiStyle.MUTED);
                }
            }
            refZones.add(new Object[]{y, ref.row(), ref.prof()});
            y += TRADE_H;
        }
        g.disableScissor();
        Scroll.bar(g, rightX2 - 6, viewY1, botY - 1, rc, scrollR, dragR, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics g, Prof p, int cx, int cy) {
        LivingEntity ent = previews.computeIfAbsent(p.id(), id -> createPreview(p));
        if (ent == null) {
            return;
        }
        float yaw = System.currentTimeMillis() % 7000L / 7000.0F * 360.0F;
        ent.yBodyRot = yaw;
        ent.yBodyRotO = yaw;
        ent.setYRot(yaw);
        ent.yHeadRot = yaw;
        ent.yHeadRotO = yaw;
        ent.setXRot(0.0F);
        Quaternionf pose = new Quaternionf().rotationZ((float) Math.PI);
        g.enableScissor(rightX1 + 2, topY + 2, rightX1 + 78, topY + 73);
        try {
            InventoryScreen.renderEntityInInventory(g, cx, cy, 28, new Vector3f(), pose, null, ent);
        } catch (Throwable ignored) {
        }
        g.disableScissor();
    }

    private LivingEntity createPreview(Prof p) {
        try {
            if (this.minecraft == null || this.minecraft.level == null) {
                return null;
            }
            if (p.wandering()) {
                return EntityType.WANDERING_TRADER.create(this.minecraft.level);
            }
            Villager v = EntityType.VILLAGER.create(this.minecraft.level);
            if (v != null && p.profession() != null) {
                v.setVillagerData(v.getVillagerData().setProfession(p.profession()).setLevel(5));
            }
            return v;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void copySelected() {
        if (selected == null || this.minecraft == null || mode != 0) {
            if (mode == 1 && selectedItem != null && this.minecraft != null) {
                this.minecraft.keyboardHandler.setClipboard(selectedItem.id());
            }
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(dumpProf(selected));
    }

    private void copyAll() {
        if (this.minecraft == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Prof p : all) {
            sb.append(dumpProf(p)).append('\n');
        }
        this.minecraft.keyboardHandler.setClipboard(sb.toString());
    }

    private String dumpProf(Prof p) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(p.name()).append(" (").append(p.id()).append(") — ").append(p.modName()).append('\n');
        if (!p.workstations().isEmpty()) {
            sb.append(I18n.get("panoptic.vill.workstation")).append(' ');
            for (int i = 0; i < p.workstations().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(BuiltInRegistries.ITEM.getKey(p.workstations().get(i).getItem()));
            }
            sb.append('\n');
        }
        int lastLevel = Integer.MIN_VALUE;
        for (TradeRow t : p.trades()) {
            if (t.level != lastLevel) {
                lastLevel = t.level;
                sb.append(levelName(p, t.level)).append(":\n");
            }
            dumpRow(sb, t, "  ");
            if (t.variants != null) {
                for (TradeRow v : t.variants) {
                    dumpRow(sb, v, "    ");
                }
            }
        }
        return sb.toString();
    }

    private void dumpRow(StringBuilder sb, TradeRow t, String indent) {
        sb.append(indent).append(dumpStack(t.costA));
        if (!t.costB.isEmpty()) {
            sb.append(" + ").append(dumpStack(t.costB));
        }
        sb.append(" -> ").append(dumpStack(t.result));
        if (t.note != null) {
            sb.append(" | ").append(t.note.replace("§8", "").replace("§7", ""));
        }
        if (t.variants != null) {
            sb.append(" | ").append(I18n.get("panoptic.vill.variants", t.variants.size()));
        }
        if (t.sourceMod != null) {
            sb.append(" | ").append(I18n.get("panoptic.vill.from", t.sourceMod));
        }
        sb.append(" | ").append(I18n.get("panoptic.vill.uses", t.maxUses)).append(", ").append(t.xp).append(" XP\n");
    }

    private static String dumpStack(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return "?";
        }
        return s.getCount() + "x " + BuiltInRegistries.ITEM.getKey(s.getItem());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!sugg.isEmpty() && queryFocused) {
            if (suggContentH > suggView && mx >= leftX2 - 7 && mx <= leftX2
                    && my >= suggViewTop && my <= suggViewTop + suggView) {
                dragS = true;
                targetS = scrollS = Scroll.thumbToScroll((int) my, suggViewTop, suggViewTop + suggView, suggContentH);
                return true;
            }
            if (mx >= leftX1 && mx < leftX2 - 7 && my >= suggViewTop && my < suggViewTop + suggView) {
                int off = (int) Math.round(scrollS);
                int idx = (int) ((my - suggViewTop + off) / SUGG_ROW_H);
                if (idx >= 0 && idx < sugg.size()) {
                    suggSel = idx;
                    acceptSuggestion();
                }
                return true;
            }
        }
        if (mx >= leftX2 - 7 && mx <= leftX2 && my >= listY1 + 2 && my <= botY - 2
                && listContentHeight() > leftView) {
            dragL = true;
            targetL = scrollL = Scroll.thumbToScroll((int) my, listY1 + 2, botY - 2, listContentHeight());
            return true;
        }
        if (mx >= rightX2 - 7 && mx <= rightX2 && my >= rightViewTop && my <= botY - 1
                && rightContentNow() > rightView) {
            dragR = true;
            targetR = scrollR = Scroll.thumbToScroll((int) my, rightViewTop, botY - 1, rightContentNow());
            return true;
        }
        if (button == 0 && mode == 0 && selected != null
                && isHover(mx, my, spawnBtnX1, spawnBtnY1, spawnBtnX2, spawnBtnY2)) {
            if (!Perms.allowed(Perms.Feature.TRADE_SPAWN)) {
                Perms.deny();
                return true;
            }
            if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
                VillagerBook.spawnWithTrades(selected);
                sound(1.4F);
                onClose();
            } else if (Perms.serverSynced()) {
                String id = selected.wandering() || selected.profession() == null ? ""
                        : String.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(selected.profession()));
                ClientPlayNetworking.send(
                        new SpawnVillagerPacket(id, selected.wandering()));
                sound(1.4F);
                onClose();
            }
            return true;
        }
        if (button == 0) {
            for (Object[] z : itemZones) {
                int ix = (Integer) z[0];
                int iy = (Integer) z[1];
                if (mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18) {
                    ItemStack s = (ItemStack) z[2];
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                    if (id != null && this.minecraft != null) {
                        String txt = id.toString();
                        if (!s.getComponentsPatch().isEmpty()) {
                            txt += s.getComponentsPatch();
                        }
                        this.minecraft.keyboardHandler.setClipboard(txt);
                        sound(1.4F);
                    }
                    return true;
                }
            }
        }
        for (int[] z : chipZones) {
            if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                sound(1.0F);
                applyChip(z[4]);
                return true;
            }
        }
        queryFocused = mx >= leftX1 && mx <= leftX2 && my >= topY && my <= topY + 18;
        if (queryFocused) {
            caret = query.length();
            return true;
        }
        if (mx >= leftX1 && mx <= leftX2 && my >= listY1 && my <= botY) {
            int y = listY1 + 3 - (int) Math.round(scrollL);
            for (Object o : rows) {
                int h = o instanceof String ? HEADER_H : ROW_H;
                if (my >= y && my < y + h) {
                    if (o instanceof Prof p) {
                        sound(1.1F);
                        selected = p;
                        targetR = 0;
                        scrollR = 0;
                        expanded.clear();
                    } else if (o instanceof ItemEntry e) {
                        sound(1.1F);
                        selectedItem = e;
                        targetR = 0;
                        scrollR = 0;
                    }
                    return true;
                }
                y += h;
            }
            return true;
        }
        if (mx > rightX1 && button == 0) {
            if (mode == 0 && selected != null) {
                int infoX = rightX1 + 80;
                String id = selected.id().toString();
                if (mx >= infoX && mx <= infoX + this.font.width(id) + 10 && my >= topY + 19 && my <= topY + 29) {
                    this.minecraft.keyboardHandler.setClipboard(id);
                    sound(1.4F);
                    return true;
                }
                if (mx >= infoX && mx <= infoX + this.font.width(selected.modName()) + 10
                        && my >= topY + 30 && my <= topY + 40) {
                    this.minecraft.keyboardHandler.setClipboard(selected.id().getNamespace());
                    sound(1.4F);
                    return true;
                }
            }
            if (mode == 1 && selectedItem != null) {
                String id = selectedItem.id();
                if (mx >= rightX1 + 46 && mx <= rightX1 + 46 + this.font.width(id)
                        && my >= topY + 20 && my <= topY + 29) {
                    this.minecraft.keyboardHandler.setClipboard(id);
                    sound(1.4F);
                    return true;
                }
            }
            for (Object[] z : refZones) {
                int y = (Integer) z[0];
                if (my >= y && my < y + TRADE_H) {
                    TradeRow t = (TradeRow) z[1];
                    Prof target = (Prof) z[2];
                    if (target != null) {
                        sound(1.2F);
                        mode = 0;
                        selected = target;
                        targetR = 0;
                        scrollR = 0;
                        rebuildRows();
                        if (t.variants != null) {
                            expanded.add(t);
                        }
                    } else if (t.variants != null) {
                        sound(0.9F);
                        if (!expanded.remove(t)) {
                            expanded.add(t);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void applyChip(int id) {
        if (id == 200) {
            onClose();
            return;
        }
        if (id == 201) {
            copySelected();
            return;
        }
        if (id == 202) {
            copyAll();
            return;
        }
        if (id >= 100) {
            int newMode = id - 100;
            if (newMode != mode) {
                mode = newMode;
                rebuildRows();
                targetR = 0;
                scrollR = 0;
            }
            return;
        }
        if (id >= 1 && id <= 5) {
            if (!levelFilter.remove(id)) {
                levelFilter.add(id);
            }
        } else if (id == 11) {
            dirFilter = dirFilter == 1 ? 0 : 1;
        } else if (id == 12) {
            dirFilter = dirFilter == 2 ? 0 : 2;
        }
        targetR = 0;
        scrollR = 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (!sugg.isEmpty() && queryFocused && mx >= leftX1 && mx <= leftX2
                && my >= suggViewTop && my <= suggViewTop + suggView) {
            targetS = Mth.clamp(targetS - delta * 40, 0, Math.max(0, suggContentH - suggView));
            return true;
        }
        if (mx <= leftX2) {
            int max = Math.max(0, listContentHeight() - leftView);
            targetL = Mth.clamp(targetL - delta * 40, 0, max);
        } else {
            int max = Math.max(0, rightContentNow() - rightView);
            targetR = Mth.clamp(targetR - delta * 40, 0, max);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragS) {
            targetS = scrollS = Scroll.thumbToScroll((int) my, suggViewTop, suggViewTop + suggView, suggContentH);
            return true;
        }
        if (dragL) {
            targetL = scrollL = Scroll.thumbToScroll((int) my, listY1 + 2, botY - 2, listContentHeight());
            return true;
        }
        if (dragR) {
            targetR = scrollR = Scroll.thumbToScroll((int) my, rightViewTop, botY - 1, rightContentNow());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragS = false;
        dragL = false;
        dragR = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (queryFocused) {
            TextOps.Res r = TextOps.type(query, caret, querySel, ch, 256);
            if (r.handled) {
                query = r.text;
                caret = r.caret;
                querySel = r.sel;
                if (r.changed) {
                    rebuildRows();
                }
                return true;
            }
        }
        return super.charTyped(ch, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!queryFocused && ModBinds.matchesNow(ModBinds.Bind.SEARCH, key)) {
            queryFocused = true;
            caret = query.length();
            querySel = -1;
            sound(1.0F);
            return true;
        }
        if (queryFocused) {
            if (!sugg.isEmpty()) {
                if (key == 264) {
                    suggSel = Math.min(sugg.size() - 1, suggSel + 1);
                    ensureSuggVisible();
                    return true;
                }
                if (key == 265) {
                    suggSel = Math.max(0, suggSel - 1);
                    ensureSuggVisible();
                    return true;
                }
                if (key == 257 || key == 335 || key == 258) {
                    acceptSuggestion();
                    return true;
                }
                if (key == 256) {
                    suggHidden = true;
                    sugg.clear();
                    return true;
                }
            }
            if (key == 256) {
                queryFocused = false;
                return true;
            }
            TextOps.Res r = TextOps.key(query, caret, querySel, key, mods, 256);
            if (r.handled) {
                query = r.text;
                caret = r.caret;
                querySel = r.sel;
                if (r.changed) {
                    rebuildRows();
                }
                return true;
            }
        }
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        previews.clear();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
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
}