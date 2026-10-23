package net.mokich.panoptic.screen.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class PreviewParsers {
    private PreviewParsers() {}

    public record Ingredient(List<ItemStack> options) {}
    public record Recipe(String layout, int gw, int gh, Ingredient[] grid, ItemStack result, String note) {}
    public record Tag(boolean blocks, List<ItemStack> items, List<String> refs, List<String> missing) {}
    public record LootRow(ItemStack icon, String name, int weight, float chance, String note) {}
    public record Loot(List<LootRow> rows, int pools) {}
    public record LangEntry(String key, String value) {}
    public record Lang(List<LangEntry> entries) {}
    public record TexRef(String key, ResourceLocation texture, boolean exists) {}
    public record Model(List<TexRef> textures, String parent) {}
    public record Advancement(ItemStack icon, String title, String description, String parent, int criteria) {}
    public record BlockstateEntry(String variant, String model) {}
    public record Blockstate(List<BlockstateEntry> entries) {}
    public record Lint(boolean ok, List<String> issues) {}
    public record Parsed(JsonObject json, Lint lint, Object view) {}

    public static Parsed parse(String path, String text) {
        JsonObject json;
        try {
            json = GsonHelper.parse(text);
        } catch (Throwable t) {
            return new Parsed(null, new Lint(false, List.of("JSON: " + shortMsg(t))), null);
        }
        List<String> issues = new ArrayList<>();
        Object view = null;
        try {
            if (path.contains("/advancement")) {
                view = parseAdvancement(json);
            } else if (path.contains("/recipe")) {
                view = parseRecipe(json);
                lintRecipe(json, issues);
            } else if (path.contains("/tags/items")) {
                view = parseTag(json, false);
                lintTag(json, false, issues);
            } else if (path.contains("/tags/blocks")) {
                view = parseTag(json, true);
                lintTag(json, true, issues);
            } else if (path.contains("/loot_table")) {
                view = parseLoot(json);
                lintLoot(json, issues);
            } else if (path.contains("/lang/")) {
                view = parseLang(json);
            } else if (path.contains("/models/")) {
                view = parseModel(json);
                lintModel(json, issues);
            } else if (path.contains("/blockstates/")) {
                view = parseBlockstate(json);
                lintBlockstate(json, issues);
            }
        } catch (Throwable ignored) {
        }
        return new Parsed(json, new Lint(issues.isEmpty(), issues), view);
    }

    private static Recipe parseRecipe(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type", "");
        if (type.contains("crafting_shaped")) {
            JsonArray pattern = o.getAsJsonArray("pattern");
            JsonObject key = o.getAsJsonObject("key");
            if (pattern == null || key == null) {
                return null;
            }
            int gh = pattern.size();
            int gw = 1;
            String[] rows = new String[gh];
            for (int i = 0; i < gh; i++) {
                rows[i] = pattern.get(i).getAsString();
                gw = Math.max(gw, rows[i].length());
            }
            Ingredient[] grid = new Ingredient[gw * gh];
            for (int y = 0; y < gh; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char c = rows[y].charAt(x);
                    if (c != ' ' && key.has(String.valueOf(c))) {
                        grid[y * gw + x] = ingredient(key.get(String.valueOf(c)));
                    }
                }
            }
            return new Recipe("crafting", gw, gh, grid, result(o), null);
        }
        if (type.contains("crafting_shapeless")) {
            JsonArray ings = o.getAsJsonArray("ingredients");
            if (ings == null) {
                return null;
            }
            int n = Math.min(9, ings.size());
            int gw = Math.min(3, Math.max(1, n));
            int gh = Math.max(1, (n + gw - 1) / gw);
            Ingredient[] grid = new Ingredient[gw * gh];
            for (int i = 0; i < n; i++) {
                grid[i] = ingredient(ings.get(i));
            }
            return new Recipe("crafting", gw, gh, grid, result(o), null);
        }
        if (type.contains("smelting") || type.contains("blasting") || type.contains("smoking") || type.contains("campfire")) {
            String note = null;
            if (o.has("cookingtime")) {
                note = (o.get("cookingtime").getAsInt() / 20.0) + "s";
            }
            return new Recipe("cooking", 1, 1, new Ingredient[]{ingredient(o.get("ingredient"))}, result(o), note);
        }
        if (type.contains("stonecutting")) {
            return new Recipe("stonecutting", 1, 1, new Ingredient[]{ingredient(o.get("ingredient"))}, result(o), null);
        }
        if (type.contains("smithing")) {
            Ingredient[] grid = {ingredient(o.get("template")), ingredient(o.get("base")), ingredient(o.get("addition"))};
            return new Recipe("smithing", 3, 1, grid, result(o), null);
        }
        return null;
    }

    private static Ingredient ingredient(JsonElement e) {
        List<ItemStack> out = new ArrayList<>();
        addIngredient(e, out);
        return new Ingredient(out);
    }

    private static void addIngredient(JsonElement e, List<ItemStack> out) {
        if (e == null) {
            return;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                addIngredient(x, out);
            }
            return;
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("item")) {
                ItemStack s = itemStack(o.get("item").getAsString());
                if (!s.isEmpty()) {
                    out.add(s);
                }
            } else if (o.has("tag")) {
                out.addAll(itemsOfTag(o.get("tag").getAsString()));
            }
        }
    }

    private static ItemStack result(JsonObject o) {
        JsonElement r = o.get("result");
        if (r == null) {
            return ItemStack.EMPTY;
        }
        if (r.isJsonPrimitive()) {
            return stack(r.getAsString(), o.has("count") ? o.get("count").getAsInt() : 1);
        }
        JsonObject ro = r.getAsJsonObject();
        String id = ro.has("item") ? ro.get("item").getAsString() : (ro.has("id") ? ro.get("id").getAsString() : null);
        return stack(id, ro.has("count") ? ro.get("count").getAsInt() : 1);
    }

    private static Tag parseTag(JsonObject o, boolean blocks) {
        List<ItemStack> items = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        JsonArray values = o.getAsJsonArray("values");
        if (values != null) {
            for (JsonElement e : values) {
                String id;
                boolean required = true;
                if (e.isJsonObject()) {
                    JsonObject vo = e.getAsJsonObject();
                    id = vo.get("id").getAsString();
                    required = !vo.has("required") || vo.get("required").getAsBoolean();
                } else {
                    id = e.getAsString();
                }
                if (id.startsWith("#")) {
                    refs.add(id);
                    continue;
                }
                ItemStack st = blocks ? blockStack(id) : itemStack(id);
                if (st.isEmpty()) {
                    if (required) {
                        missing.add(id);
                    }
                } else {
                    items.add(st);
                }
            }
        }
        return new Tag(blocks, items, refs, missing);
    }

    private static Loot parseLoot(JsonObject o) {
        List<LootRow> rows = new ArrayList<>();
        JsonArray pools = o.getAsJsonArray("pools");
        int np = pools == null ? 0 : pools.size();
        if (pools != null) {
            for (JsonElement pe : pools) {
                JsonArray entries = pe.getAsJsonObject().getAsJsonArray("entries");
                if (entries == null) {
                    continue;
                }
                int total = 0;
                for (JsonElement ee : entries) {
                    JsonObject en = ee.getAsJsonObject();
                    total += en.has("weight") ? en.get("weight").getAsInt() : 1;
                }
                for (JsonElement ee : entries) {
                    collectLoot(ee.getAsJsonObject(), total, rows);
                }
            }
        }
        return new Loot(rows, np);
    }

    private static void collectLoot(JsonObject en, int total, List<LootRow> rows) {
        if (rows.size() >= 200) {
            return;
        }
        String type = en.has("type") ? en.get("type").getAsString() : "";
        int weight = en.has("weight") ? en.get("weight").getAsInt() : 1;
        float chance = total > 0 ? weight * 100.0F / total : 0;
        if (type.contains("alternatives") || type.contains("group") || type.contains("sequence")) {
            JsonArray children = en.getAsJsonArray("children");
            if (children != null) {
                for (JsonElement c : children) {
                    collectLoot(c.getAsJsonObject(), total, rows);
                }
            }
        } else if (type.contains("item")) {
            String name = en.get("name").getAsString();
            rows.add(new LootRow(itemStack(name), name, weight, chance, null));
        } else if (type.contains("tag")) {
            rows.add(new LootRow(ItemStack.EMPTY, "#" + en.get("name").getAsString(), weight, chance, "tag"));
        } else if (type.contains("loot_table")) {
            rows.add(new LootRow(ItemStack.EMPTY, en.get("name").getAsString(), weight, chance, "table"));
        } else if (type.contains("empty")) {
            rows.add(new LootRow(ItemStack.EMPTY, "—", weight, chance, "empty"));
        }
    }

    private static Lang parseLang(JsonObject o) {
        List<LangEntry> entries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                entries.add(new LangEntry(e.getKey(), e.getValue().getAsString()));
            }
            if (entries.size() >= 4000) {
                break;
            }
        }
        return new Lang(entries);
    }

    private static Model parseModel(JsonObject o) {
        List<TexRef> refs = new ArrayList<>();
        if (o.has("textures") && o.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("textures").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) {
                    continue;
                }
                String tex = e.getValue().getAsString();
                if (tex.startsWith("#")) {
                    continue;
                }
                ResourceLocation t = rl(tex, "textures/", ".png");
                refs.add(new TexRef(e.getKey(), t, assetExists(t)));
            }
        }
        return new Model(refs, o.has("parent") ? o.get("parent").getAsString() : null);
    }

    private static Advancement parseAdvancement(JsonObject o) {
        JsonObject display = o.has("display") && o.get("display").isJsonObject() ? o.getAsJsonObject("display") : null;
        ItemStack icon = ItemStack.EMPTY;
        String title = "";
        String desc = "";
        if (display != null) {
            title = text(display.get("title"));
            desc = text(display.get("description"));
            if (display.has("icon") && display.get("icon").isJsonObject()) {
                JsonObject ic = display.getAsJsonObject("icon");
                String id = ic.has("item") ? ic.get("item").getAsString() : (ic.has("id") ? ic.get("id").getAsString() : null);
                icon = id == null ? ItemStack.EMPTY : itemStack(id);
            }
        }
        int criteria = o.has("criteria") && o.get("criteria").isJsonObject() ? o.getAsJsonObject("criteria").size() : 0;
        return new Advancement(icon, title, desc, o.has("parent") ? o.get("parent").getAsString() : null, criteria);
    }

    private static String text(JsonElement e) {
        if (e == null) {
            return "";
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("translate")) {
                return o.get("translate").getAsString();
            }
            if (o.has("text")) {
                return o.get("text").getAsString();
            }
        }
        if (e.isJsonArray() && !e.getAsJsonArray().isEmpty()) {
            return text(e.getAsJsonArray().get(0));
        }
        return "";
    }

    private static Blockstate parseBlockstate(JsonObject o) {
        List<BlockstateEntry> entries = new ArrayList<>();
        if (o.has("variants") && o.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("variants").entrySet()) {
                entries.add(new BlockstateEntry(e.getKey().isEmpty() ? "(default)" : e.getKey(), modelOf(e.getValue())));
            }
        } else if (o.has("multipart") && o.get("multipart").isJsonArray()) {
            for (JsonElement part : o.getAsJsonArray("multipart")) {
                JsonObject po = part.getAsJsonObject();
                String when = po.has("when") ? po.get("when").toString() : "*";
                entries.add(new BlockstateEntry(when, modelOf(po.get("apply"))));
            }
        }
        return new Blockstate(entries);
    }

    private static String modelOf(JsonElement e) {
        if (e == null) {
            return "";
        }
        if (e.isJsonArray() && !e.getAsJsonArray().isEmpty()) {
            e = e.getAsJsonArray().get(0);
        }
        if (e.isJsonObject() && e.getAsJsonObject().has("model")) {
            return e.getAsJsonObject().get("model").getAsString();
        }
        return "";
    }

    private static void lintRecipe(JsonObject o, List<String> issues) {
        for (String key : new String[]{"ingredient", "base", "addition", "template"}) {
            checkIngredient(o.get(key), issues);
        }
        if (o.has("ingredients")) {
            for (JsonElement e : o.getAsJsonArray("ingredients")) {
                checkIngredient(e, issues);
            }
        }
        if (o.has("key")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("key").entrySet()) {
                checkIngredient(e.getValue(), issues);
            }
        }
        ItemStack res = result(o);
        if (o.has("result") && res.isEmpty()) {
            issues.add("result: " + o.get("result"));
        }
    }

    private static void checkIngredient(JsonElement e, List<String> issues) {
        if (e == null) {
            return;
        }
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                checkIngredient(x, issues);
            }
            return;
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("item") && itemStack(o.get("item").getAsString()).isEmpty()) {
                issues.add("item ?: " + o.get("item").getAsString());
            } else if (o.has("tag") && !tagExists(o.get("tag").getAsString(), false)) {
                issues.add("tag ?: #" + o.get("tag").getAsString());
            }
        }
    }

    private static void lintTag(JsonObject o, boolean blocks, List<String> issues) {
        JsonArray values = o.getAsJsonArray("values");
        if (values == null) {
            return;
        }
        for (JsonElement e : values) {
            String id = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
            boolean required = !e.isJsonObject() || !e.getAsJsonObject().has("required") || e.getAsJsonObject().get("required").getAsBoolean();
            if (!required) {
                continue;
            }
            if (id.startsWith("#")) {
                if (!tagExists(id.substring(1), blocks)) {
                    issues.add("tag ?: " + id);
                }
            } else if ((blocks ? blockStack(id) : itemStack(id)).isEmpty()) {
                issues.add((blocks ? "block ?: " : "item ?: ") + id);
            }
        }
    }

    private static void lintLoot(JsonObject o, List<String> issues) {
        JsonArray pools = o.getAsJsonArray("pools");
        if (pools == null) {
            return;
        }
        for (JsonElement pe : pools) {
            JsonArray entries = pe.getAsJsonObject().getAsJsonArray("entries");
            if (entries == null) {
                continue;
            }
            for (JsonElement ee : entries) {
                JsonObject en = ee.getAsJsonObject();
                String type = en.has("type") ? en.get("type").getAsString() : "";
                if (type.contains("item") && en.has("name") && itemStack(en.get("name").getAsString()).isEmpty()) {
                    issues.add("item ?: " + en.get("name").getAsString());
                }
            }
        }
    }

    private static void lintModel(JsonObject o, List<String> issues) {
        if (o.has("parent")) {
            String parent = o.get("parent").getAsString();
            if (!parent.startsWith("builtin/") && !assetExists(rl(parent, "models/", ".json"))) {
                issues.add("parent ?: " + parent);
            }
        }
        if (o.has("textures") && o.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("textures").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) {
                    continue;
                }
                String tex = e.getValue().getAsString();
                if (!tex.startsWith("#") && !assetExists(rl(tex, "textures/", ".png"))) {
                    issues.add("texture ?: " + tex);
                }
            }
        }
    }

    private static void lintBlockstate(JsonObject o, List<String> issues) {
        if (o.has("variants")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("variants").entrySet()) {
                checkModelRef(e.getValue(), issues);
            }
        }
    }

    private static void checkModelRef(JsonElement e, List<String> issues) {
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                checkModelRef(x, issues);
            }
        } else if (e.isJsonObject() && e.getAsJsonObject().has("model")) {
            String model = e.getAsJsonObject().get("model").getAsString();
            if (!assetExists(rl(model, "models/", ".json"))) {
                issues.add("model ?: " + model);
            }
        }
    }

    private static ItemStack stack(String id, int count) {
        ItemStack s = itemStack(id);
        if (!s.isEmpty()) {
            s.setCount(Math.max(1, count));
        }
        return s;
    }

    private static ItemStack itemStack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static ItemStack blockStack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        Block b = BuiltInRegistries.BLOCK.get(rl);
        ItemStack s = b == null ? ItemStack.EMPTY : new ItemStack(b);
        return s.isEmpty() ? itemStack(id) : s;
    }

    private static List<ItemStack> itemsOfTag(String id) {
        List<ItemStack> out = new ArrayList<>();
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl))
                        .ifPresent(named -> named.forEach(h -> out.add(new ItemStack(h.value()))));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean tagExists(String id, boolean blocks) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                return false;
            }
            return blocks
                    ? BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl)).isPresent()
                    : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl)).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private static ResourceLocation rl(String id, String prefix, String suffix) {
        ResourceLocation base = ResourceLocation.tryParse(id);
        return base == null ? null : ResourceLocation.tryParse(base.getNamespace() + ":" + prefix + base.getPath() + suffix);
    }

    private static boolean assetExists(ResourceLocation rl) {
        if (rl == null) {
            return false;
        }
        try {
            return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null) {
            return t.getClass().getSimpleName();
        }
        m = m.replaceAll("[\\r\\n]+", " ");
        return m.length() > 400 ? m.substring(0, 400) + "…" : m;
    }
}