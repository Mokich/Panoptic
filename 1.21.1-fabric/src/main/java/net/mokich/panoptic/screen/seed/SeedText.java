package net.mokich.panoptic.screen.seed;

import net.mokich.panoptic.data.seed.SeedMap;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.fabricmc.loader.api.FabricLoader;

public final class SeedText {
    private SeedText() {}

    public static String dimName(ResourceKey<Level> key) {
        if (key == Level.OVERWORLD) {
            return I18n.get("panoptic.seed.dim.overworld");
        }
        if (key == Level.NETHER) {
            return I18n.get("panoptic.seed.dim.nether");
        }
        if (key == Level.END) {
            return I18n.get("panoptic.seed.dim.end");
        }
        return prettify(key.location().getPath());
    }

    public static String structName(Holder<Structure> s) {
        ResourceLocation id = s.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return "?";
        }
        String direct = "structure." + id.getNamespace() + "." + id.getPath();
        if (I18n.exists(direct)) {
            return I18n.get(direct);
        }
        String mine = "panoptic.struct." + id.getNamespace() + "." + id.getPath();
        if (I18n.exists(mine)) {
            return I18n.get(mine);
        }
        return prettify(id.getPath());
    }

    public static String prettify(String path) {
        String[] words = path.replace('/', ' ').replace('_', ' ').trim().split(" +");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String modDisplay(String ns) {
        if ("minecraft".equals(ns)) {
            return "Minecraft";
        }
        return FabricLoader.getInstance().getModContainer(ns)
                .map(c -> c.getMetadata().getName()).orElse(ns);
    }

    public static String structId(Holder<Structure> s) {
        return s.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    public static String biomeName(Holder<Biome> b) {
        ResourceLocation id = b.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id != null) {
            String key = "biome." + id.getNamespace() + "." + id.getPath();
            if (I18n.exists(key)) {
                return I18n.get(key);
            }
        }
        return SeedMap.name(b);
    }
}