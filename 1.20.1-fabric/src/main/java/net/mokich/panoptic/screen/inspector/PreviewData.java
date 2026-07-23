package net.mokich.panoptic.screen.inspector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PreviewData {
    private PreviewData() {
    }

    public static List<String> lines(Path file, String dir, String name, String ext) {
        if ("NBT".equals(ext)) {
            return nbt(file);
        }
        if ("OGG".equals(ext)) {
            return ogg(file);
        }
        return text(file, dir, name);
    }

    private static List<String> text(Path file, String dir, String name) {
        byte[] data = readBytes(file, dir, name, 16384);
        if (data == null || data.length == 0) {
            return List.of(I18n.get("panoptic.gui.prev.unavailable"));
        }
        List<String> out = new ArrayList<>();
        for (String ln : new String(data, StandardCharsets.UTF_8).split("\n", -1)) {
            out.add(ln.replace("\r", "").replace("\t", "  "));
            if (out.size() >= 2500) {
                break;
            }
        }
        return out;
    }

    public static byte[] readBytes(Path file, String dir, String name, int limit) {
        try {
            if (file != null) {
                try (InputStream is = Files.newInputStream(file)) {
                    return is.readNBytes(limit);
                }
            }
            ResourceLocation rl = assetRL(dir, name);
            if (rl != null) {
                var res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isPresent()) {
                    try (InputStream is = res.get().open()) {
                        return is.readNBytes(limit);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ResourceLocation assetRL(String dir, String name) {
        String p = dir + name;
        if (!p.startsWith("assets/")) {
            return null;
        }
        String rest = p.substring(7);
        int slash = rest.indexOf('/');
        return slash < 0 ? null : ResourceLocation.tryParse(rest.substring(0, slash) + ":" + rest.substring(slash + 1));
    }

    public static List<String> nbt(Path file) {
        if (file == null) {
            return List.of(I18n.get("panoptic.gui.prev.unavailable"));
        }
        try (InputStream is = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(is);
            List<String> out = new ArrayList<>();
            if (tag.contains("size", Tag.TAG_LIST)) {
                ListTag sz = tag.getList("size", Tag.TAG_INT);
                out.add("§b" + sz.getInt(0) + " × " + sz.getInt(1) + " × " + sz.getInt(2));
                out.add(I18n.get("panoptic.gui.prev.blocks", tag.getList("blocks", Tag.TAG_COMPOUND).size()));
                out.add(I18n.get("panoptic.gui.prev.states",
                        tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND).size() : 0));
                int entities = tag.contains("entities", Tag.TAG_LIST) ? tag.getList("entities", Tag.TAG_COMPOUND).size() : 0;
                if (entities > 0) {
                    out.add(I18n.get("panoptic.gui.prev.entities", entities));
                }
            } else {
                out.add(I18n.get("panoptic.gui.prev.nbt"));
                String keys = String.join(", ", tag.getAllKeys());
                out.add("§8" + (keys.length() > 200 ? keys.substring(0, 200) + "…" : keys));
            }
            return out;
        } catch (Throwable t) {
            return List.of(I18n.get("panoptic.gui.prev.unavailable"));
        }
    }

    public static List<String> pretty(String v) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int indent = 0;
        boolean inStr = false;
        char strCh = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (inStr) {
                line.append(c);
                if (c == strCh) {
                    inStr = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true;
                strCh = c;
                line.append(c);
            } else if (c == '{' || c == '[') {
                line.append(c);
                out.add(line.toString());
                indent++;
                line = new StringBuilder("  ".repeat(indent));
            } else if (c == '}' || c == ']') {
                if (!line.toString().isBlank()) {
                    out.add(line.toString());
                }
                indent = Math.max(0, indent - 1);
                line = new StringBuilder("  ".repeat(indent)).append(c);
            } else if (c == ',') {
                line.append(c);
                out.add(line.toString());
                line = new StringBuilder("  ".repeat(indent));
            } else {
                line.append(c);
            }
            if (out.size() >= 40) {
                out.add("§8…");
                return out;
            }
            if (line.length() > 120) {
                out.add(line.toString());
                line = new StringBuilder("  ".repeat(indent));
            }
        }
        if (!line.toString().isBlank()) {
            out.add(line.toString());
        }
        if (out.isEmpty()) {
            out.add(v);
        }
        return out;
    }

    private static List<String> ogg(Path file) {
        List<String> out = new ArrayList<>();
        out.add("§d♪ OGG");
        try {
            if (file != null) {
                out.add(I18n.get("panoptic.gui.prev.size", Files.size(file) / 1024));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
