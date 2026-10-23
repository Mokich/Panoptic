package net.mokich.panoptic.inspect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class LanguageNames {
    private static volatile Map<String, Map<String, String>> cache;
    private LanguageNames() {}

    public static void invalidate() {
        cache = null;
    }

    public static void preloadAsync() {
        if (cache != null) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                data();
            } catch (Throwable ignored) {
            }
        }, "gmt-lang-preload");
        t.setDaemon(true);
        t.start();
    }

    private static Map<String, Map<String, String>> data() {
        Map<String, Map<String, String>> local = cache;
        if (local != null) {
            return local;
        }
        synchronized (LanguageNames.class) {
            if (cache != null) {
                return cache;
            }
            cache = load();
            return cache;
        }
    }

    private static Map<String, Map<String, String>> load() {
        Map<String, Map<String, String>> result = new TreeMap<>();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> found =
                rm.listResources("lang", rl -> rl.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            int slash = path.lastIndexOf('/');
            String lang = path.substring(slash + 1, path.length() - ".json".length());

            try (InputStream in = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject obj = GsonHelper.parse(reader);
                Map<String, String> map = result.computeIfAbsent(lang, k -> new HashMap<>());
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        map.putIfAbsent(e.getKey(), e.getValue().getAsString());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static boolean ready() {
        return cache != null;
    }

    public static Map<String, String> allNames(String key) {
        Map<String, String> result = new LinkedHashMap<>();
        if (key == null) {
            return result;
        }
        Map<String, Map<String, String>> local = cache;
        if (local == null) {
            preloadAsync();
            return result;
        }
        for (Map.Entry<String, Map<String, String>> e : local.entrySet()) {
            String value = e.getValue().get(key);
            if (value != null) {
                result.put(e.getKey(), value);
            }
        }
        return result;
    }
}