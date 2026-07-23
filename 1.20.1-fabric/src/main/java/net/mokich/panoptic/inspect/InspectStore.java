package net.mokich.panoptic.inspect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class InspectStore {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final List<InspectEntry> ENTRIES = new ArrayList<>();
    private static boolean loaded;

    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gmt-inspections-save");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean saveQueued = new AtomicBoolean();
    private static volatile List<InspectEntry> saveSnapshot;

    private InspectStore() {
    }

    private static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic");
    }

    private static Path file() {
        return dir().resolve("inspections.json");
    }

    public static List<InspectEntry> entries() {
        ensureLoaded();
        return ENTRIES;
    }

    public static void add(InspectEntry entry) {
        ensureLoaded();
        ENTRIES.add(0, entry);
        save();
    }

    public static int addAll(List<InspectEntry> incoming) {
        ensureLoaded();
        Set<String> seen = new HashSet<>();
        for (InspectEntry e : ENTRIES) {
            seen.add(e.type + " " + e.id);
        }
        List<InspectEntry> toAdd = new ArrayList<>();
        for (InspectEntry e : incoming) {
            if (e != null && seen.add(e.type + " " + e.id)) {
                toAdd.add(e);
            }
        }
        if (!toAdd.isEmpty()) {
            ENTRIES.addAll(0, toAdd);
            save();
        }
        return toAdd.size();
    }

    public static boolean containsEqual(InspectEntry entry) {
        ensureLoaded();
        for (InspectEntry e : ENTRIES) {
            if (e.sameData(entry)) {
                return true;
            }
        }
        return false;
    }

    public static boolean has(InspectType type, String id) {
        ensureLoaded();
        for (InspectEntry e : ENTRIES) {
            if (e.typeEnum() == type && id.equals(e.id)) {
                return true;
            }
        }
        return false;
    }

    public static void remove(InspectEntry entry) {
        ensureLoaded();
        if (ENTRIES.remove(entry)) {
            save();
        }
    }

    public static void removeAll(Collection<InspectEntry> entries) {
        ensureLoaded();
        if (ENTRIES.removeAll(entries)) {
            save();
        }
    }

    public static void clear() {
        ensureLoaded();
        ENTRIES.clear();
        save();
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
        try (Reader reader = new InputStreamReader(openMaybeGzip(file), StandardCharsets.UTF_8)) {
            List<InspectEntry> list = GSON.fromJson(reader, new TypeToken<List<InspectEntry>>() {}.getType());
            if (list != null) {
                ENTRIES.addAll(list);
            }
        } catch (Exception ignored) {
        }
    }

    private static InputStream openMaybeGzip(Path file) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(file));
        raw.mark(2);
        int b0 = raw.read();
        int b1 = raw.read();
        raw.reset();
        if (b0 == 0x1F && b1 == 0x8B) {
            return new GZIPInputStream(raw);
        }
        return raw;
    }

    public static void save() {
        saveSnapshot = new ArrayList<>(ENTRIES);
        if (saveQueued.compareAndSet(false, true)) {
            SAVER.execute(InspectStore::writeSnapshot);
        }
    }

    private static void writeSnapshot() {
        saveQueued.set(false);
        List<InspectEntry> snapshot = saveSnapshot;
        if (snapshot == null) {
            return;
        }
        try {
            Files.createDirectories(dir());
            Path tmp = file().resolveSibling("inspections.json.tmp");
            try (OutputStream os = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)));
                 Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
}