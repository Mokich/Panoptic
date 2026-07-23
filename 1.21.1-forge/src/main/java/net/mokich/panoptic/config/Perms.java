package net.mokich.panoptic.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Perms {
    public enum Feature {
        INSPECTOR("panoptic.inspector"),
        SEED_VIEW("panoptic.seed.view"),
        SEED_STRUCTURES("panoptic.seed.structures"),
        TRADE("panoptic.trade"),
        TRADE_SPAWN("panoptic.trade.spawn"),
        SCREENS("panoptic.screens"),
        SCREENS_GIVE("panoptic.screens.give"),
        ADMIN("panoptic.admin");

        public final String node;

        Feature(String node) {
            this.node = node;
        }
    }

    private static volatile Set<Feature> synced;

    private Perms() {
    }

    public static boolean allowed(Feature f) {
        Set<Feature> s = synced;
        if (s != null) {
            return s.contains(f);
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return false;
        }
        if (p.hasPermissions(2)) {
            return true;
        }
        if (f == Feature.TRADE_SPAWN || f == Feature.SCREENS_GIVE || f == Feature.ADMIN) {
            return false;
        }
        return PanopticConfig.openAccess() && singleWorld();
    }

    private static boolean singleWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return true;
        }
        ServerData sd = mc.getCurrentServer();
        return sd != null && sd.isLan();
    }

    public static boolean seedScreenAllowed() {
        return allowed(Feature.SEED_VIEW) || allowed(Feature.SEED_STRUCTURES);
    }

    public static boolean serverSynced() {
        return synced != null;
    }

    public static void applySync(Set<Feature> perms) {
        synced = perms;
    }

    public static void applySyncNodes(List<String> nodes) {
        EnumSet<Feature> set = EnumSet.noneOf(Feature.class);
        for (Feature f : Feature.values()) {
            if (nodes.contains(f.node)) {
                set.add(f);
            }
        }
        synced = set;
    }

    public static void clearSync() {
        synced = null;
    }

    public static void deny() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("panoptic.perm.denied"), true);
        }
    }
}