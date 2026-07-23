package net.mokich.panoptic.inspect;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourceFiles {
    public record FileRec(String source, String path, Path file) {}
    private static final int MAX_RESULTS = 600;
    private static List<FileRec> index;
    private static String signature;

    private ResourceFiles() {}

    public static synchronized List<FileRec> filesFor(String idStr) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) {
            return List.of();
        }
        try {
            ensureIndex();
        } catch (Throwable t) {
            return List.of();
        }
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        String ns = id.getNamespace();
        String fullId = ns + ":" + path;

        String prefix = name + "_";
        LinkedHashSet<FileRec> out = new LinkedHashSet<>();
        Map<String, FileRec> byPath = new HashMap<>();
        List<FileRec> contentCandidates = new ArrayList<>();
        for (FileRec rec : index) {
            byPath.putIfAbsent(rec.path(), rec);
            String[] seg = rec.path().split("/");
            if (seg.length < 3) {
                continue;
            }
            String last = seg[seg.length - 1];
            int dot = last.lastIndexOf('.');
            String base = dot > 0 ? last.substring(0, dot) : last;
            if (base.endsWith(".png")) {
                base = base.substring(0, base.length() - 4);
            }
            boolean match = base.equals(name);
            if (!match) {
                for (int i = 2; i < seg.length - 1 && !match; i++) {
                    match = seg[i].equals(name);
                }
            }
            if (!match && seg[1].equals(ns)) {
                match = base.startsWith(prefix);
            }
            if (match) {
                out.add(rec);
                continue;
            }
            if (isText(rec.path()) && rec.file() != null && base.contains(name)) {
                contentCandidates.add(rec);
            }
        }

        for (FileRec rec : contentCandidates) {
            if (out.size() >= MAX_RESULTS) {
                break;
            }
            if (out.contains(rec)) {
                continue;
            }
            String body = readSafe(rec.file());
            if (body != null && mentions(body, fullId, ns, path)) {
                out.add(rec);
            }
        }

        ArrayDeque<FileRec> queue = new ArrayDeque<>(out);
        int hops = 0;
        while (!queue.isEmpty() && out.size() < MAX_RESULTS && hops++ < 600) {
            FileRec rec = queue.poll();
            if (rec.file() == null || !rec.path().endsWith(".json")) {
                continue;
            }
            String body = readSafe(rec.file());
            if (body == null) {
                continue;
            }
            Matcher m = REF_PATTERN.matcher(body);
            while (m.find() && out.size() < MAX_RESULTS) {
                String refNs = m.group(1) == null ? "minecraft" : m.group(1);
                String refPath = m.group(2);
                for (String candidate : new String[]{
                        "assets/" + refNs + "/models/" + refPath + ".json",
                        "assets/" + refNs + "/textures/" + refPath + ".png",
                        "assets/" + refNs + "/textures/" + refPath + ".png.mcmeta",
                        "assets/" + refNs + "/sounds/" + refPath + ".ogg"
                }) {
                    FileRec linked = byPath.get(candidate);
                    if (linked != null && out.add(linked)) {
                        if (linked.path().endsWith(".json")) {
                            queue.add(linked);
                        }
                    }
                }
            }
        }

        return new ArrayList<>(out);
    }

    private static final Pattern REF_PATTERN =
            Pattern.compile("\"(?:([a-z0-9_.-]+):)?([a-z0-9_/.-]+)\"");

    private static boolean isText(String p) {
        return p.endsWith(".json") || p.endsWith(".mcmeta") || p.endsWith(".txt") || p.endsWith(".toml")
                || p.endsWith(".properties") || p.endsWith(".cfg") || p.endsWith(".snbt")
                || p.endsWith(".fsh") || p.endsWith(".vsh") || p.endsWith(".glsl");
    }

    private static String readSafe(Path file) {
        try {
            if (Files.size(file) > 262144) {
                return null;
            }
            return Files.readString(file);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean mentions(String body, String fullId, String ns, String path) {
        int idx = body.indexOf(fullId);
        while (idx >= 0) {
            int end = idx + fullId.length();
            char before = idx > 0 ? body.charAt(idx - 1) : ' ';
            char after = end < body.length() ? body.charAt(end) : ' ';
            if (!isIdChar(before) && !isIdChar(after)) {
                return true;
            }
            idx = body.indexOf(fullId, end);
        }
        if (ns.equals("minecraft")) {
            String quoted = "\"" + path + "\"";
            if (body.contains(quoted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdChar(char c) {
        return c == '_' || c == '/' || c == '.' || c == '-' || Character.isLetterOrDigit(c);
    }

    private static String currentSignature() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        return String.join("|", mc.getResourcePackRepository().getSelectedIds())
                + "@" + (server == null ? 0 : System.identityHashCode(server));
    }

    private static void ensureIndex() {
        String sig = currentSignature();
        if (index != null && sig.equals(signature)) {
            return;
        }
        List<FileRec> out = new ArrayList<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            String src = modName(info);
            walkRoot(findResource(info, "assets"), "assets", src, out);
            walkRoot(findResource(info, "data"), "data", src, out);
        }
        Minecraft mc = Minecraft.getInstance();
        Path rpDir = mc.getResourcePackDirectory();
        for (String id : mc.getResourcePackRepository().getSelectedIds()) {
            if (id.startsWith("file/")) {
                addPack(rpDir.resolve(id.substring(5)), out);
            }
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            File[] packs = server.getWorldPath(LevelResource.DATAPACK_DIR).toFile().listFiles();
            if (packs != null) {
                for (File f : packs) {
                    addPack(f.toPath(), out);
                }
            }
        }
        index = out;
        signature = sig;
    }

    private static Path findResource(IModFileInfo info, String dir) {
        try {
            return info.getFile().findResource(dir);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String modName(IModFileInfo info) {
        try {
            if (!info.getMods().isEmpty()) {
                return info.getMods().get(0).getDisplayName();
            }
            return info.getFile().getFileName();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static void addPack(Path path, List<FileRec> out) {
        String src = String.valueOf(path.getFileName());
        try {
            if (Files.isDirectory(path)) {
                walkRoot(path.resolve("assets"), "assets", src, out);
                walkRoot(path.resolve("data"), "data", src, out);
            } else if (path.toString().toLowerCase().endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    Enumeration<? extends ZipEntry> en = zip.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry ze = en.nextElement();
                        String n = ze.getName();
                        if (!ze.isDirectory() && (n.startsWith("assets/") || n.startsWith("data/"))) {
                            out.add(new FileRec(src, n, null));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void walkRoot(Path root, String prefix, String src, List<FileRec> out) {
        if (root == null) {
            return;
        }
        try {
            if (!Files.isDirectory(root)) {
                return;
            }
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(Files::isRegularFile).forEach(p ->
                        out.add(new FileRec(src, prefix + "/" + root.relativize(p).toString().replace('\\', '/'), p)));
            }
        } catch (Throwable ignored) {
        }
    }
}