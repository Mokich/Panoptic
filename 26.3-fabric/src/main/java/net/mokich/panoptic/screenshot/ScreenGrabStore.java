package net.mokich.panoptic.screenshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.mokich.panoptic.api.util.BgTex;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ScreenGrabStore {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final List<ScreenGrab> GRABS = new ArrayList<>();
    private static boolean loaded;

    private ScreenGrabStore() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic").resolve("screengrabs.json");
    }

    public static Path grabsDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic").resolve("grabs");
    }

    public static List<ScreenGrab> grabs() {
        ensureLoaded();
        return GRABS;
    }

    public static void add(ScreenGrab grab) {
        ensureLoaded();
        GRABS.add(0, grab);
        save();
    }

    public static void remove(ScreenGrab grab) {
        ensureLoaded();
        if (GRABS.remove(grab)) {
            deleteBg(grab);
            save();
        }
    }

    public static void clear() {
        ensureLoaded();
        for (ScreenGrab grab : GRABS) {
            deleteBg(grab);
        }
        GRABS.clear();
        save();
    }

    private static void deleteBg(ScreenGrab grab) {
        BgTex.drop(grab);
        if (grab.bg != null) {
            try {
                Files.deleteIfExists(grabsDir().resolve(grab.bg));
            } catch (Exception ignored) {
            }
        }
    }

    public static ScreenGrab duplicate(ScreenGrab src) {
        ensureLoaded();
        ScreenGrab copy = GSON.fromJson(GSON.toJson(src), ScreenGrab.class);
        copy.id = System.currentTimeMillis();
        copy.favorite = false;
        if (src.bg != null) {
            try {
                Files.copy(grabsDir().resolve(src.bg), grabsDir().resolve(copy.id + ".png"),
                        StandardCopyOption.REPLACE_EXISTING);
                copy.bg = copy.id + ".png";
            } catch (Exception e) {
                copy.bg = null;
            }
        }
        int idx = GRABS.indexOf(src);
        GRABS.add(idx < 0 ? 0 : idx + 1, copy);
        save();
        return copy;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<ScreenGrab> list = GSON.fromJson(reader, new TypeToken<List<ScreenGrab>>() {
            }.getType());
            if (list != null) {
                GRABS.addAll(list);
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("screengrabs.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(GRABS, writer);
            }
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
}