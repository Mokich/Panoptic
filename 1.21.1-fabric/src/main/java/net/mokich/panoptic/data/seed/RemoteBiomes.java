package net.mokich.panoptic.data.seed;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.network.BiomeTilePackets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class RemoteBiomes {
    public static final int RES = BiomeTilePackets.RES;
    private static final int MAX_IN_FLIGHT = 3;

    public record Payload(int[] palette, byte[] grid) {}
    private record TileKey(String dim, int step, int tx, int tz) {}

    private static final Map<TileKey, Payload> TILES = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, Payload> e) {
                    return size() > 768;
                }
            });
    private static final Map<TileKey, Long> REQUESTED = new ConcurrentHashMap<>();

    private RemoteBiomes() {
    }

    public static void clear() {
        TILES.clear();
        REQUESTED.clear();
    }

    private static int inFlight() {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Map.Entry<TileKey, Long> e : REQUESTED.entrySet()) {
            if (!TILES.containsKey(e.getKey()) && e.getValue() > now) {
                n++;
            }
        }
        return n;
    }

    public static Payload get(SeedMap map, int step, int tx, int tz) {
        TileKey key = new TileKey(map.dim.location().toString(), step, tx, tz);
        Payload p = TILES.get(key);
        if (p != null) {
            return p;
        }
        long now = System.currentTimeMillis();
        Long deadline = REQUESTED.get(key);
        if ((deadline == null || now > deadline) && inFlight() < MAX_IN_FLIGHT) {
            REQUESTED.put(key, now + 12000L + ThreadLocalRandom.current().nextLong(6000L));
            ClientPlayNetworking.send(new BiomeTilePackets.Request(key.dim(), step, tx, tz));
        }
        return null;
    }

    public static void onResult(String dim, int step, int tx, int tz, int[] palette, byte[] grid) {
        if (grid.length == RES * RES) {
            TILES.put(new TileKey(dim, step, tx, tz), new Payload(palette, grid));
        }
    }

    private static Registry<Biome> biomeRegistry() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME);
    }

    public static Holder<Biome>[] resolve(Payload p) {
        Registry<Biome> reg = biomeRegistry();
        @SuppressWarnings("unchecked")
        Holder<Biome>[] out = new Holder[p.palette().length];
        if (reg == null) {
            return out;
        }
        for (int i = 0; i < out.length; i++) {
            out[i] = reg.getHolder(p.palette()[i]).orElse(null);
        }
        return out;
    }

    public static Holder<Biome> biomeAt(ResourceLocation dim, int blockX, int blockZ) {
        String d = dim.toString();
        for (int step = 4; step <= 512; step <<= 1) {
            int tw = RES * step;
            int tx = Math.floorDiv(blockX, tw);
            int tz = Math.floorDiv(blockZ, tw);
            Payload p = TILES.get(new TileKey(d, step, tx, tz));
            if (p == null) {
                continue;
            }
            int px = Mth.clamp(Math.floorDiv(blockX - tx * tw, step), 0, RES - 1);
            int pz = Mth.clamp(Math.floorDiv(blockZ - tz * tw, step), 0, RES - 1);
            Registry<Biome> reg = biomeRegistry();
            if (reg == null) {
                return null;
            }
            return reg.getHolder(p.palette()[p.grid()[pz * RES + px] & 0xFF]).orElse(null);
        }
        return null;
    }

    public static BlockPos findNearest(ResourceLocation dim, Holder<Biome> target, int cx, int cz) {
        Registry<Biome> reg = biomeRegistry();
        if (reg == null) {
            return null;
        }
        int targetId = reg.getId(target.value());
        if (targetId < 0) {
            return null;
        }
        String d = dim.toString();
        List<Map.Entry<TileKey, Payload>> snapshot;
        synchronized (TILES) {
            snapshot = new ArrayList<>(TILES.entrySet());
        }
        long best = Long.MAX_VALUE;
        BlockPos bestPos = null;
        for (Map.Entry<TileKey, Payload> e : snapshot) {
            TileKey k = e.getKey();
            if (!k.dim().equals(d)) {
                continue;
            }
            Payload p = e.getValue();
            int pi = -1;
            for (int i = 0; i < p.palette().length; i++) {
                if (p.palette()[i] == targetId) {
                    pi = i;
                    break;
                }
            }
            if (pi < 0) {
                continue;
            }
            int tw = RES * k.step();
            byte want = (byte) pi;
            byte[] grid = p.grid();
            for (int i = 0; i < grid.length; i++) {
                if (grid[i] != want) {
                    continue;
                }
                int wx = k.tx() * tw + (i % RES) * k.step();
                int wz = k.tz() * tw + (i / RES) * k.step();
                long dx = wx - cx;
                long dz = wz - cz;
                long dist = dx * dx + dz * dz;
                if (dist < best) {
                    best = dist;
                    bestPos = new BlockPos(wx, 64, wz);
                }
            }
        }
        return bestPos;
    }
}