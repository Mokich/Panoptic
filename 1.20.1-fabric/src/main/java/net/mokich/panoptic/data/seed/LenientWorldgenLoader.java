package net.mokich.panoptic.data.seed;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LenientWorldgenLoader {
    private static final Set<ResourceKey<? extends Registry<?>>> CONTENT = Set.of(
            Registries.DIMENSION_TYPE,
            Registries.BIOME,
            Registries.NOISE,
            Registries.DENSITY_FUNCTION,
            Registries.NOISE_SETTINGS,
            Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
            Registries.STRUCTURE,
            Registries.LEVEL_STEM);

    private LenientWorldgenLoader() {}

    public static RegistryAccess.Frozen load(ResourceManager mgr, RegistryAccess base,
            List<RegistryDataLoader.RegistryData<?>> datas) {
        List<Registry<?>> regs = new ArrayList<>();
        Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> ctx = new HashMap<>();
        base.registries().forEach(e -> ctx.put(e.key(), contextInfo(e.value())));
        Map<ResourceKey<? extends Registry<?>>, WritableRegistry<?>> byKey = new LinkedHashMap<>();
        for (RegistryDataLoader.RegistryData<?> d : datas) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            MappedRegistry<?> reg = new MappedRegistry<>((ResourceKey) d.key(), Lifecycle.stable());
            byKey.put(d.key(), reg);
            ctx.put(d.key(), newInfo(reg));
            regs.add(reg);
        }
        RegistryOps.RegistryInfoLookup lookup = new RegistryOps.RegistryInfoLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> k) {
                return Optional.ofNullable((RegistryOps.RegistryInfo<T>) ctx.get(k));
            }
        };
        for (RegistryDataLoader.RegistryData<?> d : datas) {
            if (CONTENT.contains(d.key())) {
                loadContents(lookup, mgr, d, byKey.get(d.key()));
            }
        }
        for (Registry<?> r : regs) {
            try {
                r.freeze();
            } catch (Throwable ignored) {
            }
        }
        return new RegistryAccess.ImmutableRegistryAccess(regs).freeze();
    }

    private static <T> RegistryOps.RegistryInfo<T> contextInfo(Registry<T> reg) {
        return new RegistryOps.RegistryInfo<>(reg.asLookup(), reg.asTagAddingLookup(), reg.registryLifecycle());
    }

    private static <T> RegistryOps.RegistryInfo<T> newInfo(WritableRegistry<T> reg) {
        return new RegistryOps.RegistryInfo<>(reg.asLookup(), reg.createRegistrationLookup(), reg.registryLifecycle());
    }

    @SuppressWarnings("unchecked")
    private static <T> void loadContents(RegistryOps.RegistryInfoLookup lookup, ResourceManager mgr,
            RegistryDataLoader.RegistryData<T> data, WritableRegistry<?> target) {
        WritableRegistry<T> registry = (WritableRegistry<T>) target;
        ResourceLocation rk = data.key().location();
        String dir = rk.getNamespace().equals("minecraft") ? rk.getPath() : rk.getNamespace() + "/" + rk.getPath();
        FileToIdConverter conv = FileToIdConverter.json(dir);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        for (Map.Entry<ResourceLocation, Resource> e : conv.listMatchingResources(mgr).entrySet()) {
            try (Reader reader = e.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DataResult<T> res = data.elementCodec().parse(ops, json);
                T val = res.getOrThrow(false, s -> {
                });
                registry.register(ResourceKey.create(data.key(), conv.fileToId(e.getKey())), val, Lifecycle.stable());
            } catch (Throwable ignored) {
            }
        }
    }
}