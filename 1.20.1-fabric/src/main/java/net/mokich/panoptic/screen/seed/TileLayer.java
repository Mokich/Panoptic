package net.mokich.panoptic.screen.seed;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mokich.panoptic.data.seed.RemoteBiomes;
import net.mokich.panoptic.data.seed.SeedMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
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

    public void draw(GuiGraphics g, SeedMap map, ExecutorService pool,
                     double camX, double camZ, double zoom, int mLeft, int mTop, int mRight, int mBottom) {
        double mcx = (mLeft + mRight) / 2.0;
        double mcy = (mTop + mBottom) / 2.0;
        int step = stepFor(zoom);
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
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int uploads = 0;
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
                if (t.state >= 1 && (t.state == 2 || uploads < 4)) {
                    if (t.state == 1) {
                        uploads++;
                    }
                    if (t.upload()) {
                        int sx = (int) Math.round(mcx + ((double) tx * tw - camX) * zoom);
                        int sy = (int) Math.round(mcy + ((double) tz * tw - camZ) * zoom);
                        int sw = (int) Math.round(tw * zoom) + 1;
                        g.blit(t.id, sx, sy, sw, sw, 0, 0, RES, RES, RES, RES);
                    }
                }
            }
        }
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
        String ck = cacheKey(map, step, tx, tz);
        int[] cached = CACHE.get(ck);
        if (cached != null) {
            t.argb = cached;
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
                if (g != generation || m == null) {
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
                t.state = 1;
            });
            return t;
        }
        pool.submit(() -> {
            if (g != generation || m == null) {
                return;
            }
            int[] px = build(m, step, tx, tz, (pxi, pz) -> m.biome(tx * RES * step + pxi * step, tz * RES * step + pz * step));
            if (g != generation) {
                return;
            }
            CACHE.put(ck, px);
            t.argb = px;
            t.state = 1;
        });
        return t;
    }

    private interface BiomeAt {
        Holder<Biome> get(int pxi, int pz);
    }

    private static int[] build(SeedMap m, int step, int tx, int tz, BiomeAt src) {
        int tw = RES * step;
        int[] px = new int[RES * RES];
        boolean dither = step < 16;
        Object[] prevRow = new Object[RES];
        for (int pz = 0; pz < RES; pz++) {
            Object left = null;
            for (int pxi = 0; pxi < RES; pxi++) {
                int wx = tx * tw + pxi * step;
                int wz = tz * tw + pz * step;
                Holder<Biome> b = src.get(pxi, pz);
                int c = b == null ? 0xFF14100A : m.color(b);
                if (dither) {
                    int hash = wx * 0x2545F491 + wz * 0x9E3779B9;
                    hash ^= hash >>> 15;
                    hash *= 0x85EBCA6B;
                    hash ^= hash >>> 13;
                    c = shade(c, ((hash & 7) - 3) * 3);
                }
                if ((left != null && b != left) || (prevRow[pxi] != null && b != prevRow[pxi])) {
                    c = shade(c, -30);
                }
                px[pz * RES + pxi] = c;
                prevRow[pxi] = b;
                left = b;
            }
        }
        return px;
    }

    private static String cacheKey(SeedMap map, int step, int tx, int tz) {
        return (map.serverBiomes() ? "s|" : "4|") + map.seed + "|" + map.dim.location()
                + "|" + step + "|" + tx + "|" + tz;
    }

    private static int shade(int argb, int delta) {
        int r = Mth.clamp(((argb >> 16) & 0xFF) + delta, 0, 255);
        int gg = Mth.clamp(((argb >> 8) & 0xFF) + delta, 0, 255);
        int b = Mth.clamp((argb & 0xFF) + delta, 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
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
        boolean keep = true;
        ResourceLocation id;
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
                        int a = src[y * RES + x];
                        int rr = (a >> 16) & 0xFF;
                        int gg = (a >> 8) & 0xFF;
                        int bb = a & 0xFF;
                        img.setPixelRGBA(x, y, 0xFF000000 | (bb << 16) | (gg << 8) | rr);
                    }
                }
                tex = new DynamicTexture(img);
                id = new ResourceLocation("panoptic",
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