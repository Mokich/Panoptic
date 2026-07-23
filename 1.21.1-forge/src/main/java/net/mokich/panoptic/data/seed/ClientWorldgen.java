package net.mokich.panoptic.data.seed;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                Path root = info.getFile().getSecureJar().getRootPath();
                String name = "mod/" + info.moduleName();
                PackLocationInfo loc = new PackLocationInfo(name, Component.literal(name),
                        PackSource.DEFAULT, Optional.empty());
                packs.add(new PathPackResources(loc, root));
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
