package net.mokich.panoptic.inspect;

public enum InspectType {
    BLOCK("panoptic.type.block", 0x7FD6FF),
    ITEM("panoptic.type.item", 0xB6FF9C),
    ENTITY("panoptic.type.entity", 0xFFC56B),
    STRUCTURE("panoptic.type.structure", 0xE0A0FF),
    BIOME("panoptic.type.biome", 0x9CFFD0),
    FLUID("panoptic.type.fluid", 0x9CC4FF);

    private final String key;
    private final int color;

    InspectType(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public String labelKey() {
        return key;
    }
    public int color() {
        return color;
    }
}