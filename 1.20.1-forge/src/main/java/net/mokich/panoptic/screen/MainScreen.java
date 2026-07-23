package net.mokich.panoptic.screen;

import net.mokich.panoptic.data.seed.ServerSeed;
import net.mokich.panoptic.screen.inspector.InspectorScreen;
import net.mokich.panoptic.screen.seed.SeedMapScreen;
import net.mokich.panoptic.screen.trade.VillagerBrowserScreen;
import net.mokich.panoptic.screen.screengrab.ScreenInspectorScreen;

import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.HelpCard;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.api.ui.Scroll;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.config.Perms;
import net.mokich.panoptic.config.LogFeed;

import net.mokich.panoptic.inspect.InspectStore;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class MainScreen extends Screen {
    public record Tool(ItemStack icon, String nameKey, String descKey, ModBinds.Bind bind, Function<Screen, Screen> open) {
        public boolean unlocked() {
            if (nameKey.equals("panoptic.tool.inspector")) {
                return Perms.allowed(Perms.Feature.INSPECTOR);
            }
            if (nameKey.equals("panoptic.tool.map")) {
                return Perms.seedScreenAllowed();
            }
            if (nameKey.equals("panoptic.tool.villagers")) {
                return Perms.allowed(Perms.Feature.TRADE);
            }
            if (nameKey.equals("panoptic.tool.screens")) {
                return Perms.allowed(Perms.Feature.SCREENS);
            }
            return true;
        }
    }

    public static List<Tool> tools() {
        List<Tool> out = new ArrayList<>(List.of(
                new Tool(new ItemStack(Items.SPYGLASS), "panoptic.tool.inspector", "panoptic.main.desc.inspector",
                        ModBinds.Bind.INSPECTOR, p -> new InspectorScreen()),
                new Tool(new ItemStack(Items.FILLED_MAP), "panoptic.tool.map", "panoptic.main.desc.map",
                        ModBinds.Bind.SEEDMAP, SeedMapScreen::new),
                new Tool(new ItemStack(Items.EMERALD), "panoptic.tool.villagers", "panoptic.main.desc.villagers",
                        ModBinds.Bind.VILLAGERS, VillagerBrowserScreen::new),
                new Tool(new ItemStack(Items.PAINTING), "panoptic.tool.screens", "panoptic.main.desc.screens",
                        ModBinds.Bind.SCREEN_INSPECTOR, p -> new ScreenInspectorScreen()),
                new Tool(new ItemStack(Items.COMPARATOR), "panoptic.tool.settings", "panoptic.main.desc.settings",
                        null, SettingsScreen::new)
        ));
        if (Perms.serverSynced() && Perms.allowed(Perms.Feature.ADMIN)) {
            out.add(new Tool(new ItemStack(Items.WRITABLE_BOOK), "panoptic.tool.perms", "panoptic.main.desc.perms",
                    null, PermsAdminScreen::new));
        }
        return out;
    }

    private record Kpi(String label, String value) {}
    private static final int CONSOLE_H = 150;
    private final List<Tool> tools = tools();
    private final List<Kpi> kpis = new ArrayList<>();
    private final List<int[]> zones = new ArrayList<>();
    private final Map<Integer, String> copyZones = new HashMap<>();
    private long seed;
    private boolean hasSeed;
    private boolean helpHover;
    private String copied;
    private long copiedUntil;
    private double logScroll;
    private double logTarget;
    private boolean logStick = true;
    private int logLastCount = -1;
    private final Set<LogFeed.Line> logSel = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean logSelecting;
    private boolean logSelAdd;
    private boolean logBarDrag;
    private double pageScroll;
    private double pageTarget;
    private int pageMax;
    private long lastNano;
    private long appearStart;

    private int x1;
    private int x2;
    private int logY1;
    private int logY2;
    private String hoverTip;
    private LogFeed.Line logHoverLine;

    public MainScreen() {
        super(Component.translatable("panoptic.main.title"));
    }

    @Override
    protected void init() {
        LogFeed.attach();
        int w = Math.min(680, this.width - 16);
        x1 = (this.width - w) / 2;
        x2 = x1 + w;
        if (appearStart == 0) {
            appearStart = System.nanoTime();
            sound(1.2F);
        }
        kpis.clear();
        Minecraft mc = this.minecraft;
        try {
            assert mc != null;
            if (Perms.allowed(Perms.Feature.SEED_VIEW)) {
                if (mc.getSingleplayerServer() != null) {
                    seed = mc.getSingleplayerServer().overworld().getSeed();
                    hasSeed = true;
                } else {
                    Long remote = ServerSeed.get();
                    if (remote != null) {
                        seed = remote;
                        hasSeed = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        kpi("panoptic.dash.mods", () -> ModList.get().size());
        kpi("panoptic.dash.items", () -> ForgeRegistries.ITEMS.getKeys().size());
        kpi("panoptic.dash.blocks", () -> ForgeRegistries.BLOCKS.getKeys().size());
        kpi("panoptic.dash.entities", () -> ForgeRegistries.ENTITY_TYPES.getKeys().size());
        kpi("panoptic.dash.biomes", () -> mc.level.registryAccess().registryOrThrow(Registries.BIOME).size());
        kpi("panoptic.dash.structures", () -> mc.level.registryAccess().registryOrThrow(Registries.STRUCTURE).size());
        kpi("panoptic.dash.recipes", () -> mc.level.getRecipeManager().getRecipes().size());
        kpi("panoptic.dash.dims", () -> mc.player.connection.levels().size());
        kpi("panoptic.dash.advancements", () -> {
            assert mc != null;
            if (mc.getSingleplayerServer() != null) {
                return mc.getSingleplayerServer().getAdvancements().getAllAdvancements().size();
            }
            return mc.player.connection.getAdvancements().getAdvancements().getAllAdvancements().size();
        });
    }

    private void kpi(String baseKey, IntSupplier value) {
        int n;
        try {
            n = value.getAsInt();
        } catch (Throwable t) {
            return;
        }
        kpis.add(new Kpi(I18n.get(baseKey + "." + GuiStyle.plural(n, "one", "few", "many")), String.valueOf(n)));
    }

    private void sound(float pitch) {
        if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5F));
        }
    }

    private float appear() {
        if (appearStart == 0) {
            return 1.0F;
        }
        float t = Mth.clamp((System.nanoTime() - appearStart) / 1.6E8F, 0.0F, 1.0F);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        if (lastNano == 0) {
            lastNano = now;
        }
        double dt = Math.min((now - lastNano) / 1.0e9, 0.1);
        lastNano = now;
        logScroll = Scroll.ease(logScroll, logTarget, dt, 16.0);
        pageScroll = Scroll.ease(pageScroll, pageTarget, dt, 16.0);

        renderBackground(g);
        zones.clear();
        copyZones.clear();
        hoverTip = null;
        logHoverLine = null;

        float ap = appear();
        g.pose().pushPose();
        float sc = 0.96F + 0.04F * ap;
        float ccx = (x1 + x2) / 2.0F;
        float ccy = this.height / 2.0F;
        g.pose().translate(ccx, ccy, 0);
        g.pose().scale(sc, sc, 1.0F);
        g.pose().translate(-ccx, -ccy, 0);

        int y = 10 - (int) Math.round(pageScroll);
        y = drawKpis(g, y);
        y += 6;
        y = drawPanels(g, y, mouseX, mouseY);
        y += 6;
        y = drawTools(g, y, mouseX, mouseY);
        y += 6;
        y = drawLogConsole(g, y, mouseX, mouseY);
        pageMax = Math.max(0, y + (int) Math.round(pageScroll) + 8 - this.height);

        if (pageMax > 0) {
            int trackH = this.height - 16;
            int thumbH = Math.max(20, (int) (trackH * (float) (this.height - 20) / (this.height + pageMax)));
            int thumbY = 8 + (int) ((trackH - thumbH) * (pageScroll / pageMax));
            g.fill(x2 + 3, 8, x2 + 6, 8 + trackH, 0x40000000);
            g.fill(x2 + 3, thumbY, x2 + 6, thumbY + thumbH, GuiStyle.BORDER_B);
        }

        if (copied != null && System.currentTimeMillis() < copiedUntil) {
            g.fill(x1, 2, x2, 14, GuiStyle.T(0xC0141109));
            g.drawCenteredString(this.font, copied, (x1 + x2) / 2, 4, GuiStyle.ACCENT);
        }
        g.pose().popPose();
        helpHover = HelpCard.icon(g, this.font, x2 - HelpCard.SIZE - 1, 2, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partial);
        if (hoverTip != null) {
            g.renderTooltip(this.font, Component.literal(hoverTip), mouseX, mouseY);
        }
        drawHelp(g);
    }

    private void drawHelp(GuiGraphics g) {
        List<Component> bullets = new ArrayList<>();
        bullets.add(Component.translatable("panoptic.main.help_b1"));
        bullets.add(Component.translatable("panoptic.main.help_b2"));
        bullets.add(Component.translatable("panoptic.main.help_b3"));
        bullets.add(Component.translatable("panoptic.main.help_b4"));
        List<HelpCard.KeyHint> keys = List.of(
                ModBinds.hint(ModBinds.Bind.MAIN, "panoptic.main.help_wheel"));
        HelpCard.render(g, this.font, this.width, this.height, helpHover,
                Component.translatable("panoptic.main.help_title"),
                Component.translatable("panoptic.main.help_sum"), bullets, keys);
    }

    private int drawKpis(GuiGraphics g, int y) {
        int n = kpis.size();
        if (n == 0) {
            return y;
        }
        int cols = (x2 - x1) >= 640 ? Math.min(8, n) : Math.min(4, n);
        int rows = (n + cols - 1) / cols;
        int gap = 5;
        int tw = (x2 - x1 - gap * (cols - 1)) / cols;
        int th = 30;
        for (int i = 0; i < n; i++) {
            int cx = x1 + (i % cols) * (tw + gap);
            int cy = y + (i / cols) * (th + gap);
            GuiStyle.plate(g, cx, cy, cx + tw, cy + th, GuiStyle.T(0xFF3A3020), GuiStyle.T(0xFF241E14), GuiStyle.BORDER);
            g.fill(cx + 2, cy + 4, cx + 4, cy + th - 4, GuiStyle.ACCENT);
            Kpi kp = kpis.get(i);
            g.drawString(this.font, "§l" + kp.value(), cx + 8, cy + 5, GuiStyle.TEXT);
            g.drawString(this.font, trim(kp.label(), tw - 12), cx + 8, cy + 17, GuiStyle.DIM);
        }
        return y + rows * (th + gap) - gap;
    }

    private int drawPanels(GuiGraphics g, int y, int mouseX, int mouseY) {
        int gap = 6;
        boolean wide = (x2 - x1) >= 520;
        int pw = wide ? (x2 - x1 - gap) / 2 : x2 - x1;
        int ph = 70;
        drawWorldPanel(g, x1, y, x1 + pw, y + ph, mouseX, mouseY);
        if (wide) {
            drawPerfPanel(g, x1 + pw + gap, y, x2, y + ph);
            return y + ph;
        }
        drawPerfPanel(g, x1, y + ph + gap, x2, y + ph * 2 + gap);
        return y + ph * 2 + gap;
    }

    private int panelHeader(GuiGraphics g, int px1, int py1, int px2, int py2, String titleKey, String right) {
        GuiStyle.panel(g, px1, py1, px2, py2);
        GuiStyle.panelHeader(g, this.font, px1, py1, px2, I18n.get(titleKey), right);
        return py1 + 19;
    }

    private void kv(GuiGraphics g, int px1, int px2, int y, String label, String value,
                    String copyValue, int mouseX, int mouseY) {
        g.drawString(this.font, label, px1 + 6, y, GuiStyle.DIM);
        int vw = this.font.width(value);
        int vx = px2 - 6 - vw - (copyValue != null ? 12 : 0);
        boolean hov = copyValue != null && mouseX >= vx - 2 && mouseX <= px2 - 6
                && mouseY >= y - 2 && mouseY <= y + 9;
        g.drawString(this.font, value, vx, y, hov ? GuiStyle.ACCENT : GuiStyle.TEXT);
        if (copyValue != null) {
            Icons.iconCopy(g, px2 - 15, y - 1, hov ? GuiStyle.ACCENT : GuiStyle.DIM);
            int id = 50 + copyZones.size();
            zones.add(new int[]{vx - 2, y - 2, px2 - 4, y + 9, id});
            copyZones.put(id, copyValue);
            if (hov) {
                hoverTip = I18n.get("panoptic.dash.click_copy");
            }
        }
    }

    private void drawWorldPanel(GuiGraphics g, int px1, int py1, int px2, int py2, int mouseX, int mouseY) {
        Minecraft mc = this.minecraft;
        String dimShort = mc == null || mc.level == null ? "—" : mc.level.dimension().location().getPath();
        int y = panelHeader(g, px1, py1, px2, py2, "panoptic.dash.world", dimShort);
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        kv(g, px1, px2, y, I18n.get("panoptic.dash.seed"), hasSeed ? String.valueOf(seed) : I18n.get("panoptic.dash.seed_remote"),
                hasSeed ? String.valueOf(seed) : null, mouseX, mouseY);
        y += 12;
        String dim = mc.level.dimension().location().toString();
        kv(g, px1, px2, y, I18n.get("panoptic.dash.dim"), trim(dim, (px2 - px1) / 2), dim, mouseX, mouseY);
        y += 12;
        BlockPos pos = mc.player.blockPosition();
        String posS = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        kv(g, px1, px2, y, I18n.get("panoptic.dash.pos"), posS, posS, mouseX, mouseY);
        y += 12;
        kv(g, px1, px2, y, I18n.get("panoptic.dash.chunk"),
                (pos.getX() >> 4) + " " + (pos.getZ() >> 4) + " §8· §7" + biomeName(mc, pos), null, mouseX, mouseY);
    }

    private String biomeName(Minecraft mc, BlockPos pos) {
        try {
            var key = mc.level.getBiome(pos).unwrapKey().orElse(null);
            if (key == null) {
                return "?";
            }
            String lk = "biome." + key.location().getNamespace() + "." + key.location().getPath();
            return I18n.exists(lk) ? I18n.get(lk) : key.location().getPath();
        } catch (Throwable t) {
            return "?";
        }
    }

    private void drawPerfPanel(GuiGraphics g, int px1, int py1, int px2, int py2) {
        Minecraft mc = this.minecraft;
        int y = panelHeader(g, px1, py1, px2, py2, "panoptic.dash.perf", mc == null ? null : mc.getFps() + " FPS");
        if (mc == null || mc.level == null) {
            return;
        }
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long maxMb = rt.maxMemory() / 1048576L;
        float frac = Mth.clamp(usedMb / (float) Math.max(1L, maxMb), 0.0F, 1.0F);
        g.drawString(this.font, I18n.get("panoptic.dash.ram"), px1 + 6, y, GuiStyle.DIM);
        String ram = usedMb + " / " + maxMb + " MB";
        g.drawString(this.font, ram, px2 - 6 - this.font.width(ram), y, GuiStyle.TEXT);
        y += 11;
        int bw = px2 - px1 - 12;
        g.fill(px1 + 6, y, px1 + 6 + bw, y + 4, 0xFF17130B);
        g.fill(px1 + 6, y, px1 + 6 + (int) (bw * frac), y + 4, frac > 0.85F ? 0xFFE06666 : GuiStyle.ACCENT);
        y += 9;
        kv(g, px1, px2, y, I18n.get("panoptic.dash.chunks_loaded"),
                String.valueOf(mc.level.getChunkSource().getLoadedChunksCount()), null, 0, 0);
        y += 12;
        kv(g, px1, px2, y, I18n.get("panoptic.dash.captured"),
                String.valueOf(InspectStore.entries().size()), null, 0, 0);
    }

    private int drawTools(GuiGraphics g, int y, int mouseX, int mouseY) {
        int gap = 6;
        int cols = (x2 - x1) >= 520 ? 4 : 2;
        int cw = (x2 - x1 - gap * (cols - 1)) / cols;
        int ch = 30;
        int rows = (tools.size() + cols - 1) / cols;
        for (int i = 0; i < tools.size(); i++) {
            Tool t = tools.get(i);
            int cx = x1 + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            boolean unlocked = t.unlocked();
            boolean hov = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            GuiStyle.button(g, this.font, cx, cy, cx + cw, cy + ch, "", hov && unlocked, unlocked);
            GuiStyle.slot(g, cx + 5, cy + 6);
            g.renderFakeItem(t.icon(), cx + 6, cy + 7);
            if (!unlocked) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(cx + 5, cy + 6, cx + 23, cy + 24, 0x90100C07);
                Icons.iconCross(g, cx + 11, cy + 12, 0xFFFF6B6B);
                g.pose().popPose();
            }
            g.drawString(this.font, I18n.get(t.nameKey()), cx + 28, cy + 6,
                    !unlocked ? GuiStyle.DIM : hov ? GuiStyle.ACCENT : GuiStyle.TEXT);
            String bind = t.bind() == null ? "" : ModBinds.label(t.bind());
            g.drawString(this.font, trim(bind, cw - 34), cx + 28, cy + 17, GuiStyle.DIM);
            zones.add(new int[]{cx, cy, cx + cw, cy + ch, i});
            if (hov) {
                hoverTip = unlocked ? I18n.get(t.descKey()) : I18n.get("panoptic.perm.locked");
            }
        }
        return y + rows * (ch + gap) - gap;
    }

    private int drawLogConsole(GuiGraphics g, int y, int mouseX, int mouseY) {
        logY1 = y;
        logY2 = y + CONSOLE_H;
        int e = LogFeed.errors();
        int w = LogFeed.warns();
        List<LogFeed.Line> lines = LogFeed.snapshot();
        String right = lines.size() + " §8| §c" + e + " §8/ §6" + w;
        GuiStyle.panel(g, x1, logY1, x2, logY2);
        GuiStyle.panelHeader(g, this.font, x1, logY1, x2, I18n.get("panoptic.dash.console"), right);

        int viewY1 = logY1 + 16;
        int viewH = logY2 - 2 - viewY1;
        int maxScroll = Math.max(0, lines.size() * 10 + 4 - viewH);
        if (lines.size() != logLastCount) {
            logLastCount = lines.size();
            if (logStick) {
                logTarget = maxScroll;
                logScroll = maxScroll;
            }
        }
        logTarget = Mth.clamp(logTarget, 0, maxScroll);

        g.enableScissor(x1 + 2, viewY1, x2 - 2, logY2 - 2);
        if (lines.isEmpty()) {
            g.drawString(this.font, I18n.get("panoptic.dash.console_empty"), x1 + 6, viewY1 + 4, GuiStyle.DIM);
        }
        int scrollPx = (int) Math.round(logScroll);
        int first = Math.max(0, (scrollPx - 12) / 10);
        int last = Math.min(lines.size(), first + viewH / 10 + 3);
        int ly = viewY1 + 2 - scrollPx + first * 10;
        for (int i = first; i < last; i++) {
            LogFeed.Line line = lines.get(i);
            if (ly + 10 >= viewY1 && ly <= logY2) {
                boolean hov = mouseX >= x1 + 2 && mouseX < x2 - 2 && mouseY >= ly && mouseY < ly + 10
                        && mouseY >= viewY1 && mouseY <= logY2 - 2;
                if (logSel.contains(line)) {
                    g.fill(x1 + 2, ly - 1, x2 - 2, ly + 9, GuiStyle.T(0x33E8C06C));
                    g.fill(x1 + 2, ly - 1, x1 + 4, ly + 9, GuiStyle.ACCENT);
                }
                if (hov) {
                    GuiStyle.row(g, x1 + 2, ly - 1, x2 - 2, ly + 9, true, false);
                    hoverTip = I18n.get("panoptic.dash.log_copy");
                    logHoverLine = line;
                }
                g.fill(x1 + 3, ly, x1 + 5, ly + 8, LogFeed.barColorOf(line.level()));
                String tag = "[" + line.logger() + "]";
                g.drawString(this.font, trim(tag, 90), x1 + 9, ly, GuiStyle.DIM);
                int tx = x1 + 9 + Math.min(90, this.font.width(tag)) + 5;
                int textRight = maxScroll > 0 ? x2 - 10 : x2 - 4;
                g.drawString(this.font, trim(line.text(), textRight - tx - 4), tx, ly, LogFeed.colorOf(line.level()));
            }
            ly += 10;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackY1 = viewY1 + 1;
            int trackH = viewH - 2;
            int thumbH = Math.max(14, (int) (trackH * (float) viewH / (viewH + maxScroll)));
            int thumbY = trackY1 + (int) ((trackH - thumbH) * (logScroll / maxScroll));
            g.fill(x2 - 6, trackY1, x2 - 3, trackY1 + trackH, 0x50000000);
            g.fill(x2 - 6, thumbY, x2 - 3, thumbY + thumbH, GuiStyle.BORDER_B);
        }

        if (!logSel.isEmpty()) {
            String copyL = I18n.get("panoptic.dash.copy_sel", logSel.size());
            String clearL = I18n.get("panoptic.dash.clear_sel");
            int cw = this.font.width(copyL) + 14;
            int xw = this.font.width(clearL) + 14;
            int bx = x1 + 8;
            int by = logY2 - 22;
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(bx - 3, by - 3, bx + cw + 4 + xw + 3, by + 17, GuiStyle.T(0xE0141109));
            boolean cHov = mouseX >= bx && mouseX < bx + cw && mouseY >= by && mouseY < by + 14;
            GuiStyle.button(g, this.font, bx, by, bx + cw, by + 14, copyL, cHov, true, true);
            zones.add(new int[]{bx, by, bx + cw, by + 14, 42});
            int bx2 = bx + cw + 4;
            boolean xHov = mouseX >= bx2 && mouseX < bx2 + xw && mouseY >= by && mouseY < by + 14;
            GuiStyle.button(g, this.font, bx2, by, bx2 + xw, by + 14, clearL, xHov, true);
            zones.add(new int[]{bx2, by, bx2 + xw, by + 14, 43});
            g.pose().popPose();
        }

        if (!logStick && maxScroll > 0) {
            int bx2 = x2 - 10;
            int bx1 = bx2 - 18;
            int by2 = logY2 - 6;
            int by1 = by2 - 16;
            boolean hov = mouseX >= bx1 && mouseX < bx2 && mouseY >= by1 && mouseY < by2;
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(bx1 - 2, by1 - 2, bx2 + 2, by2 + 2, GuiStyle.T(0xE0141109));
            GuiStyle.button(g, this.font, bx1, by1, bx2, by2, "", hov, true);
            Icons.iconTriDown(g, (bx1 + bx2) / 2 - 3, (by1 + by2) / 2 - 3, hov ? GuiStyle.ACCENT : GuiStyle.TEXT);
            g.pose().popPose();
            zones.add(new int[]{bx1, by1, bx2, by2, 41});
            if (hov) {
                hoverTip = I18n.get("panoptic.dash.log_bottom");
            }
        }
        return logY2;
    }

    private int logContentHeight() {
        return LogFeed.snapshot().size() * 10 + 4;
    }

    private String trim(String s, int w) {
        if (w <= 6) {
            return "";
        }
        if (this.font.width(s) <= w) {
            return s;
        }
        return this.font.plainSubstrByWidth(s, w - 6) + "…";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my >= logY1 && my <= logY2 && mx >= x1 && mx <= x2) {
            int max = Math.max(0, logContentHeight() - (logY2 - 2 - (logY1 + 16)));
            logTarget = Mth.clamp(logTarget - delta * 30, 0, max);
            logStick = logTarget >= max - 0.5;
            return true;
        }
        if (pageMax > 0) {
            pageTarget = Mth.clamp(pageTarget - delta * 34, 0, pageMax);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int[] z : zones) {
                if (mx >= z[0] && mx < z[2] && my >= z[1] && my < z[3]) {
                    int id = z[4];
                    if (id < tools.size()) {
                        if (!tools.get(id).unlocked()) {
                            Perms.deny();
                            return true;
                        }
                        sound(1.0F);
                        this.minecraft.setScreen(tools.get(id).open().apply(this));
                    } else if (id == 41) {
                        logStick = true;
                        logTarget = Math.max(0, logContentHeight() - (logY2 - 2 - (logY1 + 16)));
                        sound(1.0F);
                    } else if (id == 42) {
                        copyLogSelection();
                    } else if (id == 43) {
                        logSel.clear();
                        sound(0.9F);
                    } else {
                        String value = copyZones.get(id);
                        if (value != null) {
                            this.minecraft.keyboardHandler.setClipboard(value);
                            copied = I18n.get("panoptic.dash.copied");
                            copiedUntil = System.currentTimeMillis() + 1500;
                            sound(1.4F);
                        }
                    }
                    return true;
                }
            }
            int viewY1 = logY1 + 16;
            int viewH = logY2 - 2 - viewY1;
            int maxScroll = Math.max(0, logContentHeight() - viewH);
            if (maxScroll > 0 && mx >= x2 - 7 && mx < x2 - 2 && my >= viewY1 && my < logY2 - 2) {
                jumpLogBar(my);
                logBarDrag = true;
                return true;
            }
            if (logHoverLine != null) {
                logSelAdd = !logSel.contains(logHoverLine);
                if (logSelAdd) {
                    logSel.add(logHoverLine);
                } else {
                    logSel.remove(logHoverLine);
                }
                logSelecting = true;
                sound(1.1F);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (logBarDrag) {
            jumpLogBar(my);
            return true;
        }
        if (logSelecting) {
            LogFeed.Line line = lineAt(mx, my);
            if (line != null) {
                if (logSelAdd) {
                    logSel.add(line);
                } else {
                    logSel.remove(line);
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (logBarDrag || logSelecting) {
            logBarDrag = false;
            logSelecting = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private LogFeed.Line lineAt(double mx, double my) {
        int viewY1 = logY1 + 16;
        if (mx < x1 + 2 || mx >= x2 - 2 || my < viewY1 || my > logY2 - 2) {
            return null;
        }
        List<LogFeed.Line> lines = LogFeed.snapshot();
        int idx = (int) ((my - viewY1 - 2 + Math.round(logScroll)) / 10);
        return idx >= 0 && idx < lines.size() ? lines.get(idx) : null;
    }

    private void jumpLogBar(double my) {
        int viewY1 = logY1 + 16;
        int viewH = logY2 - 2 - viewY1;
        int maxScroll = Math.max(0, logContentHeight() - viewH);
        if (maxScroll <= 0) {
            return;
        }
        int thumbH = Math.max(14, (int) (viewH * (float) viewH / (viewH + maxScroll)));
        double frac = (my - viewY1 - thumbH / 2.0) / Math.max(1, viewH - thumbH);
        logTarget = Mth.clamp(frac * maxScroll, 0, maxScroll);
        logScroll = logTarget;
        logStick = logTarget >= maxScroll - 0.5;
    }

    private void copyLogSelection() {
        if (logSel.isEmpty() || this.minecraft == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (LogFeed.Line line : LogFeed.snapshot()) {
            if (logSel.contains(line)) {
                sb.append('[').append(line.logger()).append("] ").append(line.text()).append('\n');
            }
        }
        this.minecraft.keyboardHandler.setClipboard(sb.toString());
        copied = I18n.get("panoptic.dash.copied");
        copiedUntil = System.currentTimeMillis() + 1500;
        logSel.clear();
        sound(1.4F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}