package net.mokich.panoptic.screen.seed;

import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.config.Perms;
import net.mokich.panoptic.data.seed.RemoteStructs;
import net.mokich.panoptic.data.seed.SeedMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public final class StructWorker {
    private static final int SREGION = 512;
    private static final Map<String, List<SeedMap.Placed>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, List<SeedMap.Placed>>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<SeedMap.Placed>> e) {
                    return size() > 1200;
                }
            });

    private final IntSupplier generation;
    private final Map<Long, StructRegion> regions = new HashMap<>();
    private final List<SeedMap.Placed> visible = new ArrayList<>();
    private final HashSet<Long> seen = new HashSet<>();
    private volatile List<SeedMap.Placed> strongholds;
    private boolean shRequested;
    private final AtomicInteger pending = new AtomicInteger();
    private int want;
    private int loaded;
    private long regionOrderKey = Long.MIN_VALUE;
    private final List<long[]> regionOrder = new ArrayList<>();

    public StructWorker(IntSupplier generation) {
        this.generation = generation;
    }

    public List<SeedMap.Placed> visible() {
        return visible;
    }

    public int want() {
        return want;
    }

    public int loaded() {
        return loaded;
    }

    public void reset() {
        regions.clear();
        visible.clear();
        strongholds = null;
        shRequested = false;
    }

    public void invalidate() {
        CACHE.clear();
        regions.clear();
    }

    public void collect(SeedMap map, ExecutorService pool, double camX, double camZ, double zoom,
                        int mLeft, int mTop, int mRight, int mBottom) {
        visible.clear();
        seen.clear();
        want = 0;
        loaded = 0;
        if (map == null || zoom < ModSettings.get(ModSettings.STRUCT_MIN_ZOOM)) {
            return;
        }
        double mcx = (mLeft + mRight) / 2.0;
        double mcy = (mTop + mBottom) / 2.0;
        if (!shRequested) {
            shRequested = true;
            int g = generation.getAsInt();
            SeedMap m = map;
            pool.submit(() -> {
                List<SeedMap.Placed> s = m.strongholds();
                if (g == generation.getAsInt()) {
                    strongholds = s;
                }
            });
        }
        double wl = camX + (mLeft - mcx) / zoom;
        double wt = camZ + (mTop - mcy) / zoom;
        double wr = camX + (mRight - mcx) / zoom;
        double wb = camZ + (mBottom - mcy) / zoom;
        List<SeedMap.Placed> sh = strongholds;
        if (sh != null) {
            for (SeedMap.Placed p : sh) {
                addVisible(p, wl, wt, wr, wb);
            }
        }
        int rx0 = Mth.floor(wl / SREGION);
        int rx1 = Mth.floor(wr / SREGION);
        int rz0 = Mth.floor(wt / SREGION);
        int rz1 = Mth.floor(wb / SREGION);
        for (StructRegion sr : regions.values()) {
            sr.keep = false;
        }
        long total = (long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1);
        if (total <= 20000) {
            rebuildRegionOrder(rx0, rx1, rz0, rz1);
            for (long[] rc : regionOrder) {
                int rx = (int) rc[0];
                int rz = (int) rc[1];
                long key = regKey(rx, rz);
                StructRegion sr = regions.get(key);
                if (sr != null && sr.retryAt != 0L && System.currentTimeMillis() > sr.retryAt && sr.list != null) {
                    regions.remove(key);
                    sr = null;
                }
                if (sr == null) {
                    sr = tryRequestRegion(map, pool, rx, rz);
                    if (sr != null) {
                        regions.put(key, sr);
                    }
                }
                want++;
                if (sr == null) {
                    continue;
                }
                sr.keep = true;
                List<SeedMap.Placed> list = sr.list;
                if (list != null) {
                    loaded++;
                    for (SeedMap.Placed p : list) {
                        addVisible(p, wl, wt, wr, wb);
                    }
                }
            }
        } else {
            for (Map.Entry<Long, StructRegion> e : regions.entrySet()) {
                int rx = (int) (e.getKey() >> 32);
                int rz = (int) (long) e.getKey();
                if (rx < rx0 || rx > rx1 || rz < rz0 || rz > rz1) {
                    continue;
                }
                StructRegion sr = e.getValue();
                sr.keep = true;
                List<SeedMap.Placed> list = sr.list;
                if (list != null) {
                    for (SeedMap.Placed p : list) {
                        addVisible(p, wl, wt, wr, wb);
                    }
                }
            }
        }
        int cap = Math.max(1200, want * 2);
        if (regions.size() > cap) {
            Iterator<Map.Entry<Long, StructRegion>> it = regions.entrySet().iterator();
            while (it.hasNext() && regions.size() > cap) {
                if (!it.next().getValue().keep) {
                    it.remove();
                }
            }
        }
    }

    private void addVisible(SeedMap.Placed p, double wl, double wt, double wr, double wb) {
        if (!inView(p, wl, wt, wr, wb)) {
            return;
        }
        ResourceLocation id = p.structure().unwrapKey().map(ResourceKey::location).orElse(null);
        long key = ((long) (id == null ? 0 : id.hashCode()) << 32)
                | (((long) (p.x() >> 4) & 0xFFFFL) << 16) | ((long) (p.z() >> 4) & 0xFFFFL);
        if (seen.add(key)) {
            visible.add(p);
        }
    }

    private static boolean inView(SeedMap.Placed p, double wl, double wt, double wr, double wb) {
        return p.x() >= wl - 256 && p.x() <= wr + 256 && p.z() >= wt - 256 && p.z() <= wb + 256;
    }

    private void rebuildRegionOrder(int rx0, int rx1, int rz0, int rz1) {
        long key = ((long) (rx0 & 0xFFFF) << 48) | ((long) (rx1 & 0xFFFF) << 32)
                | ((long) (rz0 & 0xFFFF) << 16) | (rz1 & 0xFFFF);
        if (key == regionOrderKey) {
            return;
        }
        regionOrderKey = key;
        regionOrder.clear();
        double crx = (rx0 + rx1) / 2.0;
        double crz = (rz0 + rz1) / 2.0;
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                regionOrder.add(new long[]{rx, rz});
            }
        }
        regionOrder.sort((a, b) -> {
            double da = (a[0] - crx) * (a[0] - crx) + (a[1] - crz) * (a[1] - crz);
            double db = (b[0] - crx) * (b[0] - crx) + (b[1] - crz) * (b[1] - crz);
            return Double.compare(da, db);
        });
    }

    private StructRegion tryRequestRegion(SeedMap map, ExecutorService pool, int rx, int rz) {
        StructRegion sr = new StructRegion();
        String ck = "6|" + map.seed + "|" + map.dim.location() + "|" + rx + "|" + rz;
        List<SeedMap.Placed> cached = CACHE.get(ck);
        if (cached != null) {
            sr.list = cached;
            return sr;
        }
        if (map.remote() && Perms.serverSynced()) {
            if (!Perms.allowed(Perms.Feature.SEED_STRUCTURES)) {
                sr.list = List.of();
                return sr;
            }
            List<SeedMap.Placed> got = RemoteStructs.request(map, rx, rz);
            if (got != null) {
                CACHE.put(ck, got);
                sr.list = got;
            } else {
                sr.list = List.of();
                sr.retryAt = System.currentTimeMillis() + 1500L;
            }
            return sr;
        }
        if (pending.get() >= 6) {
            return null;
        }
        pending.incrementAndGet();
        int g = generation.getAsInt();
        SeedMap m = map;
        pool.submit(() -> {
            try {
                if (g != generation.getAsInt() || m == null) {
                    return;
                }
                SeedMap.RegionResult r = m.findStructuresRegion(rx, rz);
                if (g != generation.getAsInt()) {
                    return;
                }
                if (r.exact()) {
                    CACHE.put(ck, r.list());
                } else {
                    sr.retryAt = System.currentTimeMillis() + 4000L;
                }
                sr.list = r.list();
            } finally {
                pending.decrementAndGet();
            }
        });
        return sr;
    }

    private static long regKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public int loadedCountFor(ResourceLocation id) {
        int n = 0;
        for (StructRegion sr : regions.values()) {
            List<SeedMap.Placed> list = sr.list;
            if (list == null) {
                continue;
            }
            for (SeedMap.Placed p : list) {
                if (id.equals(p.structure().unwrapKey().map(ResourceKey::location).orElse(null))) {
                    n++;
                }
            }
        }
        return n;
    }

    public BlockPos locateOnMap(SeedMap m, ResourceLocation id, int ccx, int ccz, int gen) {
        int crx = Mth.floor(ccx / (double) SREGION);
        int crz = Mth.floor(ccz / (double) SREGION);
        for (int ring = 0; ring <= 96; ring++) {
            BlockPos best = null;
            long bestD = Long.MAX_VALUE;
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    if (gen != generation.getAsInt()) {
                        return null;
                    }
                    int rx = crx + dx;
                    int rz = crz + dz;
                    String ck = "6|" + m.seed + "|" + m.dim.location() + "|" + rx + "|" + rz;
                    List<SeedMap.Placed> list = CACHE.get(ck);
                    if (list == null) {
                        SeedMap.RegionResult r = m.findStructuresRegion(rx, rz);
                        list = r.list();
                        if (r.exact()) {
                            CACHE.put(ck, list);
                        }
                    }
                    for (SeedMap.Placed p : list) {
                        if (!id.equals(p.structure().unwrapKey().map(ResourceKey::location).orElse(null))) {
                            continue;
                        }
                        long ddx = p.x() - ccx;
                        long ddz = p.z() - ccz;
                        long d = ddx * ddx + ddz * ddz;
                        if (d < bestD) {
                            bestD = d;
                            best = new BlockPos(p.x(), 64, p.z());
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static final class StructRegion {
        volatile List<SeedMap.Placed> list;
        volatile long retryAt;
        boolean keep = true;
    }
}