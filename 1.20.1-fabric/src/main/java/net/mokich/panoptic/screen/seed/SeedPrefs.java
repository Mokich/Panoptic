package net.mokich.panoptic.screen.seed;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SeedPrefs {
    public static final LinkedHashSet<ResourceLocation> FILTERS = new LinkedHashSet<>();
    public static final LinkedHashSet<ResourceLocation> HIDDEN = new LinkedHashSet<>();
    public static final LinkedHashSet<ResourceLocation> SHOWN = new LinkedHashSet<>();
    public static final LinkedHashMap<String, Boolean> HISTORY = new LinkedHashMap<>();

    private static volatile Set<ResourceLocation> HIDDEN_EFF = Set.of();
    private static boolean filtersLoaded;
    private static boolean hiddenLoaded;
    private static boolean shownLoaded;
    private static boolean historyLoaded;

    private SeedPrefs() {
    }

    private static Path path(String file) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic").resolve(file);
    }

    public static void loadAll() {
        loadFilters();
        loadHidden();
        loadShown();
        loadHistory();
    }

    public static boolean structHidden(ResourceLocation id) {
        return HIDDEN_EFF.contains(id);
    }

    public static boolean updateEff() {
        HashSet<ResourceLocation> eff = new HashSet<>(HIDDEN);
        eff.removeAll(FILTERS);
        if (eff.equals(HIDDEN_EFF)) {
            return false;
        }
        HIDDEN_EFF = eff;
        return true;
    }

    private static void loadSet(String file, LinkedHashSet<ResourceLocation> into) {
        try {
            Path p = path(file);
            if (Files.isRegularFile(p)) {
                for (String l : Files.readAllLines(p)) {
                    ResourceLocation rl = ResourceLocation.tryParse(l.trim());
                    if (rl != null) {
                        into.add(rl);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void saveSet(String file, LinkedHashSet<ResourceLocation> from) {
        try {
            Path p = path(file);
            Files.createDirectories(p.getParent());
            StringBuilder sb = new StringBuilder();
            for (ResourceLocation rl : from) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(rl);
            }
            Files.writeString(p, sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void loadFilters() {
        if (filtersLoaded) {
            return;
        }
        filtersLoaded = true;
        loadSet("seedmap_filter.txt", FILTERS);
    }

    public static void saveFilters() {
        saveSet("seedmap_filter.txt", FILTERS);
    }

    private static void loadHidden() {
        if (hiddenLoaded) {
            return;
        }
        hiddenLoaded = true;
        Path p = path("seedmap_hidden.txt");
        if (Files.isRegularFile(p)) {
            loadSet("seedmap_hidden.txt", HIDDEN);
            return;
        }
        HIDDEN.add(new ResourceLocation("minecraft:mineshaft"));
        HIDDEN.add(new ResourceLocation("minecraft:mineshaft_mesa"));
        saveHidden();
    }

    public static void saveHidden() {
        saveSet("seedmap_hidden.txt", HIDDEN);
    }

    private static void loadShown() {
        if (shownLoaded) {
            return;
        }
        shownLoaded = true;
        loadSet("seedmap_shown.txt", SHOWN);
    }

    public static void saveShown() {
        saveSet("seedmap_shown.txt", SHOWN);
    }

    private static void loadHistory() {
        if (historyLoaded) {
            return;
        }
        historyLoaded = true;
        try {
            Path p = path("seedmap_history.txt");
            if (Files.isRegularFile(p)) {
                for (String l : Files.readAllLines(p)) {
                    String s = l.trim();
                    if (s.isEmpty()) {
                        continue;
                    }
                    if (s.startsWith("*")) {
                        HISTORY.put(s.substring(1).trim(), true);
                    } else {
                        HISTORY.put(s, false);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void saveHistory() {
        try {
            Path p = path("seedmap_history.txt");
            Files.createDirectories(p.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Boolean> e : HISTORY.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                if (e.getValue()) {
                    sb.append('*');
                }
                sb.append(e.getKey());
            }
            Files.writeString(p, sb.toString());
        } catch (Throwable ignored) {
        }
    }

    public static void histAdd(String s) {
        Boolean fav = HISTORY.remove(s);
        LinkedHashMap<String, Boolean> rest = new LinkedHashMap<>(HISTORY);
        HISTORY.clear();
        HISTORY.put(s, fav != null && fav);
        HISTORY.putAll(rest);
        int plain = 0;
        Iterator<Map.Entry<String, Boolean>> it = HISTORY.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue() && ++plain > 30) {
                it.remove();
            }
        }
        saveHistory();
    }
}