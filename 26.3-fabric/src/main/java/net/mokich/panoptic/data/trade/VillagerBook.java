package net.mokich.panoptic.data.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.EntitySpawnReason;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Block;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.mokich.panoptic.network.TradeBookPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.nio.file.Path;

public final class VillagerBook {
    public static volatile int revision;

    public static final class TradeRow {
        public final int level;
        public final ItemStack costA;
        public final ItemStack costB;
        public final ItemStack result;
        public final int maxUses;
        public final int xp;
        public final boolean random;
        public final String note;
        public final String sourceMod;
        public final List<TradeRow> variants;
        public transient VillagerTrade pendingTrade;
        public transient boolean serverPending;
        public transient List<TradeRow> rawVariants;

        TradeRow(int level, ItemStack costA, ItemStack costB, ItemStack result,
                 int maxUses, int xp, boolean random, String note, String sourceMod, List<TradeRow> variants) {
            this.level = level;
            this.costA = costA;
            this.costB = costB;
            this.result = result;
            this.maxUses = maxUses;
            this.xp = xp;
            this.random = random;
            this.note = note;
            this.sourceMod = sourceMod;
            this.variants = variants;
        }

        public boolean sellsToPlayer() {
            return !result.is(Items.EMERALD);
        }

        public List<TradeRow> children() {
            if (variants != null && !variants.isEmpty()) {
                return variants;
            }
            if (rawVariants != null && rawVariants.size() > 1) {
                return rawVariants;
            }
            return null;
        }
    }

    public record Prof(Identifier id, String name, String modName, boolean wandering,
                       VillagerProfession profession, List<ItemStack> workstations,
                       List<TradeRow> trades, String searchBlob) {
    }

    private static final Set<String> RANDOM_LISTINGS = Set.of(
            "EnchantBookForEmeralds", "EnchantedItemForEmeralds", "TippedArrowForItemsAndEmeralds",
            "DyedArmorForEmeralds", "SuspiciousStewForEmerald", "TreasureMapForEmeralds");
    private static final Map<Class<?>, String> SOURCE_CACHE = new HashMap<>();

    private VillagerBook() {
    }

    public static volatile boolean lastBuildComplete;

    public static String sessionSignature() {
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftServer sp = mc.getSingleplayerServer();
            if (sp != null) {
                return "sp" + System.identityHashCode(sp);
            }
            return mc.getConnection() == null ? "none" : "mp" + System.identityHashCode(mc.getConnection());
        } catch (Throwable t) {
            return "none";
        }
    }

    public static List<Prof> build() {
        synchronized (REMOTE_PENDING) {
            REMOTE_PENDING.clear();
            REMOTE_FILLED.clear();
        }
        List<Prof> out = new ArrayList<>();
        RegistryAccess regs = registries();
        Registry<TradeSet> tradeSets = regs == null ? null : regs.lookup(Registries.TRADE_SET).orElse(null);
        lastBuildComplete = tradeSets != null;
        for (VillagerProfession prof : BuiltInRegistries.VILLAGER_PROFESSION) {
            Identifier id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
            if (id == null) {
                continue;
            }
            List<TradeRow> trades = collectTrades(prof.tradeSetsByLevel(), tradeSets);
            out.add(make(id, false, prof, workstations(prof), trades));
        }
        out.sort((a, b) -> {
            int m = a.modName().compareToIgnoreCase(b.modName());
            return m != 0 ? m : a.name().compareToIgnoreCase(b.name());
        });
        List<TradeRow> wander = new ArrayList<>();
        Identifier wid = Identifier.withDefaultNamespace("wandering_trader");
        if (tradeSets != null) {
            appendTradeSet(wander, tradeSets, TradeSets.WANDERING_TRADER_BUYING, 1);
            appendTradeSet(wander, tradeSets, TradeSets.WANDERING_TRADER_COMMON, 1);
            appendTradeSet(wander, tradeSets, TradeSets.WANDERING_TRADER_UNCOMMON, 2);
        }
        out.add(0, make(wid, true, null, List.of(), wander));
        return out;
    }

    private static RegistryAccess registries() {
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftServer sp = mc.getSingleplayerServer();
            if (sp != null) {
                return sp.registryAccess();
            }
            if (mc.getConnection() != null) {
                return mc.getConnection().registryAccess();
            }
            return mc.level == null ? null : mc.level.registryAccess();
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<TradeRow> collectTrades(Int2ObjectMap<ResourceKey<TradeSet>> byLevel, Registry<TradeSet> tradeSets) {
        List<TradeRow> trades = new ArrayList<>();
        if (byLevel == null || tradeSets == null) {
            return trades;
        }
        int[] levels = byLevel.keySet().toIntArray();
        Arrays.sort(levels);
        for (int lvl : levels) {
            appendTradeSet(trades, tradeSets, byLevel.get(lvl), lvl);
        }
        return trades;
    }

    private static void appendTradeSet(List<TradeRow> out, Registry<TradeSet> tradeSets, ResourceKey<TradeSet> key, int level) {
        if (key == null) {
            return;
        }
        TradeSet ts = tradeSets.getValue(key);
        if (ts == null) {
            return;
        }
        String source = modDisplay(key.identifier().getNamespace());
        ts.trades().stream().forEach(h -> {
            TradeRow row = tradeRow(h.value(), level, source);
            if (row != null) {
                out.add(row);
            }
        });
    }

    private static TradeRow tradeRow(VillagerTrade trade, int level, String source) {
        try {
            ItemStack result = ItemStack.EMPTY;
            ItemStack costA = ItemStack.EMPTY;
            ItemStack costB = ItemStack.EMPTY;
            int maxUses = 12;
            int xp = 0;
            for (Field f : VillagerTrade.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Object v;
                try {
                    if (!f.trySetAccessible()) {
                        continue;
                    }
                    v = f.get(trade);
                } catch (Throwable ignored) {
                    continue;
                }
                if (v instanceof ItemStackTemplate tpl && result.isEmpty()) {
                    result = tpl.create();
                } else if (v instanceof TradeCost tc && costA.isEmpty()) {
                    costA = costStack(tc);
                } else if (v instanceof Optional<?> opt && opt.isPresent()
                        && opt.get() instanceof TradeCost tc2 && costB.isEmpty()) {
                    costB = costStack(tc2);
                } else if (v instanceof Holder<?> h && h.value() instanceof ContextIntProvider cip) {
                    int n = evalInt(cip);
                    if (f.getName().equals("maxUses")) {
                        maxUses = n <= 0 ? 12 : n;
                    } else if (f.getName().equals("xp")) {
                        xp = n;
                    }
                }
            }
            if (result.isEmpty()) {
                return null;
            }
            TradeRow row = new TradeRow(level, costA, costB, result, maxUses, xp, false, null, source, null);
            row.pendingTrade = trade;
            row.serverPending = true;
            return row;
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack costStack(TradeCost tc) {
        try {
            Item item = tc.item().value();
            int n = evalInt(tc.count().value());
            return new ItemStack(item, Math.max(1, n));
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static int evalInt(ContextIntProvider np) {
        try {
            return Math.round(np.getInt(null));
        } catch (Throwable t) {
            return 1;
        }
    }

    private static Prof make(Identifier id, boolean wandering, VillagerProfession prof,
                             List<ItemStack> stations, List<TradeRow> trades) {
        String name = profName(id, wandering);
        String mod = modDisplay(id.getNamespace());
        StringBuilder blob = new StringBuilder();
        blob.append(name.toLowerCase(Locale.ROOT)).append(' ')
                .append(id).append(' ')
                .append(mod.toLowerCase(Locale.ROOT)).append(' ');
        for (TradeRow t : trades) {
            appendDeep(blob, t);
        }
        return new Prof(id, name, mod, wandering, prof, stations, trades, blob.toString());
    }

    private static void appendDeep(StringBuilder blob, TradeRow t) {
        appendRow(blob, t);
        if (t.variants != null) {
            for (TradeRow v : t.variants) {
                appendDeep(blob, v);
            }
        }
        if (t.rawVariants != null) {
            for (TradeRow v : t.rawVariants) {
                appendRow(blob, v);
            }
        }
    }

    private static void appendRow(StringBuilder blob, TradeRow t) {
        appendStack(blob, t.costA);
        appendStack(blob, t.costB);
        appendStack(blob, t.result);
        if (t.note != null) {
            blob.append(t.note.toLowerCase(Locale.ROOT)).append(' ');
        }
    }

    private static void appendStack(StringBuilder blob, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key != null) {
            blob.append(key).append(' ');
        }
        blob.append(stack.getHoverName().getString().toLowerCase(Locale.ROOT)).append(' ');
        ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Holder<Enchantment> e : ench.keySet()) {
            blob.append(e.value().description().getString().toLowerCase(Locale.ROOT)).append(' ');
            blob.append(e.unwrapKey().map(k -> k.identifier().toString()).orElse("")).append(' ');
            blob.append(lvlText(ench.getLevel(e)).toLowerCase(Locale.ROOT)).append(' ');
        }
    }

    private static final Map<String, Prof> REMOTE_PENDING = new HashMap<>();
    private static final Map<String, List<TradeBookPackets.Entry>> REMOTE_CACHE = new HashMap<>();
    private static final Set<Prof> REMOTE_FILLED =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static String remoteSession = "";

    public static void resolveAll(List<Prof> profs) {
        if (profs == null || Minecraft.getInstance().getSingleplayerServer() == null) {
            return;
        }
        for (Prof p : profs) {
            resolvePending(p);
        }
    }

    public static void applyCachedRemote(List<Prof> profs) {
        if (profs == null) {
            return;
        }
        for (Prof p : profs) {
            List<TradeBookPackets.Entry> cached;
            synchronized (REMOTE_PENDING) {
                cached = REMOTE_CACHE.get(remoteKey(p.id().toString(), p.wandering()));
            }
            if (cached != null) {
                fillFromEntries(p, cached);
            }
        }
    }

    public static void applyRemote(String profId, boolean wandering, List<TradeBookPackets.Entry> entries) {
        Prof prof;
        String key = remoteKey(profId, wandering);
        synchronized (REMOTE_PENDING) {
            prof = REMOTE_PENDING.remove(key);
            if (entries != null) {
                REMOTE_CACHE.put(key, entries);
            }
        }
        if (prof == null || entries == null) {
            return;
        }
        fillFromEntries(prof, entries);
    }

    private static void fillFromEntries(Prof prof, List<TradeBookPackets.Entry> entries) {
        synchronized (REMOTE_PENDING) {
            if (!REMOTE_FILLED.add(prof)) {
                return;
            }
        }
        List<TradeRow> rows = new ArrayList<>();
        for (TradeBookPackets.Entry e : entries) {
            Map<String, TradeRow> distinct = new LinkedHashMap<>();
            for (TradeBookPackets.Offer o : e.offers()) {
                if (o.result().isEmpty()) {
                    continue;
                }
                TradeRow row = new TradeRow(e.level(), o.costA().copy(), o.costB().copy(), o.result().copy(),
                        o.maxUses(), o.xp(), false, null, e.sourceMod(), null);
                distinct.putIfAbsent(rowKey(row), row);
            }
            TradeRow built = buildRow(distinct, e.level(), e.sourceMod());
            if (built != null) {
                rows.add(built);
            }
        }
        List<TradeRow> target = prof.trades();
        target.clear();
        target.addAll(rows);
        revision++;
    }

    private static String rowKey(TradeRow t) {
        return t.result.getItem() + "|" + t.result.getCount() + "|" + t.result.getComponentsPatch()
                + "|" + t.costA.getItem() + "|" + t.costA.getCount()
                + "|" + t.costB.getItem() + "|" + t.costB.getCount();
    }

    private static String remoteKey(String profId, boolean wandering) {
        return (wandering ? "w|" : "p|") + profId;
    }

    private static boolean requestRemote(Prof prof) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return false;
        }
        String key = remoteKey(prof.id().toString(), prof.wandering());
        String sig = sessionSignature();
        List<TradeBookPackets.Entry> cached;
        synchronized (REMOTE_PENDING) {
            if (!sig.equals(remoteSession)) {
                remoteSession = sig;
                REMOTE_PENDING.clear();
                REMOTE_CACHE.clear();
                REMOTE_FILLED.clear();
            }
            if (REMOTE_FILLED.contains(prof)) {
                return true;
            }
            cached = REMOTE_CACHE.get(key);
            if (cached == null) {
                if (REMOTE_PENDING.containsKey(key)) {
                    return true;
                }
                REMOTE_PENDING.put(key, prof);
            }
        }
        if (cached != null) {
            fillFromEntries(prof, cached);
            return true;
        }
        try {
            ClientPlayNetworking.send(
                    new TradeBookPackets.Request(prof.id().toString(), prof.wandering()));
            return true;
        } catch (Throwable t) {
            synchronized (REMOTE_PENDING) {
                REMOTE_PENDING.remove(key);
            }
            return false;
        }
    }

    public static void resolvePending(Prof prof) {
        if (prof == null) {
            return;
        }
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            requestRemote(prof);
            return;
        }
        boolean any = false;
        for (TradeRow r : prof.trades()) {
            if (r.serverPending && r.pendingTrade != null) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        server.submit(() -> serverResolve(server, prof));
    }

    private static void serverResolve(MinecraftServer server, Prof prof) {
        try {
            ServerLevel world = server.overworld();
            if (world == null) {
                return;
            }
            AbstractVillager ent = prof.wandering()
                    ? wanderingTraderType().create(world, EntitySpawnReason.COMMAND)
                    : villagerType().create(world, EntitySpawnReason.COMMAND);
            if (ent == null) {
                return;
            }
            BlockPos at = world.getRespawnData().pos();
            ent.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            List<TradeRow> trades = prof.trades();
            List<Integer> pending = new ArrayList<>();
            for (int i = 0; i < trades.size(); i++) {
                TradeRow row = trades.get(i);
                if (row.serverPending && row.pendingTrade != null) {
                    row.serverPending = false;
                    pending.add(i);
                }
            }
            if (pending.isEmpty()) {
                ent.discard();
                return;
            }
            List<Holder<VillagerType>> types = variantHolders(ent);
            Map<Integer, Map<String, TradeRow>> collected = new LinkedHashMap<>();
            long deadline = System.nanoTime() + 5_000_000_000L;
            int passes = Math.max(1, types.size());
            for (int vi = 0; vi < passes; vi++) {
                applyVariant(ent, types, vi);
                LootParams params = new LootParams.Builder(world)
                        .withParameter(LootContextParams.ORIGIN, ent.position())
                        .withParameter(LootContextParams.THIS_ENTITY, ent)
                        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                        .create(LootContextParamSets.VILLAGER_TRADE);
                for (int idx : pending) {
                    TradeRow row = trades.get(idx);
                    if (locatesStructure(row.pendingTrade)) {
                        continue;
                    }
                    if (vi > 0 && !isVariantGated(row.pendingTrade)) {
                        continue;
                    }
                    collectOffers(row.pendingTrade, params, row.level, row.sourceMod,
                            collected.computeIfAbsent(idx, k -> new LinkedHashMap<>()), deadline);
                }
            }
            for (int idx : pending) {
                TradeRow row = trades.get(idx);
                TradeRow updated = locatesStructure(row.pendingTrade)
                        ? structureMapRow(row)
                        : buildRow(collected.get(idx), row.level, row.sourceMod);
                if (updated == null) {
                    continue;
                }
                int slot = idx;
                Minecraft.getInstance().execute(() -> {
                    List<TradeRow> list = prof.trades();
                    if (slot >= 0 && slot < list.size()) {
                        list.set(slot, updated);
                        revision++;
                    }
                });
            }
            ent.discard();
        } catch (Throwable ignored) {
        }
    }

    private static List<Holder<VillagerType>> variantHolders(AbstractVillager ent) {
        if (!(ent instanceof Villager)) {
            return List.of();
        }
        List<Holder<VillagerType>> out = new ArrayList<>();
        try {
            for (Holder<VillagerType> h : BuiltInRegistries.VILLAGER_TYPE.listElements().toList()) {
                out.add(h);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void applyVariant(AbstractVillager ent, List<Holder<VillagerType>> types, int index) {
        if (!(ent instanceof Villager v) || index >= types.size()) {
            return;
        }
        try {
            v.setVillagerData(v.getVillagerData().withType(types.get(index)));
        } catch (Throwable ignored) {
        }
    }

    private static void collectOffers(VillagerTrade trade, LootParams params, int level, String source,
                                      Map<String, TradeRow> into, long deadline) {
        int sinceNew = 0;
        for (long seed = 1; seed <= 400 && sinceNew < 60 && into.size() < 160
                && System.nanoTime() < deadline; seed++) {
            MerchantOffer o = trySample(trade, params, seed * 7919L);
            if (o == null || o.getResult().isEmpty()) {
                continue;
            }
            String k = offerKey(o);
            if (into.containsKey(k)) {
                sinceNew++;
            } else {
                sinceNew = 0;
                into.put(k, rowFromOffer(o, level, false, null, source, null));
            }
        }
    }

    private static TradeRow buildRow(Map<String, TradeRow> distinct, int level, String source) {
        if (distinct == null || distinct.isEmpty()) {
            return null;
        }
        List<TradeRow> variants = new ArrayList<>(distinct.values());
        if (variants.size() == 1) {
            return variants.get(0);
        }
        variants.sort((a, b) -> {
            int c = a.result.getHoverName().getString().compareToIgnoreCase(b.result.getHoverName().getString());
            return c != 0 ? c : Integer.compare(a.costA.getCount(), b.costA.getCount());
        });
        List<TradeRow> grouped = groupVariants(variants);
        if (grouped.size() == 1) {
            return grouped.get(0);
        }
        TradeRow first = grouped.get(0);
        return new TradeRow(level, first.costA, first.costB, first.result, first.maxUses, first.xp,
                true, null, source, grouped);
    }

    private static List<TradeRow> groupVariants(List<TradeRow> raw) {
        Map<String, List<TradeRow>> groups = new LinkedHashMap<>();
        for (TradeRow t : raw) {
            groups.computeIfAbsent(groupKey(t), k -> new ArrayList<>()).add(t);
        }
        List<TradeRow> out = new ArrayList<>();
        for (List<TradeRow> gr : groups.values()) {
            out.add(mergeGroup(gr));
        }
        return out;
    }

    private static String groupKey(TradeRow t) {
        ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(t.result);
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.ITEM.getKey(t.result.getItem()));
        if (ench.isEmpty()) {
            return "u|" + sb + "|" + System.identityHashCode(t);
        }
        if (ench.size() == 1) {
            Holder<Enchantment> e = ench.keySet().iterator().next();
            sb.append('|').append(e.unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
        } else {
            sb.append("|*");
        }
        sb.append('|').append(BuiltInRegistries.ITEM.getKey(t.costA.getItem()));
        sb.append('|').append(BuiltInRegistries.ITEM.getKey(t.costB.getItem()));
        return sb.toString();
    }

    private static TradeRow mergeGroup(List<TradeRow> gr) {
        TradeRow best = gr.get(0);
        int bestScore = -1;
        int minC = Integer.MAX_VALUE;
        int maxC = 0;
        Map<Holder<Enchantment>, int[]> levels = new LinkedHashMap<>();
        Set<Holder<Enchantment>> firstSet = null;
        boolean sameSet = true;
        for (TradeRow t : gr) {
            minC = Math.min(minC, t.costA.getCount());
            maxC = Math.max(maxC, t.costA.getCount());
            ItemEnchantments em = EnchantmentHelper.getEnchantmentsForCrafting(t.result);
            Set<Holder<Enchantment>> keys = new LinkedHashSet<>(em.keySet());
            if (firstSet == null) {
                firstSet = keys;
            } else if (!firstSet.equals(keys)) {
                sameSet = false;
            }
            int score = 0;
            for (Holder<Enchantment> e : keys) {
                int lv = em.getLevel(e);
                score += lv;
                int[] mm = levels.get(e);
                if (mm == null) {
                    levels.put(e, new int[]{lv, lv});
                } else {
                    mm[0] = Math.min(mm[0], lv);
                    mm[1] = Math.max(mm[1], lv);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        String price = minC == maxC ? String.valueOf(minC) : minC + "-" + maxC;
        String note = null;
        if (!levels.isEmpty() && sameSet) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Holder<Enchantment>, int[]> e : levels.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(enchLabel(e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
            note = sb + " §8· §7" + price;
        } else if (gr.size() > 1) {
            note = I18n.get("panoptic.vill.variants", gr.size()) + " §8· §7" + price;
        }
        ItemStack cost = best.costA.copy();
        cost.setCount(minC);
        TradeRow row = new TradeRow(best.level, cost, best.costB, best.result,
                best.maxUses, best.xp, gr.size() > 1, note, best.sourceMod, null);
        row.rawVariants = gr;
        return row;
    }

    private static String enchLabel(Holder<Enchantment> e, int lo, int hi) {
        String name = e.value().description().getString();
        if (e.value().getMaxLevel() <= 1) {
            return name;
        }
        return name + " " + (lo == hi ? lvlText(lo) : lvlText(lo) + "-" + lvlText(hi));
    }

    private static TradeRow structureMapRow(TradeRow row) {
        ItemStack base = row.result == null ? ItemStack.EMPTY : row.result.copy();
        if (base.isEmpty()) {
            return null;
        }
        if (base.is(Items.MAP)) {
            ItemStack filled = new ItemStack(Items.FILLED_MAP, base.getCount());
            filled.applyComponents(base.getComponentsPatch());
            base = filled;
        }
        Component name = modifierName(row.pendingTrade);
        if (name != null) {
            base.set(DataComponents.ITEM_NAME, name);
        }
        return new TradeRow(row.level, row.costA, row.costB, base, row.maxUses, row.xp,
                false, row.note, row.sourceMod, null);
    }

    private static Component modifierName(VillagerTrade trade) {
        for (LootItemFunction fn : modifiersOf(trade)) {
            if (!(fn instanceof SetNameFunction)) {
                continue;
            }
            for (Field f : SetNameFunction.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != Optional.class) {
                    continue;
                }
                try {
                    if (!f.trySetAccessible()) {
                        continue;
                    }
                    if (f.get(fn) instanceof Optional<?> o && o.isPresent()
                            && o.get() instanceof Component c) {
                        return c;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean locatesStructure(VillagerTrade trade) {
        for (LootItemFunction fn : modifiersOf(trade)) {
            if (fn instanceof ExplorationMapFunction) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVariantGated(VillagerTrade trade) {
        for (Field f : VillagerTrade.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != Optional.class
                    || !genericArgIs(f, LootItemCondition.class)) {
                continue;
            }
            try {
                if (!f.trySetAccessible()) {
                    continue;
                }
                return f.get(trade) instanceof Optional<?> o && o.isPresent();
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static List<LootItemFunction> modifiersOf(VillagerTrade trade) {
        if (trade == null) {
            return List.of();
        }
        for (Field f : VillagerTrade.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                if (!f.trySetAccessible()) {
                    continue;
                }
                if (!(f.get(trade) instanceof List<?> raw)) {
                    continue;
                }
                List<LootItemFunction> out = new ArrayList<>();
                for (Object o : raw) {
                    if (o instanceof LootItemFunction fn) {
                        out.add(fn);
                    }
                }
                return out;
            } catch (Throwable ignored) {
            }
        }
        return List.of();
    }

    private static boolean genericArgIs(Field f, Class<?> want) {
        if (!(f.getGenericType() instanceof ParameterizedType pt)) {
            return false;
        }
        Type[] args = pt.getActualTypeArguments();
        return args.length == 1 && args[0] instanceof Class<?> c && want.isAssignableFrom(c);
    }

    private static MerchantOffer trySample(VillagerTrade trade, LootParams params, long seed) {
        try {
            LootContext ctx = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(Optional.empty());
            return trade.getOffer(ctx);
        } catch (Throwable t) {
            return null;
        }
    }

    private static TradeRow rowFromOffer(MerchantOffer o, int level, boolean random, String note,
                                         String source, List<TradeRow> variants) {
        return new TradeRow(level, o.getBaseCostA().copy(), o.getCostB().copy(), o.getResult().copy(),
                o.getMaxUses(), o.getXp(), random, note, source, variants);
    }

    public static void spawnWithTrades(Prof prof) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null || prof == null) {
            return;
        }
        UUID pid = mc.player.getUUID();
        String name = prof.name();
        server.submit(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(pid);
                if (sp == null) {
                    return;
                }
                ServerLevel world = sp.level();
                AbstractVillager ent;
                if (prof.wandering()) {
                    ent = wanderingTraderType().create(world, EntitySpawnReason.COMMAND);
                } else {
                    Villager v = villagerType().create(world, EntitySpawnReason.COMMAND);
                    if (v != null && prof.profession() != null) {
                        Holder<VillagerProfession> ph = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(prof.profession());
                        v.setVillagerData(new VillagerData(v.getVillagerData().type(), ph, 5));
                        v.setVillagerXp(9999);
                    }
                    ent = v;
                }
                if (ent == null) {
                    return;
                }
                MerchantOffers offers = ent.getOffers();
                offers.clear();
                for (TradeRow t : prof.trades()) {
                    if (t.variants != null) {
                        for (TradeRow v : t.variants) {
                            addOffer(offers, v);
                        }
                    } else {
                        addOffer(offers, t);
                    }
                    if (offers.size() >= 200) {
                        break;
                    }
                }
                ent.snapTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
                ent.setCustomName(Component.literal(name));
                ent.setPersistenceRequired();
                world.addFreshEntity(ent);
            } catch (Throwable ignored) {
            }
        });
    }

    private static void addOffer(MerchantOffers offers, TradeRow t) {
        if (t.costA.isEmpty() || t.result.isEmpty() || offers.size() >= 200) {
            return;
        }
        ItemCost costA = new ItemCost(t.costA.getItem(), t.costA.getCount());
        Optional<ItemCost> costB = t.costB.isEmpty() ? Optional.empty()
                : Optional.of(new ItemCost(t.costB.getItem(), t.costB.getCount()));
        offers.add(new MerchantOffer(costA, costB, t.result.copy(),
                0, t.maxUses <= 0 ? 12 : t.maxUses, t.xp, 0.05F));
    }

    private static String offerKey(MerchantOffer o) {
        ItemStack r = o.getResult();
        ItemStack a = o.getBaseCostA();
        ItemStack b = o.getCostB();
        return BuiltInRegistries.ITEM.getKey(r.getItem()) + "|" + r.getCount() + "|" + r.getComponentsPatch()
                + "|" + BuiltInRegistries.ITEM.getKey(a.getItem()) + "|" + a.getCount()
                + "|" + BuiltInRegistries.ITEM.getKey(b.getItem()) + "|" + b.getCount();
    }

    private static String lvlText(int lvl) {
        String key = "enchantment.level." + lvl;
        return Language.getInstance().has(key) ? I18n.get(key) : String.valueOf(lvl);
    }

    private static String structureLocation(Object key) {
        if (key instanceof TagKey<?> tk) {
            return tk.location().toString();
        }
        if (key instanceof ResourceKey<?> rk) {
            return rk.identifier().toString();
        }
        return "?";
    }

    private static String sourceModOf(Object listing, Identifier profId) {
        String modId = SOURCE_CACHE.computeIfAbsent(listing.getClass(), cls -> {
            String cn = cls.getName();
            if (cn.startsWith("net.minecraft")) {
                return "minecraft";
            }
            try {
                var src = cls.getProtectionDomain().getCodeSource();
                String loc = src == null || src.getLocation() == null ? "" : src.getLocation().toString();
                if (!loc.isEmpty()) {
                    for (ModContainer mi : FabricLoader.getInstance().getAllMods()) {
                        try {
                            for (Path jf : mi.getOrigin().getPaths()) {
                                String fn = String.valueOf(jf.getFileName());
                                if (!fn.isEmpty() && loc.contains(fn)) {
                                    return mi.getMetadata().getId();
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            for (ModContainer mi : FabricLoader.getInstance().getAllMods()) {
                String mid = mi.getMetadata().getId();
                String midp = "." + mid + ".";
                if (cn.startsWith(mid + ".") || cn.contains(midp)) {
                    return mid;
                }
            }
            return "";
        });
        if (modId.isEmpty()) {
            return modDisplay(profId.getNamespace());
        }
        return modDisplay(modId);
    }

    private static List<ItemStack> workstations(VillagerProfession prof) {
        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        try {
            BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().forEach(holder -> {
                if (prof.heldJobSite().test(holder)) {
                    holder.value().matchingStates().forEach(state -> blocks.add(state.getBlock()));
                }
            });
        } catch (Throwable ignored) {
        }
        List<ItemStack> out = new ArrayList<>();
        for (Block b : blocks) {
            ItemStack stack = new ItemStack(b);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
            if (out.size() >= 8) {
                break;
            }
        }
        return out;
    }

    private static String profName(Identifier id, boolean wandering) {
        if (wandering) {
            return I18n.get("entity.minecraft.wandering_trader");
        }
        String[] keys = {
                "entity.minecraft.villager." + id.getPath(),
                "entity.minecraft.villager." + id.getNamespace() + "." + id.getPath(),
                "entity." + id.getNamespace() + ".villager." + id.getPath()
        };
        for (String key : keys) {
            if (Language.getInstance().has(key)) {
                return I18n.get(key);
            }
        }
        return prettify(id.getPath());
    }

    private static String prettify(String path) {
        String[] parts = path.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String modDisplay(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(c -> c.getMetadata().getName())
                .orElse(namespace);
    }

    @SuppressWarnings("unchecked")
    private static EntityType<Villager> villagerType() {
        return (EntityType<Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("villager"));
    }

    @SuppressWarnings("unchecked")
    private static EntityType<WanderingTrader> wanderingTraderType() {
        return (EntityType<WanderingTrader>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("wandering_trader"));
    }

    private static Villager createDummy() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : villagerType().create(mc.level, EntitySpawnReason.COMMAND);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static WanderingTrader createWanderDummy() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : wanderingTraderType().create(mc.level, EntitySpawnReason.COMMAND);
        } catch (Throwable ignored) {
            return null;
        }
    }
}