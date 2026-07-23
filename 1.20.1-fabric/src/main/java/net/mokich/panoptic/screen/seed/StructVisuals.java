package net.mokich.panoptic.screen.seed;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;

public final class StructVisuals {
    private static final Map<ResourceLocation, ItemStack> ICONS = new HashMap<>();
    private static final ItemStack FALLBACK = new ItemStack(Items.FILLED_MAP);

    private StructVisuals() {}

    public static ItemStack icon(Holder<Structure> s) {
        ResourceLocation id = s.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return FALLBACK;
        }
        return ICONS.computeIfAbsent(id, k -> new ItemStack(iconItem(k.getPath())));
    }

    private static Item iconItem(String p) {
        if (p.contains("village")) return Items.EMERALD;
        if (p.contains("pillager") || p.contains("outpost")) return Items.CROSSBOW;
        if (p.contains("monument")) return Items.PRISMARINE_SHARD;
        if (p.contains("mansion")) return Items.TOTEM_OF_UNDYING;
        if (p.contains("stronghold")) return Items.ENDER_EYE;
        if (p.contains("fortress")) return Items.BLAZE_ROD;
        if (p.contains("bastion")) return Items.GOLD_INGOT;
        if (p.contains("end_city")) return Items.SHULKER_SHELL;
        if (p.contains("ruined_portal")) return Items.CRYING_OBSIDIAN;
        if (p.contains("shipwreck")) return Items.OAK_BOAT;
        if (p.contains("ocean_ruin")) return Items.TRIDENT;
        if (p.contains("buried_treasure")) return Items.CHEST;
        if (p.contains("mineshaft")) return Items.IRON_PICKAXE;
        if (p.contains("ancient_city")) return Items.SOUL_LANTERN;
        if (p.contains("trail_ruins") || p.contains("trail")) return Items.BRUSH;
        if (p.contains("desert_pyramid")) return Items.SANDSTONE;
        if (p.contains("jungle")) return Items.MOSSY_COBBLESTONE;
        if (p.contains("igloo")) return Items.SNOW_BLOCK;
        if (p.contains("swamp_hut") || p.contains("witch") || p.contains("hut")) return Items.CAULDRON;
        if (p.contains("nether_fossil") || p.contains("fossil")) return Items.BONE_BLOCK;
        if (p.contains("temple") || p.contains("pyramid")) return Items.SANDSTONE;
        if (p.contains("dungeon")) return Items.SPAWNER;
        if (p.contains("tower") || p.contains("spire")) return Items.SPYGLASS;
        if (p.contains("castle") || p.contains("keep") || p.contains("citadel")) return Items.STONE_BRICKS;
        if (p.contains("camp")) return Items.CAMPFIRE;
        if (p.contains("house") || p.contains("cabin") || p.contains("cottage") || p.contains("home")) return Items.OAK_DOOR;
        if (p.contains("grave") || p.contains("crypt") || p.contains("tomb") || p.contains("cemetery")) return Items.BONE;
        if (p.contains("lighthouse")) return Items.LANTERN;
        if (p.contains("bridge")) return Items.STONE_BRICK_WALL;
        if (p.contains("farm")) return Items.WHEAT;
        if (p.contains("barn") || p.contains("stable")) return Items.HAY_BLOCK;
        if (p.contains("market") || p.contains("bazaar")) return Items.GOLD_NUGGET;
        if (p.contains("wizard") || p.contains("mage") || p.contains("alchem")) return Items.BREWING_STAND;
        if (p.contains("ship") || p.contains("boat") || p.contains("galleon")) return Items.OAK_BOAT;
        if (p.contains("well")) return Items.WATER_BUCKET;
        if (p.contains("ruin")) return Items.CRACKED_STONE_BRICKS;
        if (p.contains("city") || p.contains("town")) return Items.BELL;
        if (p.contains("mine")) return Items.IRON_PICKAXE;
        if (p.contains("cave")) return Items.GLOW_BERRIES;
        if (p.contains("island")) return Items.TURTLE_EGG;
        return Items.FILLED_MAP;
    }

    public static int color(Holder<Structure> s) {
        ResourceLocation id = s.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return 0xFFCCCCCC;
        }
        String p = id.getPath();
        if (p.contains("village")) return 0xFFC8A85A;
        if (p.contains("pillager") || p.contains("outpost")) return 0xFFB85A4A;
        if (p.contains("monument")) return 0xFF4AA0C8;
        if (p.contains("mansion")) return 0xFF8A6A4A;
        if (p.contains("stronghold")) return 0xFF8A6AD8;
        if (p.contains("fortress")) return 0xFF9A2A2A;
        if (p.contains("bastion")) return 0xFF6A5A7A;
        if (p.contains("end_city")) return 0xFFCFCF80;
        if (p.contains("ruined_portal")) return 0xFFB05AD0;
        if (p.contains("shipwreck")) return 0xFF8A7A5A;
        if (p.contains("ocean_ruin")) return 0xFF6AA0A0;
        if (p.contains("buried_treasure")) return 0xFFE6C84A;
        if (p.contains("mineshaft")) return 0xFF8A7060;
        if (p.contains("ancient_city")) return 0xFF3A7A8A;
        if (p.contains("trail")) return 0xFFB89060;
        if (p.contains("trial")) return 0xFF5AB0B0;
        if (p.contains("temple") || p.contains("pyramid") || p.contains("jungle")
                || p.contains("igloo") || p.contains("witch") || p.contains("hut")) return 0xFFD8A838;
        int h = id.hashCode();
        return 0xFF000000 | (96 + (h & 0x5F)) << 16 | (96 + ((h >> 7) & 0x5F)) << 8 | (96 + ((h >> 14) & 0x5F));
    }
}