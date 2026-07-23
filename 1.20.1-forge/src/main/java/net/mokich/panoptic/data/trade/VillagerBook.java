package net.mokich.panoptic.data.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;

public final class VillagerBook {
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
        public transient VillagerTrades.ItemListing listing;
        public transient boolean serverPending;

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
    }

    public record Prof(ResourceLocation id, String name, String modName, boolean wandering,
                       VillagerProfession profession, List<ItemStack> workstations,
                       List<TradeRow> trades, String searchBlob) {
    }

    private static final Set<String> RANDOM_LISTINGS = Set.of(
            "EnchantBookForEmeralds", "EnchantedItemForEmeralds", "TippedArrowForItemsAndEmeralds",
            "DyedArmorForEmeralds", "SuspiciousStewForEmerald", "TreasureMapForEmeralds");
    private static final Map<Class<?>, String> SOURCE_CACHE = new HashMap<>();

    private VillagerBook() {
    }

    public static List<Prof> build() {
        List<Prof> out = new ArrayList<>();
        Villager dummy = createDummy();
        for (VillagerProfession prof : ForgeRegistries.VILLAGER_PROFESSIONS.getValues()) {
            ResourceLocation id = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(prof);
            if (id == null) {
                continue;
            }
            List<TradeRow> trades = new ArrayList<>();
            Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = VillagerTrades.TRADES.get(prof);
            if (byLevel != null) {
                int[] levels = byLevel.keySet().toIntArray();
                Arrays.sort(levels);
                for (int lvl : levels) {
                    VillagerTrades.ItemListing[] listings = byLevel.get(lvl);
                    if (listings == null) {
                        continue;
                    }
                    for (VillagerTrades.ItemListing listing : listings) {
                        trades.add(sample(listing, lvl, dummy, id));
                    }
                }
            }
            out.add(make(id, false, prof, workstations(prof), trades));
        }
        out.sort((a, b) -> {
            int m = a.modName().compareToIgnoreCase(b.modName());
            return m != 0 ? m : a.name().compareToIgnoreCase(b.name());
        });
        List<TradeRow> wander = new ArrayList<>();
        LivingEntity wanderDummy = createWanderDummy();
        ResourceLocation wid = ResourceLocation.withDefaultNamespace("wandering_trader");
        int[] wLevels = VillagerTrades.WANDERING_TRADER_TRADES.keySet().toIntArray();
        Arrays.sort(wLevels);
        for (int lvl : wLevels) {
            for (VillagerTrades.ItemListing listing : VillagerTrades.WANDERING_TRADER_TRADES.get(lvl)) {
                wander.add(sample(listing, lvl, wanderDummy, wid));
            }
        }
        out.add(0, make(wid, true, null, List.of(), wander));
        return out;
    }

    private static Prof make(ResourceLocation id, boolean wandering, VillagerProfession prof,
                             List<ItemStack> stations, List<TradeRow> trades) {
        String name = profName(id, wandering);
        String mod = modDisplay(id.getNamespace());
        StringBuilder blob = new StringBuilder();
        blob.append(name.toLowerCase(Locale.ROOT)).append(' ')
                .append(id).append(' ')
                .append(mod.toLowerCase(Locale.ROOT)).append(' ');
        for (TradeRow t : trades) {
            appendRow(blob, t);
            if (t.variants != null) {
                for (TradeRow v : t.variants) {
                    appendRow(blob, v);
                }
            }
        }
        return new Prof(id, name, mod, wandering, prof, stations, trades, blob.toString());
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
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null) {
            blob.append(key).append(' ');
        }
        blob.append(stack.getHoverName().getString().toLowerCase(Locale.ROOT)).append(' ');
    }

    private static TradeRow sample(VillagerTrades.ItemListing listing, int level, LivingEntity dummy,
                                   ResourceLocation profId) {
        String simple = listing.getClass().getSimpleName();
        String source = sourceModOf(listing, profId);
        if ("EnchantBookForEmeralds".equals(simple)) {
            TradeRow books = enchantBookRow(listing, level, source);
            if (books != null) {
                return books;
            }
        }
        boolean random = RANDOM_LISTINGS.contains(simple);
        MerchantOffer first = tryOffer(listing, dummy, 1L);
        if (first == null || first.getResult().isEmpty()) {
            TradeRow row = describeNullOffer(listing, level, source, random, simple);
            row.listing = listing;
            row.serverPending = true;
            return row;
        }
        List<TradeRow> variants = sampleVariants(listing, level, dummy, source, first);
        if (variants != null) {
            variants = groupVariants(variants);
            if (variants.size() == 1) {
                return variants.get(0);
            }
            if (variants.isEmpty()) {
                variants = null;
            }
        }
        return rowFromOffer(first, level, variants != null || random, null, source, variants);
    }

    private static MerchantOffer tryOffer(VillagerTrades.ItemListing listing, LivingEntity dummy, long seed) {
        if (dummy == null) {
            return null;
        }
        try {
            return listing.getOffer(dummy, RandomSource.create(seed));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void resolvePending(Prof prof) {
        if (prof == null || prof.wandering()) {
            return;
        }
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        List<TradeRow> trades = prof.trades();
        for (int i = 0; i < trades.size(); i++) {
            TradeRow row = trades.get(i);
            if (!row.serverPending || row.listing == null) {
                continue;
            }
            row.serverPending = false;
            int idx = i;
            VillagerTrades.ItemListing lst = row.listing;
            int lvl = row.level;
            String source = row.sourceMod;
            server.submit(() -> serverSample(server, prof, idx, lst, lvl, source));
        }
    }

    private static void serverSample(MinecraftServer server, Prof prof, int idx,
                                     VillagerTrades.ItemListing listing, int level, String source) {
        try {
            ServerLevel world = server.overworld();
            if (world == null) {
                return;
            }
            Villager sv = EntityType.VILLAGER.create(world);
            if (sv == null) {
                return;
            }
            BlockPos at = world.players().isEmpty() ? world.getSharedSpawnPos() : world.players().get(0).blockPosition();
            sv.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            MerchantOffer o = null;
            for (long seed = 1; seed <= 6 && o == null; seed++) {
                try {
                    o = listing.getOffer(sv, RandomSource.create(seed * 9127L));
                } catch (Throwable ignored) {
                }
            }
            sv.discard();
            if (o == null || o.getResult().isEmpty()) {
                TradeRow map = reflectMapTrade(listing, level, source);
                String struct = map != null && map.note != null ? map.note : null;
                Minecraft.getInstance().execute(() -> {
                    String note = I18n.get("panoptic.vill.broken") + (struct == null ? "" : " §8" + struct);
                    List<TradeRow> list = prof.trades();
                    if (idx >= 0 && idx < list.size()) {
                        list.set(idx, new TradeRow(level, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                                0, 0, true, note, source, null));
                    }
                });
                return;
            }
            ItemStack a = o.getBaseCostA().copy();
            ItemStack b = o.getCostB().copy();
            ItemStack r = o.getResult().copy();
            int maxUses = o.getMaxUses();
            int xp = o.getXp();
            Minecraft.getInstance().execute(() -> {
                List<TradeRow> list = prof.trades();
                if (idx >= 0 && idx < list.size()) {
                    list.set(idx, new TradeRow(level, a, b, r, maxUses, xp, false, null, source, null));
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static TradeRow rowFromOffer(MerchantOffer o, int level, boolean random, String note,
                                         String source, List<TradeRow> variants) {
        return new TradeRow(level, o.getBaseCostA().copy(), o.getCostB().copy(), o.getResult().copy(),
                o.getMaxUses(), o.getXp(), random, note, source, variants);
    }

    private static List<TradeRow> sampleVariants(VillagerTrades.ItemListing listing, int level,
                                                 LivingEntity dummy, String source, MerchantOffer first) {
        Map<String, TradeRow> distinct = new LinkedHashMap<>();
        distinct.put(offerKey(first), rowFromOffer(first, level, false, null, source, null));
        int sinceNew = 0;
        for (long seed = 2; seed <= 400 && sinceNew < 60 && distinct.size() < 120; seed++) {
            MerchantOffer o = tryOffer(listing, dummy, seed * 7919L);
            if (o == null) {
                continue;
            }
            String k = offerKey(o);
            if (distinct.containsKey(k)) {
                sinceNew++;
            } else {
                sinceNew = 0;
                distinct.put(k, rowFromOffer(o, level, false, null, source, null));
            }
        }
        if (distinct.size() <= 1) {
            return null;
        }
        List<TradeRow> list = new ArrayList<>(distinct.values());
        list.sort((a, b) -> {
            int c = a.result.getHoverName().getString().compareToIgnoreCase(b.result.getHoverName().getString());
            return c != 0 ? c : Integer.compare(a.costA.getCount(), b.costA.getCount());
        });
        return list;
    }

    private static List<TradeRow> groupVariants(List<TradeRow> raw) {
        Map<String, List<TradeRow>> groups = new LinkedHashMap<>();
        for (TradeRow t : raw) {
            groups.computeIfAbsent(groupKey(t), k -> new ArrayList<>()).add(t);
        }
        if (groups.size() == raw.size()) {
            return raw;
        }
        List<TradeRow> out = new ArrayList<>();
        for (List<TradeRow> gr : groups.values()) {
            out.add(gr.size() == 1 ? gr.get(0) : mergeGroup(gr));
        }
        return out;
    }

    private static String groupKey(TradeRow t) {
        Map<Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(t.result);
        if (ench.isEmpty()) {
            return "u|" + System.identityHashCode(t);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ForgeRegistries.ITEMS.getKey(t.result.getItem()));
        List<String> enchIds = new ArrayList<>();
        for (Enchantment e : ench.keySet()) {
            enchIds.add(String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(e)));
        }
        Collections.sort(enchIds);
        sb.append('|').append(enchIds);
        sb.append('|').append(ForgeRegistries.ITEMS.getKey(t.costA.getItem()));
        sb.append('|').append(ForgeRegistries.ITEMS.getKey(t.costB.getItem()));
        return sb.toString();
    }

    private static TradeRow mergeGroup(List<TradeRow> gr) {
        TradeRow best = gr.get(0);
        int bestLvl = -1;
        int minC = Integer.MAX_VALUE;
        int maxC = 0;
        int minL = Integer.MAX_VALUE;
        int maxL = 0;
        Enchantment single = null;
        boolean sameSingle = true;
        for (TradeRow t : gr) {
            minC = Math.min(minC, t.costA.getCount());
            maxC = Math.max(maxC, t.costA.getCount());
            Map<Enchantment, Integer> em = EnchantmentHelper.getEnchantments(t.result);
            if (em.size() == 1) {
                Map.Entry<Enchantment, Integer> en = em.entrySet().iterator().next();
                if (single == null) {
                    single = en.getKey();
                } else if (single != en.getKey()) {
                    sameSingle = false;
                }
                minL = Math.min(minL, en.getValue());
                maxL = Math.max(maxL, en.getValue());
                if (en.getValue() > bestLvl) {
                    bestLvl = en.getValue();
                    best = t;
                }
            } else {
                sameSingle = false;
            }
        }
        String price = minC == maxC ? String.valueOf(minC) : minC + "–" + maxC;
        String note;
        if (sameSingle && single != null) {
            note = I18n.get(single.getDescriptionId())
                    + (maxL > minL ? " " + lvlText(minL) + "–" + lvlText(maxL) : "")
                    + " §8· §7" + price;
        } else {
            note = I18n.get("panoptic.vill.variants", gr.size()) + " §8· §7" + price;
        }
        ItemStack cost = best.costA.copy();
        cost.setCount(minC);
        return new TradeRow(best.level, cost, best.costB, best.result,
                best.maxUses, best.xp, false, note, best.sourceMod, null);
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
                ServerLevel world = sp.serverLevel();
                AbstractVillager ent;
                if (prof.wandering()) {
                    ent = EntityType.WANDERING_TRADER.create(world);
                } else {
                    Villager v = EntityType.VILLAGER.create(world);
                    if (v != null && prof.profession() != null) {
                        v.setVillagerData(v.getVillagerData().setProfession(prof.profession()).setLevel(5));
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
                ent.moveTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
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
        offers.add(new MerchantOffer(t.costA.copy(), t.costB.copy(), t.result.copy(),
                0, t.maxUses <= 0 ? 12 : t.maxUses, t.xp, 0.05F));
    }

    private static String offerKey(MerchantOffer o) {
        ItemStack r = o.getResult();
        ItemStack a = o.getBaseCostA();
        ItemStack b = o.getCostB();
        return ForgeRegistries.ITEMS.getKey(r.getItem()) + "|" + r.getCount() + "|" + r.getTag()
                + "|" + ForgeRegistries.ITEMS.getKey(a.getItem()) + "|" + a.getCount()
                + "|" + ForgeRegistries.ITEMS.getKey(b.getItem()) + "|" + b.getCount();
    }

    private static TradeRow enchantBookRow(VillagerTrades.ItemListing listing, int level, String source) {
        try {
            List<TradeRow> variants = new ArrayList<>();
            for (Enchantment ench : ForgeRegistries.ENCHANTMENTS.getValues()) {
                if (!ench.isTradeable()) {
                    continue;
                }
                int minL = ench.getMinLevel();
                int maxL = ench.getMaxLevel();
                int minCost = 2 + 3 * minL;
                int maxCost = 2 + (5 + maxL * 10 - 1) + 3 * maxL;
                if (ench.isTreasureOnly()) {
                    minCost *= 2;
                    maxCost *= 2;
                }
                minCost = Math.min(64, minCost);
                maxCost = Math.min(64, maxCost);
                ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, maxL));
                String note = I18n.get(ench.getDescriptionId())
                        + (maxL > 1 ? " " + lvlText(minL) + "–" + lvlText(maxL) : "")
                        + " §8· §7" + minCost + "–" + maxCost;
                variants.add(new TradeRow(level, new ItemStack(Items.EMERALD, minCost), new ItemStack(Items.BOOK),
                        book, 12, 2 + level * 5, false, note, source, null));
            }
            if (variants.isEmpty()) {
                return null;
            }
            variants.sort((a, b) -> a.note.compareToIgnoreCase(b.note));
            return new TradeRow(level, new ItemStack(Items.EMERALD, 5), new ItemStack(Items.BOOK),
                    new ItemStack(Items.ENCHANTED_BOOK), 12, 2 + level * 5, true, null, source, variants);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String lvlText(int lvl) {
        String key = "enchantment.level." + lvl;
        return I18n.exists(key) ? I18n.get(key) : String.valueOf(lvl);
    }

    private static TradeRow describeNullOffer(VillagerTrades.ItemListing listing, int level, String source,
                                              boolean random, String simple) {
        TradeRow map = reflectMapTrade(listing, level, source);
        if (map != null) {
            return map;
        }
        if (simple.toLowerCase(Locale.ROOT).contains("map")) {
            return new TradeRow(level, new ItemStack(Items.EMERALD), new ItemStack(Items.COMPASS),
                    new ItemStack(Items.FILLED_MAP), 12, 2 + level * 5, true,
                    I18n.get("panoptic.vill.treasure_map"), source, null);
        }
        boolean lambda = listing.getClass().isSynthetic() || simple.contains("$$Lambda");
        String note = lambda ? I18n.get("panoptic.vill.special_trade") : simple;
        return new TradeRow(level, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, random, note, source, null);
    }

    private static TradeRow reflectMapTrade(VillagerTrades.ItemListing listing, int level, String source) {
        Object destination = null;
        List<Integer> ints = new ArrayList<>();
        for (Field f : listing.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
            } catch (Throwable t) {
                continue;
            }
            Class<?> type = f.getType();
            try {
                if (destination == null && (TagKey.class.isAssignableFrom(type) || ResourceKey.class.isAssignableFrom(type))) {
                    destination = f.get(listing);
                } else if (type == int.class) {
                    ints.add(f.getInt(listing));
                }
            } catch (Throwable ignored) {
            }
        }
        if (destination == null) {
            return null;
        }
        int emeralds = !ints.isEmpty() ? ints.get(0) : 8;
        int maxUses = ints.size() > 1 ? ints.get(1) : 12;
        int xp = ints.size() > 2 ? ints.get(2) : 5;
        return new TradeRow(level, new ItemStack(Items.EMERALD, emeralds), new ItemStack(Items.COMPASS),
                new ItemStack(Items.FILLED_MAP), maxUses, xp, true, "#" + structureLocation(destination), source, null);
    }

    private static String structureLocation(Object key) {
        if (key instanceof TagKey<?> tk) {
            return tk.location().toString();
        }
        if (key instanceof ResourceKey<?> rk) {
            return rk.location().toString();
        }
        return "?";
    }

    private static String sourceModOf(Object listing, ResourceLocation profId) {
        String modId = SOURCE_CACHE.computeIfAbsent(listing.getClass(), cls -> {
            String cn = cls.getName();
            if (cn.startsWith("net.minecraft")) {
                return "minecraft";
            }
            try {
                var src = cls.getProtectionDomain().getCodeSource();
                String loc = src == null || src.getLocation() == null ? "" : src.getLocation().toString();
                if (!loc.isEmpty()) {
                    for (IModInfo mi : ModList.get().getMods()) {
                        try {
                            String fn = mi.getOwningFile().getFile().getFileName();
                            if (fn != null && !fn.isEmpty() && loc.contains(fn)) {
                                return mi.getModId();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            for (IModInfo mi : ModList.get().getMods()) {
                String midp = "." + mi.getModId() + ".";
                if (cn.startsWith(mi.getModId() + ".") || cn.contains(midp)) {
                    return mi.getModId();
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
            BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders().forEach(holder -> {
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

    private static String profName(ResourceLocation id, boolean wandering) {
        if (wandering) {
            return I18n.get("entity.minecraft.wandering_trader");
        }
        String[] keys = {
                "entity.minecraft.villager." + id.getPath(),
                "entity.minecraft.villager." + id.getNamespace() + "." + id.getPath(),
                "entity." + id.getNamespace() + ".villager." + id.getPath()
        };
        for (String key : keys) {
            if (I18n.exists(key)) {
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
        return ModList.get().getModContainerById(namespace)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    private static Villager createDummy() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : EntityType.VILLAGER.create(mc.level);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static WanderingTrader createWanderDummy() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : EntityType.WANDERING_TRADER.create(mc.level);
        } catch (Throwable ignored) {
            return null;
        }
    }
}