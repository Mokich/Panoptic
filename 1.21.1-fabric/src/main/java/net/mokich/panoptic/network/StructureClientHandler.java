package net.mokich.panoptic.network;

import net.mokich.panoptic.inspect.InspectEntry;
import net.mokich.panoptic.inspect.InspectStore;
import net.mokich.panoptic.inspect.InspectType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

public final class StructureClientHandler {
    private StructureClientHandler() {}

    public static void handleList(AllStructuresResultPacket msg) {
        List<InspectEntry> entries = new ArrayList<>();
        for (AllStructuresResultPacket.Info s : msg.structures) {
            ResourceLocation id = ResourceLocation.tryParse(s.id);
            String namespace = id == null ? "minecraft" : id.getNamespace();
            InspectEntry e = new InspectEntry(InspectType.STRUCTURE, s.id, s.id, null);
            e.add("panoptic.field.id", s.id);
            e.add("panoptic.field.mod", modName(namespace));
            e.add("panoptic.field.tags", s.tags.isEmpty() ? I18n.get("panoptic.value.none") : String.join(", ", s.tags));
            entries.add(e);
        }
        InspectStore.addAll(entries);
    }

    public static void handle(StructureResultPacket msg) {
        for (StructureResultPacket.StructInfo s : msg.structures) {
            if (InspectStore.has(InspectType.STRUCTURE, s.id)) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(s.id);
            String namespace = id == null ? "minecraft" : id.getNamespace();

            InspectEntry e = new InspectEntry(InspectType.STRUCTURE, s.id, s.id, null);
            e.add("panoptic.field.id", s.id);
            e.add("panoptic.field.mod", modName(namespace));
            e.add("panoptic.field.bounds_min", s.minX + ", " + s.minY + ", " + s.minZ);
            e.add("panoptic.field.bounds_max", s.maxX + ", " + s.maxY + ", " + s.maxZ);
            e.add("panoptic.field.size", (s.maxX - s.minX + 1) + " × " + (s.maxY - s.minY + 1) + " × " + (s.maxZ - s.minZ + 1));
            e.add("panoptic.field.pieces", String.valueOf(s.pieces));
            e.add("panoptic.field.tags", s.tags.isEmpty()
                    ? I18n.get("panoptic.value.none")
                    : String.join(", ", s.tags));
            InspectStore.add(e);
        }
    }

    private static String modName(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(c -> c.getMetadata().getName())
                .orElse(namespace);
    }
}