package net.mokich.panoptic.screen.seed;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mokich.panoptic.data.seed.RemoteBiomes;
import net.mokich.panoptic.data.seed.SeedMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public final class TileLayer {
    private static final int RES = 128;
    private static final int MAX_TILES = 512;
    private static final Map<String, int[]> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, int[]>(512, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, int[]> e) {
                    return size() > 2000;
                }
            });

    private final Map<Long, Tile> tiles = new HashMap<>();
    private int texSeq;
    private volatile int generation;
    private volatile long visSubDone;
    private volatile long visSubTotal;
    private volatile int curStep;

    public float progress() {
        long total = visSubTotal;
        if (total <= 0) {
            return 1F;
        }
        return Mth.clamp((float) visSubDone / total, 0F, 1F);
    }

    public boolean loading() {
        return visSubDone < visSubTotal;
    }

    public static long key(int step, int x, int z) {
        return ((long) (step & 0x3FF) << 48) | (((long) x & 0xFFFFFFL) << 24) | ((long) z & 0xFFFFFFL);
    }

    public void invalidate() {
        generation++;
        clear();
    }

    public void clear() {
        for (Tile t : tiles.values()) {
            t.dispose();
        }
        tiles.clear();
    }

    public void draw(GuiGraphicsExtractor g, SeedMap map, ExecutorService pool,
                     double camX, double camZ, double zoom, int mLeft, int mTop, int mRight, int mBottom) {
        double mcx = (mLeft + mRight) / 2.0;
        double mcy = (mTop + mBottom) / 2.0;
        int step = stepFor(zoom);
        curStep = step;
        int tw = RES * step;
        double wl = camX + (mLeft - mcx) / zoom;
        double wt = camZ + (mTop - mcy) / zoom;
        double wr = camX + (mRight - mcx) / zoom;
        double wb = camZ + (mBottom - mcy) / zoom;
        int txMin = Mth.floor(wl / tw);
        int txMax = Mth.floor(wr / tw);
        int tzMin = Mth.floor(wt / tw);
        int tzMax = Mth.floor(wb / tw);
        for (Tile t : tiles.values()) {
            t.keep = false;
        }
        int uploads = 0;
        long doneSum = 0;
        long totalSum = 0;
        long now = System.currentTimeMillis();
        for (int tz = tzMin; tz <= tzMax; tz++) {
            for (int tx = txMin; tx <= txMax; tx++) {
                int fstep = step;
                int ftx = tx;
                int ftz = tz;
                long k = key(step, tx, tz);
                Tile ex = tiles.get(k);
                if (ex != null && ex.state == 0 && ex.retryAt != 0 && now > ex.retryAt) {
                    ex.dispose();
                    tiles.remove(k);
                }
                Tile t = tiles.computeIfAbsent(k, kk -> request(map, pool, fstep, ftx, ftz));
                t.keep = true;
                doneSum += t.subDone;
                totalSum += t.subTotal;
                if (t.state >= 1 && (t.state == 2 || uploads < 4)) {
                    if (t.state == 1) {
                        uploads++;
                    }
                    if (t.upload()) {
                        int sx = (int) Math.round(mcx + ((double) tx * tw - camX) * zoom);
                        int sy = (int) Math.round(mcy + ((double) tz * tw - camZ) * zoom);
                        int sw = (int) Math.round(tw * zoom) + 1;
                        g.blit(RenderPipelines.GUI_TEXTURED, t.id, sx, sy, 0.0F, 0.0F, sw, sw, RES, RES, RES, RES);
                    }
                }
            }
        }
        visSubDone = doneSum;
        visSubTotal = totalSum;
        evict(step);
    }

    private static int stepFor(double zoom) {
        double inv = 1.0 / zoom;
        int step = Integer.highestOneBit(Math.max(1, (int) inv));
        if (step < inv) {
            step <<= 1;
        }
        return Mth.clamp(step, 4, 512);
    }

    private Tile request(SeedMap map, ExecutorService pool, int step, int tx, int tz) {
        Tile t = new Tile(step, tx, tz);
        if (map.heightMap && map.canHeightMap()) {
            int side = Math.min(RES, RES * step / 16);
            t.subTotal = Math.max(1, side * side);
        }
        String ck = cacheKey(map, step, tx, tz);
        int[] cached = CACHE.get(ck);
        if (cached != null) {
            t.argb = cached;
            t.subDone = t.subTotal;
            t.state = 1;
            return t;
        }
        int g = generation;
        SeedMap m = map;
        if (map.serverBiomes()) {
            RemoteBiomes.Payload payload = RemoteBiomes.get(map, step, tx, tz);
            if (payload == null) {
                t.retryAt = System.currentTimeMillis() + 700L;
                return t;
            }
            pool.submit(() -> {
                if (g != generation || m == null || step != curStep) {
                    return;
                }
                Holder<Biome>[] pal = RemoteBiomes.resolve(payload);
                byte[] grid = payload.grid();
                int[] px = build(m, step, tx, tz, (pxi, pz) -> pal[grid[pz * RES + pxi] & 0xFF]);
                if (g != generation) {
                    return;
                }
                CACHE.put(ck, px);
                t.argb = px;
                t.subDone = t.subTotal;
                t.state = 1;
            });
            return t;
        }
        pool.submit(() -> {
            if (g != generation || m == null || step != curStep) {
                return;
            }
            int[] px;
            if (m.heightMap && m.canHeightMap()) {
                int baseX = tx * RES * step;
                int baseZ = tz * RES * step;
                int[] out = new int[RES * RES];
                Map<Long, int[]> grids = new HashMap<>();
                boolean batch = m.canBatchHeight();
                for (int pz = 0; pz < RES; pz++) {
                    int bz = baseZ + pz * step;
                    int cz = bz >> 4;
                    int lz = bz & 15;
                    for (int pxi = 0; pxi < RES; pxi++) {
                        int bx = baseX + pxi * step;
                        int cx = bx >> 4;
                        int lx = bx & 15;
                        int h;
                        if (batch) {
                            long gk = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                            int[] grid = grids.get(gk);
                            if (grid == null) {
                                grid = m.chunkSurface(cx, cz, step);
                                if (grid == null) {
                                    batch = false;
                                    out[pz * RES + pxi] = m.elevationColor(m.surfaceHeight(bx, bz));
                                    continue;
                                }
                                grids.put(gk, grid);
                                t.subDone++;
                            }
                            h = grid[lz * 16 + lx];
                        } else {
                            h = m.surfaceHeight(bx, bz);
                        }
                        out[pz * RES + pxi] = m.elevationColor(h);
                    }
                    if (g != generation || step != curStep) {
                        return;
                    }
                }
                px = out;
            } else {
                px = build(m, step, tx, tz, (pxi, pz) -> m.biome(tx * RES * step + pxi * step, tz * RES * step + pz * step));
            }
            if (g != generation) {
                return;
            }
            CACHE.put(ck, px);
            t.argb = px;
            t.subDone = t.subTotal;
            t.state = 1;
        });
        return t;
    }

    private interface BiomeAt {
        Holder<Biome> get(int pxi, int pz);
    }

    private static int[] build(SeedMap m, int step, int tx, int tz, BiomeAt src) {
        int[] px = new int[RES * RES];
        for (int pz = 0; pz < RES; pz++) {
            for (int pxi = 0; pxi < RES; pxi++) {
                Holder<Biome> b = src.get(pxi, pz);
                px[pz * RES + pxi] = b == null ? 0xFF14100A : m.color(b);
            }
        }
        return px;
    }

    private static String cacheKey(SeedMap map, int step, int tx, int tz) {
        return (map.serverBiomes() ? "s|" : "4|") + map.seed + "|" + map.dim.identifier()
                + "|" + (map.heightMap ? "H" : "B") + map.sampleY + "|" + step + "|" + tx + "|" + tz;
    }

    private void evict(int curStep) {
        Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator();
        while (it.hasNext()) {
            Tile t = it.next().getValue();
            if (t.step != curStep || (!t.keep && tiles.size() > MAX_TILES)) {
                t.dispose();
                it.remove();
            }
        }
    }

    private final class Tile {
        final int step;
        final int tx;
        final int tz;
        volatile int state;
        volatile int[] argb;
        volatile long retryAt;
        volatile int subDone;
        volatile int subTotal = 1;
        boolean keep = true;
        Identifier id;
        DynamicTexture tex;

        Tile(int step, int tx, int tz) {
            this.step = step;
            this.tx = tx;
            this.tz = tz;
        }

        boolean upload() {
            if (state == 2) {
                return id != null;
            }
            int[] src = argb;
            if (src == null) {
                return false;
            }
            try {
                NativeImage img = new NativeImage(NativeImage.Format.RGBA, RES, RES, false);
                for (int y = 0; y < RES; y++) {
                    for (int x = 0; x < RES; x++) {
                        img.setPixel(x, y, 0xFF000000 | (src[y * RES + x] & 0xFFFFFF));
                    }
                }
                tex = new DynamicTexture(() -> "panoptic-tile", img);
                id = Identifier.fromNamespaceAndPath("panoptic",
                        "seedmap/" + texSeq++ + "_" + step + "_" + tx + "_" + tz);
                Minecraft.getInstance().getTextureManager().register(id, tex);
                state = 2;
                return true;
            } catch (Throwable t) {
                state = 2;
                return false;
            }
        }

        void dispose() {
            if (id != null) {
                Minecraft.getInstance().getTextureManager().release(id);
                id = null;
            }
            if (tex != null) {
                tex.close();
                tex = null;
            }
        }
    }
}