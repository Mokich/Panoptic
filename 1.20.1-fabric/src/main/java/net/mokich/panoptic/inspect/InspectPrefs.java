package net.mokich.panoptic.inspect;

import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class InspectPrefs {
    private InspectPrefs() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic").resolve("last_tab.txt");
    }

    private static Path selectedFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic").resolve("last_selected.txt");
    }

    private static Path modFilterFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("panoptic").resolve("mod_filter.txt");
    }

    public static Set<String> modFilter() {
        Set<String> result = new LinkedHashSet<>();
        try {
            Path f = modFilterFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String ns = line.trim();
                    if (!ns.isEmpty()) {
                        result.add(ns);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static void setModFilter(Set<String> namespaces) {
        try {
            Files.createDirectories(modFilterFile().getParent());
            Files.writeString(modFilterFile(), String.join("\n", namespaces), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static long lastSelected() {
        try {
            Path f = selectedFile();
            if (!Files.exists(f)) {
                return -1L;
            }
            String value = Files.readString(f, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? -1L : Long.parseLong(value);
        } catch (Exception e) {
            return -1L;
        }
    }

    public static void setLastSelected(long id) {
        try {
            Files.createDirectories(selectedFile().getParent());
            Files.writeString(selectedFile(), Long.toString(id), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static InspectType lastTab() {
        try {
            Path file = file();
            if (!Files.exists(file)) {
                return null;
            }
            String value = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (value.isEmpty() || value.equals("ALL")) {
                return null;
            }
            return InspectType.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setLastTab(InspectType type) {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), type == null ? "ALL" : type.name(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}