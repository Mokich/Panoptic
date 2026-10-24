package net.mokich.panoptic.inspect;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.fabricmc.loader.api.FabricLoader;
import com.google.common.collect.Multimap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

public final class Inspectors {
    private Inspectors() {}

    public static InspectEntry block(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String descId = "block." + id.getNamespace() + "." + id.getPath();

        InspectEntry e = new InspectEntry(InspectType.BLOCK, displayName(descId, id), id.toString(), descId);
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.translation_key", descId);
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);

        Identifier itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (itemId != null && !itemId.getPath().equals("air")) {
            e.add("panoptic.field.item", itemId.toString());
            e.withIcon(itemId.toString());
        }

        String props = state.getProperties().stream()
                .map(p -> p.getName() + "=" + propValue(p, state))
                .collect(Collectors.joining(", "));
        e.add("panoptic.field.blockstate", props.isEmpty() ? I18n.get("panoptic.value.no_properties") : props);

        e.add("panoptic.field.light", String.valueOf(state.getLightEmission()));
        e.add("panoptic.field.hardness", String.valueOf(block.defaultDestroyTime()));
        e.add("panoptic.field.blast_resistance", String.valueOf(block.getExplosionResistance()));
        e.add("panoptic.field.tags", tagsString(state.getBlock().builtInRegistryHolder().tags()));
        blockDeep(e, block, state);

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            BlockEntityType<?> type = be.getType();
            Identifier beId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            e.add("panoptic.field.block_entity", beId == null ? "?" : beId.toString());
            e.add("panoptic.field.nbt", be.saveWithFullMetadata(level.registryAccess()).toString());
        }

        level.getBiome(pos).unwrapKey().ifPresent(k -> e.add("panoptic.field.biome", k.identifier().toString()));
        e.add("panoptic.field.position", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        e.add("panoptic.field.dimension", level.dimension().identifier().toString());
        return e;
    }

    public static InspectEntry item(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String descId = stack.getItem().getDescriptionId();

        InspectEntry e = new InspectEntry(InspectType.ITEM, displayName(descId, id), id.toString(), descId);
        e.withIcon(id.toString());
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.translation_key", descId);
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);

        e.add("panoptic.field.count", stack.getCount() + " / " + stack.getMaxStackSize());
        if (stack.isDamageableItem()) {
            e.add("panoptic.field.durability", (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage());
        }
        e.add("panoptic.field.rarity", stack.getRarity().name());

        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            e.add("panoptic.field.food", food.nutrition() + " / " + food.saturation());
        }

        itemDeep(e, stack);

        ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (!ench.isEmpty()) {
            String s = ench.entrySet().stream()
                    .map(en -> en.getKey().unwrapKey().map(k -> k.identifier().toString()).orElse("?")
                            + " " + en.getIntValue())
                    .collect(Collectors.joining(", "));
            e.add("panoptic.field.enchantments", s);
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            e.add("panoptic.field.block", blockId.toString());
        }

        e.add("panoptic.field.tags", tagsString(BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).tags()));

        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            e.add("panoptic.field.nbt", patch.toString());
        }
        return e;
    }

    public static InspectEntry entity(Entity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String descId = entity.getType().getDescriptionId();

        InspectEntry e = new InspectEntry(InspectType.ENTITY, displayName(descId, id), id.toString(), descId);
        SpawnEggItem.byId(entity.getType()).ifPresent(h -> {
            Identifier eggId = BuiltInRegistries.ITEM.getKey(h.value());
            if (eggId != null) {
                e.withIcon(eggId.toString());
            }
        });
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.translation_key", descId);
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);

        e.add("panoptic.field.uuid", entity.getStringUUID());
        if (entity.hasCustomName()) {
            e.add("panoptic.field.custom_name", entity.getCustomName().getString());
        }
        if (entity instanceof LivingEntity living) {
            e.add("panoptic.field.health", living.getHealth() + " / " + living.getMaxHealth());
        }

        e.add("panoptic.field.tags", tagsString(entity.getType().builtInRegistryHolder().tags()));
        entityDeep(e, entity.getType());

        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.level().registryAccess());
        entity.saveWithoutId(out);
        CompoundTag nbt = out.buildResult();
        e.add("panoptic.field.nbt", nbt.toString());

        e.add("panoptic.field.position", String.format("%.2f, %.2f, %.2f", entity.getX(), entity.getY(), entity.getZ()));
        e.add("panoptic.field.dimension", entity.level().dimension().identifier().toString());
        return e;
    }

    public static InspectEntry fluid(Level level, BlockPos pos, FluidState fluidState) {
        Fluid fluid = fluidState.getType();
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        Item bucket = fluid.getBucket();
        Identifier bucketId = BuiltInRegistries.ITEM.getKey(bucket);
        String descId = bucket == Items.AIR ? null : bucket.getDescriptionId();

        InspectEntry e = new InspectEntry(InspectType.FLUID,
                descId == null ? id.toString() : displayName(descId, id), id.toString(), descId);
        if (bucketId != null && !bucketId.getPath().equals("air")) {
            e.withIcon(bucketId.toString());
        }
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        if (descId != null) {
            addLocalNames(e, descId);
        }
        e.add("panoptic.field.source", fluidState.isSource() ? I18n.get("panoptic.value.yes") : I18n.get("panoptic.value.no"));
        e.add("panoptic.field.level", fluidState.getAmount() + " / 8");
        e.add("panoptic.field.tags", tagsString(fluid.builtInRegistryHolder().tags()));
        if (bucketId != null && !bucketId.getPath().equals("air")) {
            e.add("panoptic.field.bucket", bucketId.toString());
        }
        e.add("panoptic.field.position", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        e.add("panoptic.field.dimension", level.dimension().identifier().toString());
        return e;
    }

    public static InspectEntry biome(Level level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        Identifier id = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
        if (id == null) {
            return null;
        }
        Biome biome = holder.value();
        String descId = "biome." + id.getNamespace() + "." + id.getPath();

        InspectEntry e = new InspectEntry(InspectType.BIOME, displayName(descId, id), id.toString(), descId);
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);
        e.add("panoptic.field.temperature", String.valueOf(biome.getBaseTemperature()));
        e.add("panoptic.field.precipitation", biome.hasPrecipitation() ? I18n.get("panoptic.value.yes") : I18n.get("panoptic.value.no"));
        e.add("panoptic.field.tags", tagsString(holder.tags()));
        e.add("panoptic.field.position", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        e.add("panoptic.field.dimension", level.dimension().identifier().toString());
        return e;
    }

    public static List<Supplier<InspectEntry>> scanUnits(InspectType type, RegistryAccess access) {
        List<Supplier<InspectEntry>> out = new ArrayList<>();
        switch (type) {
            case BLOCK -> {
                for (Block block : BuiltInRegistries.BLOCK) {
                    out.add(() -> blockRegistryEntry(block));
                }
            }
            case ITEM -> {
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item != Items.AIR) {
                        out.add(() -> item(new ItemStack(item)));
                    }
                }
            }
            case ENTITY -> {
                for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
                    out.add(() -> entityRegistryEntry(entityType));
                }
            }
            case FLUID -> {
                for (Fluid fluid : BuiltInRegistries.FLUID) {
                    if (fluid != Fluids.EMPTY) {
                        out.add(() -> fluidRegistryEntry(fluid));
                    }
                }
            }
            case BIOME -> {
                if (access != null) {
                    Registry<Biome> registry = access.lookupOrThrow(Registries.BIOME);
                    for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
                        ResourceKey<Biome> key = entry.getKey();
                        Biome biome = entry.getValue();
                        out.add(() -> biomeRegistryEntry(registry, key, biome));
                    }
                }
            }
            case STRUCTURE -> {
                if (access != null) {
                    access.lookup(Registries.STRUCTURE).ifPresent(registry -> {
                        for (Map.Entry<ResourceKey<Structure>, Structure> entry : registry.entrySet()) {
                            ResourceKey<Structure> key = entry.getKey();
                            out.add(() -> structureRegistryEntry(registry, key));
                        }
                    });
                }
            }
        }
        return out;
    }

    private static InspectEntry blockRegistryEntry(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String descId = "block." + id.getNamespace() + "." + id.getPath();
        BlockState state = block.defaultBlockState();

        InspectEntry e = new InspectEntry(InspectType.BLOCK, displayName(descId, id), id.toString(), descId);
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.translation_key", descId);
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);

        Identifier itemId = BuiltInRegistries.ITEM.getKey(block.asItem());
        if (itemId != null && !itemId.getPath().equals("air")) {
            e.add("panoptic.field.item", itemId.toString());
            e.withIcon(itemId.toString());
        }

        String props = state.getProperties().stream()
                .map(p -> p.getName() + "=" + propValue(p, state))
                .collect(Collectors.joining(", "));
        e.add("panoptic.field.blockstate", props.isEmpty() ? I18n.get("panoptic.value.no_properties") : props);
        e.add("panoptic.field.light", String.valueOf(state.getLightEmission()));
        e.add("panoptic.field.hardness", String.valueOf(block.defaultDestroyTime()));
        e.add("panoptic.field.blast_resistance", String.valueOf(block.getExplosionResistance()));
        e.add("panoptic.field.tags", tagsString(state.getBlock().builtInRegistryHolder().tags()));
        blockDeep(e, block, state);
        return e;
    }

    private static InspectEntry entityRegistryEntry(EntityType<?> entityType) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        String descId = entityType.getDescriptionId();

        InspectEntry e = new InspectEntry(InspectType.ENTITY, displayName(descId, id), id.toString(), descId);
        SpawnEggItem.byId(entityType).ifPresent(h -> {
            Identifier eggId = BuiltInRegistries.ITEM.getKey(h.value());
            if (eggId != null) {
                e.withIcon(eggId.toString());
            }
        });
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.translation_key", descId);
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);
        e.add("panoptic.field.tags", tagsString(entityType.builtInRegistryHolder().tags()));
        entityDeep(e, entityType);
        return e;
    }

    private static InspectEntry fluidRegistryEntry(Fluid fluid) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        Item bucket = fluid.getBucket();
        Identifier bucketId = BuiltInRegistries.ITEM.getKey(bucket);
        String descId = bucket == Items.AIR ? null : bucket.getDescriptionId();

        InspectEntry e = new InspectEntry(InspectType.FLUID,
                descId == null ? id.toString() : displayName(descId, id), id.toString(), descId);
        if (bucketId != null && !bucketId.getPath().equals("air")) {
            e.withIcon(bucketId.toString());
        }
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        if (descId != null) {
            addLocalNames(e, descId);
        }
        e.add("panoptic.field.source", fluid.defaultFluidState().isSource() ? I18n.get("panoptic.value.yes") : I18n.get("panoptic.value.no"));
        e.add("panoptic.field.tags", tagsString(fluid.builtInRegistryHolder().tags()));
        if (bucketId != null && !bucketId.getPath().equals("air")) {
            e.add("panoptic.field.bucket", bucketId.toString());
        }
        return e;
    }

    private static InspectEntry biomeRegistryEntry(Registry<Biome> registry, ResourceKey<Biome> key, Biome biome) {
        Identifier id = key.identifier();
        String descId = "biome." + id.getNamespace() + "." + id.getPath();

        InspectEntry e = new InspectEntry(InspectType.BIOME, displayName(descId, id), id.toString(), descId);
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        addLocalNames(e, descId);
        e.add("panoptic.field.temperature", String.valueOf(biome.getBaseTemperature()));
        e.add("panoptic.field.precipitation", biome.hasPrecipitation() ? I18n.get("panoptic.value.yes") : I18n.get("panoptic.value.no"));
        e.add("panoptic.field.tags", tagsString(registry.getOrThrow(key).tags()));
        return e;
    }

    private static InspectEntry structureRegistryEntry(Registry<Structure> registry, ResourceKey<Structure> key) {
        Identifier id = key.identifier();
        InspectEntry e = new InspectEntry(InspectType.STRUCTURE, id.toString(), id.toString(), null);
        e.add("panoptic.field.id", id.toString());
        e.add("panoptic.field.mod", modName(id.getNamespace()));
        e.add("panoptic.field.tags", tagsString(registry.getOrThrow(key).tags()));
        return e;
    }

    private static void itemDeep(InspectEntry e, ItemStack stack) {
        Item item = stack.getItem();
        try {
            ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.EMPTY);
            double atk = sumAdd(mods, Attributes.ATTACK_DAMAGE);
            double spd = sumAdd(mods, Attributes.ATTACK_SPEED);
            if (atk != 0) {
                e.add("panoptic.field.attack_damage", fmt(1.0 + atk));
            }
            if (spd != 0) {
                e.add("panoptic.field.attack_speed", fmt(4.0 + spd));
            }
        } catch (Throwable ignored) {
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            e.add("panoptic.field.equip_slot", equippable.slot().getName());
            try {
                ItemAttributeModifiers am = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.EMPTY);
                double armor = sumAdd(am, Attributes.ARMOR);
                double tough = sumAdd(am, Attributes.ARMOR_TOUGHNESS);
                double kb = sumAdd(am, Attributes.KNOCKBACK_RESISTANCE);
                if (armor != 0) {
                    e.add("panoptic.field.armor_points", fmt(armor));
                }
                if (tough != 0) {
                    e.add("panoptic.field.toughness", fmt(tough));
                }
                if (kb != 0) {
                    e.add("panoptic.field.kb_resistance", fmt(kb));
                }
            } catch (Throwable ignored) {
            }
        }
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            e.add("panoptic.field.mining_speed", fmt(tool.defaultMiningSpeed()));
            Integer maxDmg = stack.get(DataComponents.MAX_DAMAGE);
            if (maxDmg != null) {
                e.add("panoptic.field.tier_uses", String.valueOf(maxDmg));
            }
        }
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (enchantable != null && enchantable.value() > 0) {
            e.add("panoptic.field.enchant_value", String.valueOf(enchantable.value()));
        }
        try {
            net.minecraft.world.item.component.CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
            int burn = fuel == null ? 0 : Math.max(0, fuel.burnTime().get(null, 0));
            if (burn > 0) {
                e.add("panoptic.field.burn_time", burn + " t / " + fmt(burn / 20.0) + " s");
            }
        } catch (Throwable ignored) {
        }
        try {
            FoodProperties fp = stack.get(DataComponents.FOOD);
            if (fp != null) {
                if (fp.canAlwaysEat()) {
                    e.add("panoptic.field.always_edible", I18n.get("panoptic.value.yes"));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void blockDeep(InspectEntry e, Block block, BlockState state) {
        if (state.requiresCorrectToolForDrops()) {
            e.add("panoptic.field.requires_tool", I18n.get("panoptic.value.yes"));
        }
        String tool = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "pickaxe"
                : state.is(BlockTags.MINEABLE_WITH_AXE) ? "axe"
                : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "shovel"
                : state.is(BlockTags.MINEABLE_WITH_HOE) ? "hoe" : null;
        if (tool != null) {
            e.add("panoptic.field.tool", tool);
        }
        String tier = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "diamond"
                : state.is(BlockTags.NEEDS_IRON_TOOL) ? "iron"
                : state.is(BlockTags.NEEDS_STONE_TOOL) ? "stone" : null;
        if (tier != null) {
            e.add("panoptic.field.tool_tier", tier);
        }
        try {
            float fr = block.getFriction();
            if (Math.abs(fr - 0.6F) > 1e-4) {
                e.add("panoptic.field.friction", fmt(fr));
            }
        } catch (Throwable ignored) {
        }
        try {
            e.add("panoptic.field.push_reaction", state.getPistonPushReaction().name().toLowerCase(Locale.ROOT));
        } catch (Throwable ignored) {
        }
        if (state.isSignalSource()) {
            e.add("panoptic.field.signal_source", I18n.get("panoptic.value.yes"));
        }
        try {
            e.add("panoptic.field.sound", state.getSoundType().getBreakSound().location().toString());
        } catch (Throwable ignored) {
        }
        try {
            e.add("panoptic.field.loot_table", block.getLootTable().toString());
        } catch (Throwable ignored) {
        }
        try {
            List<Property<?>> props = new ArrayList<>(state.getProperties());
            if (!props.isEmpty()) {
                e.add("panoptic.field.properties_options",
                        props.stream().map(Inspectors::propOptions).collect(Collectors.joining("  ")));
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void entityDeep(InspectEntry e, EntityType<?> type) {
        try {
            e.add("panoptic.field.category", type.getCategory().getName());
        } catch (Throwable ignored) {
        }
        try {
            e.add("panoptic.field.dimensions", fmt(type.getWidth()) + " x " + fmt(type.getHeight()));
        } catch (Throwable ignored) {
        }
        if (type.fireImmune()) {
            e.add("panoptic.field.fire_immune", I18n.get("panoptic.value.yes"));
        }
        try {
            e.add("panoptic.field.loot_table", type.getDefaultLootTable().toString());
        } catch (Throwable ignored) {
        }
        try {
            EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
            if (DefaultAttributes.hasSupplier(living)) {
                AttributeSupplier sup = DefaultAttributes.getSupplier(living);
                addSupAttr(e, sup, Attributes.MAX_HEALTH, "panoptic.field.max_health");
                addSupAttr(e, sup, Attributes.ARMOR, "panoptic.field.armor_points");
                addSupAttr(e, sup, Attributes.ATTACK_DAMAGE, "panoptic.field.attack_damage");
                addSupAttr(e, sup, Attributes.MOVEMENT_SPEED, "panoptic.field.move_speed");
                addSupAttr(e, sup, Attributes.KNOCKBACK_RESISTANCE, "panoptic.field.kb_resistance");
                addSupAttr(e, sup, Attributes.FOLLOW_RANGE, "panoptic.field.follow_range");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addSupAttr(InspectEntry e, AttributeSupplier sup, Holder<Attribute> attr, String key) {
        if (sup.hasAttribute(attr)) {
            e.add(key, fmt(sup.getValue(attr)));
        }
    }

    private static double sumAdd(ItemAttributeModifiers mods, Holder<Attribute> attr) {
        double s = 0;
        for (ItemAttributeModifiers.Entry en : mods.modifiers()) {
            if (en.attribute().equals(attr)
                    && en.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                s += en.modifier().amount();
            }
        }
        return s;
    }

    private static String propOptions(Property<?> p) {
        StringBuilder sb = new StringBuilder(p.getName()).append("=[");
        boolean first = true;
        for (Object v : p.getPossibleValues()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(propValueName(p, v));
        }
        return sb.append(']').toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propValueName(Property p, Object v) {
        return p.getName((Comparable) v);
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void addLocalNames(InspectEntry e, String descId) {
        String current = I18n.get(descId);
        if (!current.equals(descId)) {
            e.add("panoptic.field.name_current", current);
        }
    }

    private static String displayName(String descId, Identifier id) {
        String current = I18n.get(descId);
        return current.equals(descId) ? id.toString() : current;
    }

    private static <T extends Comparable<T>> String propValue(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    private static String tagsString(Stream<? extends TagKey<?>> tags) {
        String s = tags.map(t -> "#" + t.location()).collect(Collectors.joining(", "));
        return s.isEmpty() ? I18n.get("panoptic.value.none") : s;
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