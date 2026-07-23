package net.mokich.panoptic.inspect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InspectEntry {
    public String type;
    public String title;
    public String id;
    public String translationKey;
    public String iconId;
    public long capturedAt;
    public List<InspectField> fields = new ArrayList<>();

    public transient boolean expanded;
    public transient boolean langsExpanded;
    public transient List<ResourceFiles.FileRec> files;
    public transient boolean previewTried;
    public transient Entity previewEntity;
    private transient ItemStack icon;

    public InspectEntry() {}

    public InspectEntry(InspectType type, String title, String id, String translationKey) {
        this.type = type.name();
        this.title = title;
        this.id = id;
        this.translationKey = translationKey;
        this.capturedAt = System.currentTimeMillis();
    }

    public InspectEntry add(String label, String value) {
        if (value != null && !value.isEmpty()) {
            fields.add(InspectField.of(label, value));
        }
        return this;
    }

    public InspectEntry withIcon(String iconId) {
        this.iconId = iconId;
        return this;
    }

    public ItemStack icon() {
        if (icon != null) {
            return icon;
        }
        if (iconId == null || iconId.isEmpty()) {
            icon = ItemStack.EMPTY;
            return icon;
        }
        ResourceLocation rl = ResourceLocation.tryParse(iconId);
        Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        icon = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        return icon;
    }

    public boolean sameData(InspectEntry other) {
        if (other == null
                || !Objects.equals(type, other.type)
                || !Objects.equals(id, other.id)
                || !Objects.equals(translationKey, other.translationKey)
                || fields.size() != other.fields.size()) {
            return false;
        }
        for (int i = 0; i < fields.size(); i++) {
            InspectField a = fields.get(i);
            InspectField b = other.fields.get(i);
            if (!Objects.equals(a.label, b.label) || !Objects.equals(a.value, b.value)) {
                return false;
            }
        }
        return true;
    }

    public InspectType typeEnum() {
        try {
            return InspectType.valueOf(type);
        } catch (Exception e) {
            return InspectType.ITEM;
        }
    }
}