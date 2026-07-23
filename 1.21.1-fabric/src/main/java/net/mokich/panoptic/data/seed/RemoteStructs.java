package net.mokich.panoptic.data.seed;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.network.StructRegionPackets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RemoteStructs {
    public record Raw(String id, int x, int z) {}

    private static final Map<String, List<Raw>> RAW = new ConcurrentHashMap<>();
    private static final Map<String, List<SeedMap.Placed>> RESOLVED = new ConcurrentHashMap<>();
    private static final Map<String, Long> REQUESTED = new ConcurrentHashMap<>();
    private static final int MAX_IN_FLIGHT = 3;
    private static Map<ResourceLocation, Holder<Structure>> index;
    private static SeedMap indexFor;

    private RemoteStructs() {
    }

    public static synchronized void clear() {
        RAW.clear();
        RESOLVED.clear();
        REQUESTED.clear();
        index = null;
        indexFor = null;
    }

    private static int inFlight() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Map.Entry<String, Long> e : REQUESTED.entrySet()) {
            if (!RAW.containsKey(e.getKey()) && now - e.getValue() < 15000L) {
                n++;
            }
        }
        return n;
    }

    public static synchronized List<SeedMap.Placed> request(SeedMap map, int rx, int rz) {
        String key = map.dim.location() + "|" + rx + "|" + rz;
        List<SeedMap.Placed> done = RESOLVED.get(key);
        if (done != null) {
            return done;
        }
        List<Raw> raw = RAW.get(key);
        if (raw != null) {
            ensureIndex(map);
            List<SeedMap.Placed> out = new ArrayList<>(raw.size());
            for (Raw r : raw) {
                Holder<Structure> h = index.get(ResourceLocation.tryParse(r.id()));
                if (h != null) {
                    out.add(new SeedMap.Placed(h, r.x(), r.z()));
                }
            }
            RESOLVED.put(key, out);
            return out;
        }
        long now = System.currentTimeMillis();
        Long at = REQUESTED.get(key);
        if ((at == null || now - at > 15000L) && inFlight() < MAX_IN_FLIGHT) {
            REQUESTED.put(key, now);
            ClientPlayNetworking.send(
                    new StructRegionPackets.Request(map.dim.location().toString(), rx, rz));
        }
        return null;
    }

    public static void onResult(String dim, int rx, int rz, List<Raw> found) {
        RAW.put(dim + "|" + rx + "|" + rz, found);
    }

    private static void ensureIndex(SeedMap map) {
        if (indexFor == map && index != null) {
            return;
        }
        Map<ResourceLocation, Holder<Structure>> idx = new HashMap<>();
        for (Holder.Reference<Structure> h : map.structureList()) {
            idx.put(h.key().location(), h);
        }
        index = idx;
        indexFor = map;
    }
}
