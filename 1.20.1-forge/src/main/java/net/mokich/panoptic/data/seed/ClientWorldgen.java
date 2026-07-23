package net.mokich.panoptic.data.seed;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.resource.ResourcePackLoader;

import java.util.ArrayList;
import java.util.List;

public final class ClientWorldgen {
    private static volatile HolderLookup.Provider lookup;
    private static volatile boolean loading;
    private static volatile boolean failed;

    private ClientWorldgen() {
    }

    public static boolean ready() {
        return lookup != null;
    }

    public static boolean failed() {
        return failed;
    }

    public static boolean loading() {
        return loading;
    }

    public static HolderLookup.Provider get() {
        return lookup;
    }

    public static void ensureLoading() {
        if (lookup != null || failed || loading) {
            return;
        }
        loading = true;
        Thread t = new Thread(() -> {
            try {
                lookup = build();
            } catch (Throwable x) {
                failed = true;
            } finally {
                loading = false;
            }
        }, "panoptic-worldgen-load");
        t.setDaemon(true);
        t.start();
    }

    private static HolderLookup.Provider build() {
        List<PackResources> packs = new ArrayList<>();
        packs.add(ServerPacksSource.createVanillaPackSource());
        for (IModFileInfo info : ModList.get().getModFiles()) {
            try {
                PackResources pack = ResourcePackLoader.createPackForMod(info);
                if (pack != null) {
                    packs.add(pack);
                }
            } catch (Throwable ignored) {
            }
        }
        try (MultiPackResourceManager mgr = new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
            RegistryAccess.Frozen base = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            List<RegistryDataLoader.RegistryData<?>> registries =
                    new ArrayList<>(RegistryDataLoader.WORLDGEN_REGISTRIES);
            registries.addAll(RegistryDataLoader.DIMENSION_REGISTRIES);
            return LenientWorldgenLoader.load(mgr, base, registries);
        }
    }
}
