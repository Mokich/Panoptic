package net.mokich.panoptic.data.seed;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class SeedMap {
    public final long seed;
    public final boolean currentWorld;
    public final ResourceKey<Level> dim;
    public final RegistryAccess access;
    private final BiomeSource biomeSource;
    private final NoiseGeneratorSettings settings;
    private final HolderGetter<NormalNoise.NoiseParameters> noiseGetter;
    private final ChunkGenerator gen;
    private final LevelHeightAccessor levelHeight;
    private final StructureTemplateManager templates;
    private final ChunkGeneratorStructureState liveState;
    private final Path regionDir;
    private final ServerLevel serverLevel;
    private final int seaY;
    private final ThreadLocal<RandomState> tlRandom = ThreadLocal.withInitial(this::newRandom);
    private final Map<Holder<Biome>, Integer> colorCache = new ConcurrentHashMap<>();
    private volatile ChunkGeneratorStructureState structState;
    private volatile boolean stateTried;
    private final Object stateLock = new Object();
    private volatile List<Placed> strongholdCache;
    public volatile boolean closed;
    public Predicate<ResourceLocation> hiddenFilter = id -> false;
    private final AtomicInteger serverBusy = new AtomicInteger();

    public record Placed(Holder<Structure> structure, int x, int z) {}

    private SeedMap(long seed, boolean cur, ResourceKey<Level> dim, RegistryAccess access, BiomeSource bs,
                   NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noiseGetter,
                   ChunkGenerator gen, LevelHeightAccessor levelHeight,
                   StructureTemplateManager templates, ChunkGeneratorStructureState liveState, Path regionDir,
                   ServerLevel serverLevel) {
        this.seed = seed;
        this.currentWorld = cur;
        this.dim = dim;
        this.access = access;
        this.biomeSource = bs;
        this.settings = settings;
        this.noiseGetter = noiseGetter;
        this.gen = gen;
        this.levelHeight = levelHeight;
        this.templates = templates;
        this.liveState = liveState;
        this.regionDir = regionDir;
        this.serverLevel = serverLevel;
        this.seaY = QuartPos.fromBlock(63);
    }

    private RandomState newRandom() {
        return RandomState.create(settings, noiseGetter, seed);
    }

    public static long worldSeed() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.overworld() != null) {
            return server.overworld().getSeed();
        }
        Long remote = ServerSeed.get();
        return remote != null ? remote : 0L;
    }

    public static long parseSeed(String text) {
        if (text == null || text.isBlank()) {
            return worldSeed();
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return text.hashCode();
        }
    }

    public static SeedMap create(long seed, ResourceKey<Level> dim) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            ServerLevel sl = server.getLevel(dim);
            ChunkGenerator gen = sl == null ? null : sl.getChunkSource().getGenerator();
            if (gen != null && gen.getBiomeSource() != null) {
                RegistryAccess acc = sl.registryAccess();
                NoiseGeneratorSettings ngs = gen instanceof NoiseBasedChunkGenerator nbc
                        ? nbc.generatorSettings().value() : fallbackSettings(acc, dim);
                if (ngs == null) {
                    return null;
                }
                boolean cur = seed == sl.getSeed();
                ChunkGeneratorStructureState live = cur ? sl.getChunkSource().getGeneratorState() : null;
                if (live != null) {
                    ChunkGeneratorStructureState fl = live;
                    server.execute(() -> {
                        try {
                            fl.ensureStructuresGenerated();
                        } catch (Throwable ignored) {
                        }
                    });
                }
                Path region = null;
                if (cur) {
                    try {
                        region = DimensionType.getStorageFolder(dim, server.getWorldPath(LevelResource.ROOT)).resolve("region");
                    } catch (Throwable ignored) {
                    }
                }
                return new SeedMap(seed, cur, dim, acc, gen.getBiomeSource(),
                        ngs, acc.lookupOrThrow(Registries.NOISE), gen, sl,
                        server.getStructureManager(), live, region, cur ? sl : null);
            }
        }
        RegistryAccess acc = mc.level != null ? mc.level.registryAccess() : null;
        if (acc != null) {
            try {
                Holder<NoiseGeneratorSettings> ngs;
                Holder<MultiNoiseBiomeSourceParameterList> pl;
                HolderGetter<NormalNoise.NoiseParameters> noise;
                HolderLookup.Provider used = null;
                try {
                    ngs = acc.registryOrThrow(Registries.NOISE_SETTINGS)
                            .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
                    pl = acc.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
                    noise = acc.lookupOrThrow(Registries.NOISE);
                    BiomeSource bs = MultiNoiseBiomeSource.createFromPreset(pl);
                    Long remote = ServerSeed.get();
                    boolean cur = remote != null && seed == remote && dim == Level.OVERWORLD;
                    return new SeedMap(seed, cur, dim, acc, bs, ngs.value(),
                            noise, null, null, null, null, null, null);
                } catch (Throwable missing) {
                    if (ClientWorldgen.ready()) {
                        used = ClientWorldgen.get();
                    } else if (ClientWorldgen.failed()) {
                        used = vanilla();
                    } else {
                        ClientWorldgen.ensureLoading();
                        return null;
                    }
                    if (used == null) {
                        throw missing;
                    }
                }
                noise = used.lookupOrThrow(Registries.NOISE);
                BiomeSource bs = null;
                NoiseGeneratorSettings settings = null;
                try {
                    var stem = used.lookupOrThrow(Registries.LEVEL_STEM)
                            .get(ResourceKey.create(Registries.LEVEL_STEM, dim.location()));
                    if (stem.isPresent()) {
                        ChunkGenerator stemGen = stem.get().value().generator();
                        bs = stemGen.getBiomeSource();
                        if (stemGen instanceof NoiseBasedChunkGenerator nbc) {
                            settings = nbc.generatorSettings().value();
                        }
                    }
                } catch (Throwable ignored2) {
                }
                if (bs == null) {
                    if (dim == Level.NETHER) {
                        bs = MultiNoiseBiomeSource.createFromPreset(
                                used.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
                    } else if (dim == Level.END) {
                        bs = TheEndBiomeSource.create(
                                used.lookupOrThrow(Registries.BIOME));
                    } else {
                        bs = MultiNoiseBiomeSource.createFromPreset(
                                used.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
                    }
                }
                if (settings == null) {
                    var key = dim == Level.NETHER ? NoiseGeneratorSettings.NETHER
                            : dim == Level.END ? NoiseGeneratorSettings.END : NoiseGeneratorSettings.OVERWORLD;
                    settings = used.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
                }
                Long remote = ServerSeed.get();
                boolean cur = remote != null && seed == remote;
                SeedMap sm = new SeedMap(seed, cur, dim, acc, bs, settings,
                        noise, null, null, null, null, null, null);
                sm.remoteLookup = used;
                return sm;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private HolderLookup.Provider remoteLookup;

    public boolean remote() {
        return gen == null;
    }

    public List<Holder.Reference<Structure>> structureList() {
        try {
            return access.registryOrThrow(Registries.STRUCTURE).holders().toList();
        } catch (Throwable missing) {
            HolderLookup.Provider vr = remoteLookup != null ? remoteLookup : vanilla();
            if (vr == null) {
                return List.of();
            }
            return vr.lookupOrThrow(Registries.STRUCTURE).listElements().toList();
        }
    }

    private static volatile HolderLookup.Provider vanillaLookup;

    private static HolderLookup.Provider vanilla() {
        HolderLookup.Provider v = vanillaLookup;
        if (v == null) {
            try {
                v = VanillaRegistries.createLookup();
                vanillaLookup = v;
            } catch (Throwable ignored) {
            }
        }
        return v;
    }

    private static NoiseGeneratorSettings fallbackSettings(RegistryAccess acc, ResourceKey<Level> dim) {
        ResourceKey<NoiseGeneratorSettings> k = dim == Level.NETHER ? NoiseGeneratorSettings.NETHER
                : dim == Level.END ? NoiseGeneratorSettings.END : NoiseGeneratorSettings.OVERWORLD;
        try {
            Registry<NoiseGeneratorSettings> reg = acc.registryOrThrow(Registries.NOISE_SETTINGS);
            NoiseGeneratorSettings s = reg.get(k);
            return s != null ? s : reg.get(NoiseGeneratorSettings.OVERWORLD);
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean serverBiomes() {
        return gen == null && currentWorld;
    }

    public Holder<Biome> biome(int blockX, int blockZ) {
        if (serverBiomes()) {
            return RemoteBiomes.biomeAt(dim.location(), blockX, blockZ);
        }
        return biomeSource.getNoiseBiome(QuartPos.fromBlock(blockX), seaY, QuartPos.fromBlock(blockZ), tlRandom.get().sampler());
    }

    public int colorAt(int blockX, int blockZ) {
        Holder<Biome> b = biome(blockX, blockZ);
        return b == null ? 0xFF000000 : color(b);
    }

    private Holder<Biome> structureBiome(int blockX, int blockZ, RandomState rs) {
        int y = seaY;
        if (gen != null && levelHeight != null) {
            try {
                y = QuartPos.fromBlock(gen.getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, levelHeight, rs));
            } catch (Throwable ignored) {
            }
        }
        return biomeSource.getNoiseBiome(QuartPos.fromBlock(blockX), y, QuartPos.fromBlock(blockZ), rs.sampler());
    }

    private ChunkGeneratorStructureState state() {
        if (liveState != null) {
            return liveState;
        }
        ChunkGeneratorStructureState s = structState;
        if (s != null || stateTried) {
            return s;
        }
        synchronized (stateLock) {
            if (!stateTried) {
                stateTried = true;
                try {
                    HolderLookup<StructureSet> sets;
                    try {
                        sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
                    } catch (Throwable missing) {
                        HolderLookup.Provider vr = remoteLookup != null ? remoteLookup : vanilla();
                        if (vr == null) {
                            throw missing;
                        }
                        sets = vr.lookupOrThrow(Registries.STRUCTURE_SET);
                    }
                    ChunkGeneratorStructureState created = ChunkGeneratorStructureState.createForNormal(newRandom(), seed, biomeSource, sets);
                    created.ensureStructuresGenerated();
                    structState = created;
                } catch (Throwable ignored) {
                }
            }
            return structState;
        }
    }

    public List<Placed> strongholds() {
        List<Placed> sh = strongholdCache;
        if (sh != null) {
            return sh;
        }
        List<Placed> out = new ArrayList<>();
        ChunkGeneratorStructureState st = state();
        if (st != null) {
            RandomState rs = tlRandom.get();
            for (Holder<StructureSet> setH : st.possibleStructureSets()) {
                StructureSet set = setH.value();
                if (!(set.placement() instanceof ConcentricRingsStructurePlacement crsp) || set.structures().isEmpty()) {
                    continue;
                }
                List<ChunkPos> rings;
                try {
                    rings = st.getRingPositionsFor(crsp);
                } catch (Throwable t) {
                    rings = null;
                }
                if (rings == null) {
                    continue;
                }
                for (ChunkPos cp : rings) {
                    Placed p = selectForChunk(st, set, cp, rs);
                    out.add(p != null ? p
                            : new Placed(set.structures().get(0).structure(), cp.getMiddleBlockX(), cp.getMiddleBlockZ()));
                }
            }
        }
        strongholdCache = out;
        return out;
    }

    public record RegionResult(List<Placed> list, boolean exact) {
    }

    public RegionResult findStructuresRegion(int rx, int rz) {
        if (closed) {
            return new RegionResult(List.of(), false);
        }
        if (currentWorld && serverLevel != null) {
            List<Placed> checked = findViaServer(rx, rz);
            if (checked != null) {
                return new RegionResult(regionDir != null ? mergeDisk(checked, rx, rz) : checked, true);
            }
            List<Placed> predicted = findStructures(rx * 32, rz * 32, rx * 32 + 31, rz * 32 + 31);
            return new RegionResult(regionDir != null ? mergeDisk(predicted, rx, rz) : predicted, false);
        }
        List<Placed> predicted = findStructures(rx * 32, rz * 32, rx * 32 + 31, rz * 32 + 31);
        return new RegionResult(predicted, true);
    }

    private record Candidate(StructureSet set, ChunkPos pos, List<StructureSet.StructureSelectionEntry> ordered) {
    }

    private record Hit(Candidate cand, StructureSet.StructureSelectionEntry entry) {
    }

    private boolean allHidden(StructureSet set) {
        for (StructureSet.StructureSelectionEntry e : set.structures()) {
            ResourceLocation id = e.structure().unwrapKey().map(ResourceKey::location).orElse(null);
            if (id == null || !hiddenFilter.test(id)) {
                return false;
            }
        }
        return true;
    }

    private List<Candidate> candidates(int cx0, int cz0, int cx1, int cz1) {
        List<Candidate> out = new ArrayList<>();
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return out;
        }
        for (Holder<StructureSet> setH : st.possibleStructureSets()) {
            StructureSet set = setH.value();
            StructurePlacement pl = set.placement();
            if (set.structures().isEmpty() || pl instanceof ConcentricRingsStructurePlacement || allHidden(set)) {
                continue;
            }
            if (pl instanceof RandomSpreadStructurePlacement rsp && rsp.spacing() > 0) {
                int spacing = rsp.spacing();
                int rx0 = Math.floorDiv(cx0, spacing);
                int rx1 = Math.floorDiv(cx1, spacing);
                int rz0 = Math.floorDiv(cz0, spacing);
                int rz1 = Math.floorDiv(cz1, spacing);
                for (int rz = rz0; rz <= rz1; rz++) {
                    for (int rx = rx0; rx <= rx1; rx++) {
                        ChunkPos cp = rsp.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                        if (cp.x >= cx0 && cp.x <= cx1 && cp.z >= cz0 && cp.z <= cz1) {
                            out.add(new Candidate(set, cp, orderedEntries(st, set, cp)));
                        }
                    }
                }
            } else {
                for (int cz = cz0; cz <= cz1; cz++) {
                    for (int cx = cx0; cx <= cx1; cx++) {
                        ChunkPos cp = new ChunkPos(cx, cz);
                        out.add(new Candidate(set, cp, orderedEntries(st, set, cp)));
                    }
                }
            }
        }
        return out;
    }

    private List<StructureSet.StructureSelectionEntry> orderedEntries(ChunkGeneratorStructureState st, StructureSet set, ChunkPos cp) {
        List<StructureSet.StructureSelectionEntry> list = set.structures();
        if (list.size() == 1) {
            return list;
        }
        List<StructureSet.StructureSelectionEntry> pool = new ArrayList<>(list);
        List<StructureSet.StructureSelectionEntry> order = new ArrayList<>(list.size());
        WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(0L));
        rnd.setLargeFeatureSeed(st.getLevelSeed(), cp.x, cp.z);
        int total = 0;
        for (StructureSet.StructureSelectionEntry e : pool) {
            total += e.weight();
        }
        while (!pool.isEmpty() && total > 0) {
            int roll = rnd.nextInt(total);
            int idx = 0;
            for (StructureSet.StructureSelectionEntry e : pool) {
                roll -= e.weight();
                if (roll < 0) {
                    break;
                }
                idx++;
            }
            StructureSet.StructureSelectionEntry picked = pool.remove(idx);
            order.add(picked);
            total -= picked.weight();
        }
        return order;
    }

    private record ScanRes(List<Hit> hits, List<Candidate> unknown) {
    }

    private List<Placed> findViaServer(int rx, int rz) {
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return List.of();
        }
        List<Candidate> cands = candidates(rx * 32, rz * 32, rx * 32 + 31, rz * 32 + 31);
        List<Candidate> filtered = new ArrayList<>();
        for (Candidate c : cands) {
            if (safeIsStructureChunk(c.set().placement(), st, c.pos().x, c.pos().z)) {
                filtered.add(c);
            }
        }
        if (filtered.isEmpty() || closed) {
            return List.of();
        }
        ServerLevel sl = serverLevel;
        MinecraftServer srv = sl.getServer();
        if (srv == null) {
            return null;
        }
        if (serverBusy.incrementAndGet() > 2) {
            serverBusy.decrementAndGet();
            return null;
        }
        CompletableFuture<ScanRes> fut = new CompletableFuture<>();
        srv.execute(new Runnable() {
            private int idx;
            private final List<Hit> hits = new ArrayList<>();
            private final List<Candidate> unknown = new ArrayList<>();

            @Override
            public void run() {
                try {
                    if (closed) {
                        fut.complete(null);
                        return;
                    }
                    StructureManager sm = sl.structureManager();
                    long t0 = System.nanoTime();
                    while (idx < filtered.size()) {
                        if (System.nanoTime() - t0 > 8_000_000L) {
                            srv.execute(this);
                            return;
                        }
                        Candidate c = filtered.get(idx++);
                        for (StructureSet.StructureSelectionEntry e : c.ordered()) {
                            StructureCheckResult r = sm.checkStructurePresence(c.pos(), e.structure().value(), false);
                            if (r == StructureCheckResult.START_PRESENT) {
                                hits.add(new Hit(c, e));
                                break;
                            }
                            if (r == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                                unknown.add(c);
                                break;
                            }
                        }
                    }
                    fut.complete(new ScanRes(hits, unknown));
                } catch (Throwable t) {
                    fut.completeExceptionally(t);
                }
            }
        });
        ScanRes res;
        try {
            res = fut.get(20L, TimeUnit.SECONDS);
        } catch (Throwable t) {
            return null;
        } finally {
            serverBusy.decrementAndGet();
        }
        if (res == null) {
            return List.of();
        }
        List<Placed> out = new ArrayList<>();
        for (Hit hit : res.hits()) {
            ChunkPos cp = hit.cand().pos();
            out.add(new Placed(hit.entry().structure(), cp.getMiddleBlockX(), cp.getMiddleBlockZ()));
        }
        RandomState rs = tlRandom.get();
        for (Candidate c : res.unknown()) {
            if (closed) {
                break;
            }
            for (StructureSet.StructureSelectionEntry e : c.ordered()) {
                Placed p = validateFast(e, c.pos(), rs);
                if (p != null) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    private Placed validateFast(StructureSet.StructureSelectionEntry entry, ChunkPos cp, RandomState rs) {
        Structure s = entry.structure().value();
        if (s.biomes().size() == 0) {
            return null;
        }
        int bx = cp.getMiddleBlockX();
        int bz = cp.getMiddleBlockZ();
        if (s.biomes().contains(structureBiome(bx, bz, rs))) {
            return new Placed(entry.structure(), bx, bz);
        }
        return null;
    }

    private List<Placed> mergeDisk(List<Placed> predicted, int rx, int rz) {
        Path f = regionDir.resolve("r." + rx + "." + rz + ".mca");
        if (!Files.isRegularFile(f)) {
            return predicted;
        }
        Registry<Structure> reg;
        try {
            reg = access.registryOrThrow(Registries.STRUCTURE);
        } catch (Throwable t) {
            return predicted;
        }
        Set<Long> known = new HashSet<>();
        List<Placed> disk = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) {
            if (raf.length() < 4096L) {
                return predicted;
            }
            byte[] header = new byte[4096];
            raf.readFully(header);
            for (int i = 0; i < 1024; i++) {
                if (closed) {
                    return predicted;
                }
                int v = ((header[i * 4] & 0xFF) << 24) | ((header[i * 4 + 1] & 0xFF) << 16)
                        | ((header[i * 4 + 2] & 0xFF) << 8) | (header[i * 4 + 3] & 0xFF);
                int off = v >>> 8;
                if (off == 0) {
                    continue;
                }
                try {
                    raf.seek((long) off * 4096L);
                    int len = raf.readInt();
                    if (len <= 1 || len > 8 * 1024 * 1024) {
                        continue;
                    }
                    int comp = raf.readByte() & 0xFF;
                    byte[] data = new byte[len - 1];
                    raf.readFully(data);
                    InputStream in;
                    if (comp == 1) {
                        in = new GZIPInputStream(new ByteArrayInputStream(data));
                    } else if (comp == 2) {
                        in = new InflaterInputStream(new ByteArrayInputStream(data));
                    } else if (comp == 3) {
                        in = new ByteArrayInputStream(data);
                    } else {
                        continue;
                    }
                    CompoundTag tag;
                    try (DataInputStream din = new DataInputStream(new BufferedInputStream(in))) {
                        tag = NbtIo.read(din);
                    }
                    if (!tag.contains("structures")) {
                        continue;
                    }
                    int cx = rx * 32 + (i & 31);
                    int cz = rz * 32 + (i >> 5);
                    known.add(ChunkPos.asLong(cx, cz));
                    CompoundTag starts = tag.getCompound("structures").getCompound("starts");
                    for (String key : starts.getAllKeys()) {
                        CompoundTag st = starts.getCompound(key);
                        if ("INVALID".equals(st.getString("id"))) {
                            continue;
                        }
                        ResourceLocation sid = ResourceLocation.tryParse(key);
                        if (sid == null) {
                            continue;
                        }
                        Optional<Holder.Reference<Structure>> holder =
                                reg.getHolder(ResourceKey.create(Registries.STRUCTURE, sid));
                        if (holder.isEmpty()) {
                            continue;
                        }
                        disk.add(new Placed(holder.get(), cx * 16 + 8, cz * 16 + 8));
                    }
                } catch (Throwable perChunk) {
                }
            }
        } catch (Throwable t) {
            return predicted;
        }
        List<Placed> out = new ArrayList<>(disk);
        for (Placed p : predicted) {
            if (!known.contains(ChunkPos.asLong(p.x() >> 4, p.z() >> 4))) {
                out.add(p);
            }
        }
        return out;
    }

    public List<Placed> findStructures(int cx0, int cz0, int cx1, int cz1) {
        List<Placed> out = new ArrayList<>();
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return out;
        }
        RandomState rs = tlRandom.get();
        for (Holder<StructureSet> setH : st.possibleStructureSets()) {
            if (closed) {
                return out;
            }
            StructureSet set = setH.value();
            StructurePlacement pl = set.placement();
            if (set.structures().isEmpty() || pl instanceof ConcentricRingsStructurePlacement || allHidden(set)) {
                continue;
            }
            if (pl instanceof RandomSpreadStructurePlacement rsp && rsp.spacing() > 0) {
                int spacing = rsp.spacing();
                int rx0 = Math.floorDiv(cx0, spacing);
                int rx1 = Math.floorDiv(cx1, spacing);
                int rz0 = Math.floorDiv(cz0, spacing);
                int rz1 = Math.floorDiv(cz1, spacing);
                for (int rz = rz0; rz <= rz1; rz++) {
                    for (int rx = rx0; rx <= rx1; rx++) {
                        ChunkPos cp = rsp.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                        if (cp.x < cx0 || cp.x > cx1 || cp.z < cz0 || cp.z > cz1
                                || !safeIsStructureChunk(pl, st, cp.x, cp.z)) {
                            continue;
                        }
                        Placed p = selectForChunk(st, set, cp, rs);
                        if (p != null) {
                            out.add(p);
                        }
                    }
                }
            } else {
                for (int cz = cz0; cz <= cz1; cz++) {
                    if (closed) {
                        return out;
                    }
                    for (int cx = cx0; cx <= cx1; cx++) {
                        if (!safeIsStructureChunk(pl, st, cx, cz)) {
                            continue;
                        }
                        Placed p = selectForChunk(st, set, new ChunkPos(cx, cz), rs);
                        if (p != null) {
                            out.add(p);
                        }
                    }
                }
            }
        }
        return out;
    }

    private boolean safeIsStructureChunk(StructurePlacement pl, ChunkGeneratorStructureState st, int cx, int cz) {
        try {
            return pl.isStructureChunk(st, cx, cz);
        } catch (Throwable t) {
            return false;
        }
    }

    private Placed selectForChunk(ChunkGeneratorStructureState st, StructureSet set, ChunkPos cp, RandomState rs) {
        List<StructureSet.StructureSelectionEntry> list = set.structures();
        if (list.size() == 1) {
            return validate(list.get(0), cp, rs);
        }
        List<StructureSet.StructureSelectionEntry> pool = new ArrayList<>(list);
        WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(0L));
        rnd.setLargeFeatureSeed(st.getLevelSeed(), cp.x, cp.z);
        int total = 0;
        for (StructureSet.StructureSelectionEntry e : pool) {
            total += e.weight();
        }
        while (!pool.isEmpty() && total > 0) {
            int roll = rnd.nextInt(total);
            int idx = 0;
            for (StructureSet.StructureSelectionEntry e : pool) {
                roll -= e.weight();
                if (roll < 0) {
                    break;
                }
                idx++;
            }
            StructureSet.StructureSelectionEntry picked = pool.get(idx);
            Placed p = validate(picked, cp, rs);
            if (p != null) {
                return p;
            }
            pool.remove(idx);
            total -= picked.weight();
        }
        return null;
    }

    private Placed validate(StructureSet.StructureSelectionEntry entry, ChunkPos cp, RandomState rs) {
        Structure s = entry.structure().value();
        if (gen != null && levelHeight != null && templates != null) {
            try {
                Structure.GenerationContext ctx = new Structure.GenerationContext(
                        access, gen, biomeSource, rs, templates, seed, cp, levelHeight, s.biomes()::contains);
                Optional<Structure.GenerationStub> stub = s.findValidGenerationPoint(ctx);
                if (stub.isPresent()) {
                    return new Placed(entry.structure(), cp.getMiddleBlockX(), cp.getMiddleBlockZ());
                }
                return null;
            } catch (Throwable ignored) {
            }
        }
        int bx = cp.getMiddleBlockX();
        int bz = cp.getMiddleBlockZ();
        if (s.biomes().contains(structureBiome(bx, bz, rs))) {
            return new Placed(entry.structure(), bx, bz);
        }
        return null;
    }

    public BlockPos locateStructure(Holder<Structure> target, BlockPos center) {
        if (currentWorld && serverLevel != null && serverLevel.getServer() != null) {
            return locateBatched(target, center);
        }
        return predictStructureNear(target, center);
    }

    public boolean canGenerateHere(Holder<Structure> target) {
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return false;
        }
        for (Holder<StructureSet> setH : st.possibleStructureSets()) {
            for (StructureSet.StructureSelectionEntry e : setH.value().structures()) {
                if (e.structure().value() == target.value()) {
                    return true;
                }
            }
        }
        return false;
    }

    private record CandPair(StructureSet set, ChunkPos pos) {
    }

    private List<BlockPos> checkCandidatesOnServer(ChunkGeneratorStructureState st, List<CandPair> cand, Holder<Structure> target) {
        if (cand.isEmpty()) {
            return List.of();
        }
        ServerLevel sl = serverLevel;
        CompletableFuture<List<BlockPos>> fut = new CompletableFuture<>();
        sl.getServer().execute(() -> {
            try {
                List<BlockPos> hits = new ArrayList<>();
                StructureManager sm = sl.structureManager();
                for (CandPair c : cand) {
                    if (closed) {
                        break;
                    }
                    if (!c.set().placement().isStructureChunk(st, c.pos().x, c.pos().z)) {
                        continue;
                    }
                    for (StructureSet.StructureSelectionEntry e : orderedEntries(st, c.set(), c.pos())) {
                        StructureCheckResult r = sm.checkStructurePresence(c.pos(), e.structure().value(), false);
                        if (r == StructureCheckResult.START_NOT_PRESENT) {
                            continue;
                        }
                        if (e.structure().value() == target.value()) {
                            hits.add(new BlockPos(c.pos().getMiddleBlockX(), 64, c.pos().getMiddleBlockZ()));
                        }
                        break;
                    }
                }
                fut.complete(hits);
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        try {
            return fut.get(15L, TimeUnit.SECONDS);
        } catch (Throwable t) {
            return null;
        }
    }

    private BlockPos locateBatched(Holder<Structure> target, BlockPos center) {
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return null;
        }
        List<StructureSet> spreadSets = new ArrayList<>();
        List<StructureSet> ringSets = new ArrayList<>();
        for (Holder<StructureSet> setH : st.possibleStructureSets()) {
            StructureSet set = setH.value();
            boolean has = false;
            for (StructureSet.StructureSelectionEntry e : set.structures()) {
                if (e.structure().value() == target.value()) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                continue;
            }
            if (set.placement() instanceof RandomSpreadStructurePlacement rsp && rsp.spacing() > 0) {
                spreadSets.add(set);
            } else if (set.placement() instanceof ConcentricRingsStructurePlacement) {
                ringSets.add(set);
            }
        }
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (StructureSet set : ringSets) {
            List<ChunkPos> ring;
            try {
                ring = st.getRingPositionsFor((ConcentricRingsStructurePlacement) set.placement());
            } catch (Throwable t) {
                ring = null;
            }
            if (ring == null) {
                continue;
            }
            List<CandPair> cand = new ArrayList<>();
            for (ChunkPos cp : ring) {
                cand.add(new CandPair(set, cp));
            }
            List<BlockPos> hits = checkCandidatesOnServer(st, cand, target);
            if (hits == null) {
                return best;
            }
            for (BlockPos h : hits) {
                long d = dist2(h, center);
                if (d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
        }
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        for (int ring = 0; ring <= 128 && best == null; ring++) {
            List<CandPair> cand = new ArrayList<>();
            for (StructureSet set : spreadSets) {
                RandomSpreadStructurePlacement rsp = (RandomSpreadStructurePlacement) set.placement();
                int spacing = rsp.spacing();
                int crx = Math.floorDiv(ccx, spacing);
                int crz = Math.floorDiv(ccz, spacing);
                for (int dz = -ring; dz <= ring; dz++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        ChunkPos cp = rsp.getPotentialStructureChunk(seed, (crx + dx) * spacing, (crz + dz) * spacing);
                        cand.add(new CandPair(set, cp));
                    }
                }
            }
            List<BlockPos> hits = checkCandidatesOnServer(st, cand, target);
            if (hits == null) {
                return null;
            }
            for (BlockPos h : hits) {
                long d = dist2(h, center);
                if (d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
        }
        return best;
    }

    private static long dist2(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static final Field F_FREQ;
    private static final Field F_SALT;

    static {
        Field freq = null;
        Field salt = null;
        try {
            for (Field f : StructurePlacement.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (f.getType() == float.class && freq == null) {
                    f.setAccessible(true);
                    freq = f;
                } else if (f.getType() == int.class && salt == null) {
                    f.setAccessible(true);
                    salt = f;
                }
            }
        } catch (Throwable ignored) {
        }
        F_FREQ = freq;
        F_SALT = salt;
    }

    public List<String> structureInfo(Holder<Structure> target) {
        List<String> out = new ArrayList<>();
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return out;
        }
        for (Holder<StructureSet> setH : st.possibleStructureSets()) {
            StructureSet set = setH.value();
            boolean has = false;
            for (StructureSet.StructureSelectionEntry e : set.structures()) {
                if (e.structure().value() == target.value()) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                continue;
            }
            out.add("§6" + setH.unwrapKey().map(k -> k.location().toString()).orElse("?")
                    + (set.structures().size() > 1 ? " §8(" + set.structures().size() + ")" : ""));
            StructurePlacement pl = set.placement();
            if (pl instanceof RandomSpreadStructurePlacement rsp) {
                String extra = "";
                try {
                    if (F_FREQ != null) {
                        float fr = F_FREQ.getFloat(pl);
                        if (fr < 1.0F) {
                            extra += " · freq " + fr;
                        }
                    }
                    if (F_SALT != null) {
                        extra += " · salt " + F_SALT.getInt(pl);
                    }
                } catch (Throwable ignored) {
                }
                out.add("§7spacing " + rsp.spacing() + " · sep " + rsp.separation() + extra);
            } else if (pl instanceof ConcentricRingsStructurePlacement) {
                out.add("§7concentric rings");
            } else {
                out.add("§7" + pl.getClass().getSimpleName());
            }
        }
        return out;
    }

    private volatile Map<String, Integer> diskCounts;
    private volatile boolean diskCountsRunning;

    public Map<String, Integer> diskCountsNow() {
        return diskCounts;
    }

    public void ensureDiskCounts(ExecutorService pool) {
        if (diskCounts != null || diskCountsRunning || regionDir == null || !currentWorld) {
            return;
        }
        diskCountsRunning = true;
        pool.submit(() -> {
            Map<String, Integer> m = new HashMap<>();
            try {
                for (Path f : Files.list(regionDir).toList()) {
                    String fn = f.getFileName().toString();
                    if (!fn.endsWith(".mca")) {
                        continue;
                    }
                    try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) {
                        if (raf.length() < 4096L) {
                            continue;
                        }
                        byte[] header = new byte[4096];
                        raf.readFully(header);
                        for (int i = 0; i < 1024; i++) {
                            int v = ((header[i * 4] & 0xFF) << 24) | ((header[i * 4 + 1] & 0xFF) << 16)
                                    | ((header[i * 4 + 2] & 0xFF) << 8) | (header[i * 4 + 3] & 0xFF);
                            int off = v >>> 8;
                            if (off == 0) {
                                continue;
                            }
                            try {
                                raf.seek((long) off * 4096L);
                                int len = raf.readInt();
                                if (len <= 1 || len > 8 * 1024 * 1024) {
                                    continue;
                                }
                                int comp = raf.readByte() & 0xFF;
                                byte[] data = new byte[len - 1];
                                raf.readFully(data);
                                InputStream in;
                                if (comp == 1) {
                                    in = new GZIPInputStream(new ByteArrayInputStream(data));
                                } else if (comp == 2) {
                                    in = new InflaterInputStream(new ByteArrayInputStream(data));
                                } else if (comp == 3) {
                                    in = new ByteArrayInputStream(data);
                                } else {
                                    continue;
                                }
                                CompoundTag tag;
                                try (DataInputStream din = new DataInputStream(new BufferedInputStream(in))) {
                                    tag = NbtIo.read(din);
                                }
                                CompoundTag starts = tag.getCompound("structures").getCompound("starts");
                                for (String key : starts.getAllKeys()) {
                                    if (!"INVALID".equals(starts.getCompound(key).getString("id"))) {
                                        m.merge(key, 1, Integer::sum);
                                    }
                                }
                            } catch (Throwable perChunk) {
                            }
                        }
                    } catch (Throwable perFile) {
                    }
                }
            } catch (Throwable ignored) {
            }
            diskCounts = m;
        });
    }

    private BlockPos predictStructureNear(Holder<Structure> target, BlockPos center) {
        ChunkGeneratorStructureState st = state();
        if (st == null) {
            return null;
        }
        RandomState rs = tlRandom.get();
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (Holder<StructureSet> setH : st.possibleStructureSets()) {
            StructureSet set = setH.value();
            boolean has = false;
            for (StructureSet.StructureSelectionEntry e : set.structures()) {
                if (e.structure().value() == target.value()) {
                    has = true;
                    break;
                }
            }
            if (!has || !(set.placement() instanceof RandomSpreadStructurePlacement rsp) || rsp.spacing() <= 0) {
                continue;
            }
            int spacing = rsp.spacing();
            int crx = Math.floorDiv(ccx, spacing);
            int crz = Math.floorDiv(ccz, spacing);
            BlockPos setBest = null;
            long setBestD = Long.MAX_VALUE;
            for (int r = 0; r <= 100 && setBest == null; r++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        ChunkPos cp = rsp.getPotentialStructureChunk(seed, (crx + dx) * spacing, (crz + dz) * spacing);
                        if (!safeIsStructureChunk(set.placement(), st, cp.x, cp.z)) {
                            continue;
                        }
                        for (StructureSet.StructureSelectionEntry e : orderedEntries(st, set, cp)) {
                            Placed p = validate(e, cp, rs);
                            if (p == null) {
                                continue;
                            }
                            if (e.structure().value() == target.value()) {
                                long d = (long) (p.x() - center.getX()) * (p.x() - center.getX())
                                        + (long) (p.z() - center.getZ()) * (p.z() - center.getZ());
                                if (d < setBestD) {
                                    setBestD = d;
                                    setBest = new BlockPos(p.x(), 64, p.z());
                                }
                            }
                            break;
                        }
                    }
                }
            }
            if (setBest != null && setBestD < bestD) {
                bestD = setBestD;
                best = setBest;
            }
        }
        return best;
    }

    public BlockPos locateBiome(Holder<Biome> target, BlockPos center) {
        if (serverBiomes()) {
            return RemoteBiomes.findNearest(dim.location(), target, center.getX(), center.getZ());
        }
        if (currentWorld && serverLevel != null) {
            ServerLevel sl = serverLevel;
            MinecraftServer srv = sl.getServer();
            if (srv != null) {
                CompletableFuture<BlockPos> fut = new CompletableFuture<>();
                srv.execute(() -> {
                    try {
                        Pair<BlockPos, Holder<Biome>> p =
                                sl.findClosestBiome3d(h -> h.value() == target.value(), center, 6400, 32, 64);
                        fut.complete(p == null ? null : p.getFirst());
                    } catch (Throwable t) {
                        fut.complete(null);
                    }
                });
                try {
                    return fut.get(45L, TimeUnit.SECONDS);
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        RandomState rs = tlRandom.get();
        for (int r = 0; r <= 6400; r += 32) {
            for (int dz = -r; dz <= r; dz += 32) {
                for (int dx = -r; dx <= r; dx += 32) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) + 31 < r) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    Holder<Biome> b = biomeSource.getNoiseBiome(QuartPos.fromBlock(x), seaY, QuartPos.fromBlock(z), rs.sampler());
                    if (b.value() == target.value()) {
                        return new BlockPos(x, 64, z);
                    }
                }
            }
        }
        return null;
    }

    public int color(Holder<Biome> b) {
        Integer c = colorCache.get(b);
        if (c != null) {
            return c;
        }
        int col = computeColor(b);
        colorCache.put(b, col);
        return col;
    }

    public static String name(Holder<Biome> b) {
        return b.unwrapKey().map(k -> k.location().getPath().replace('_', ' ')).orElse("?");
    }

    private int computeColor(Holder<Biome> b) {
        ResourceLocation rl = b.unwrapKey().map(ResourceKey::location).orElse(null);
        if (rl != null) {
            Integer cur = CURATED.get(rl.toString());
            if (cur != null) {
                return 0xFF000000 | cur;
            }
        }
        int grass;
        try {
            grass = b.value().getGrassColor(0.0, 0.0);
        } catch (Throwable t) {
            grass = GrassColor.get(0.5, 0.5);
        }
        return 0xFF000000 | grass;
    }

    private static final Map<String, Integer> CURATED = new HashMap<>();

    static {
        for (String s : new String[]{"ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean",
                "lukewarm_ocean", "deep_lukewarm_ocean", "frozen_ocean", "deep_frozen_ocean"}) {
            CURATED.put("minecraft:" + s, 0x2C5DA6);
        }
        CURATED.put("minecraft:warm_ocean", 0x2090B0);
        CURATED.put("minecraft:river", 0x3F76C4);
        CURATED.put("minecraft:frozen_river", 0x8FB7D6);
        CURATED.put("minecraft:beach", 0xE3DBA8);
        CURATED.put("minecraft:snowy_beach", 0xDDE6E0);
        CURATED.put("minecraft:stony_shore", 0x9090A0);
        CURATED.put("minecraft:plains", 0x8DB360);
        CURATED.put("minecraft:sunflower_plains", 0xA6C266);
        CURATED.put("minecraft:meadow", 0x83B266);
        CURATED.put("minecraft:forest", 0x3E7A2E);
        CURATED.put("minecraft:flower_forest", 0x6AAE4A);
        CURATED.put("minecraft:birch_forest", 0x5E8C40);
        CURATED.put("minecraft:old_growth_birch_forest", 0x6E9C50);
        CURATED.put("minecraft:dark_forest", 0x2E4A22);
        CURATED.put("minecraft:taiga", 0x46684A);
        CURATED.put("minecraft:snowy_taiga", 0xBCD0CC);
        CURATED.put("minecraft:old_growth_pine_taiga", 0x3E5C42);
        CURATED.put("minecraft:old_growth_spruce_taiga", 0x46644A);
        CURATED.put("minecraft:jungle", 0x2E8E1C);
        CURATED.put("minecraft:sparse_jungle", 0x4E9E3C);
        CURATED.put("minecraft:bamboo_jungle", 0x5EA838);
        CURATED.put("minecraft:swamp", 0x4C6B3A);
        CURATED.put("minecraft:mangrove_swamp", 0x3E6B4A);
        CURATED.put("minecraft:desert", 0xD9C36B);
        CURATED.put("minecraft:savanna", 0xBFB755);
        CURATED.put("minecraft:savanna_plateau", 0xC2BA68);
        CURATED.put("minecraft:windswept_savanna", 0xB0A858);
        CURATED.put("minecraft:badlands", 0xC06A2B);
        CURATED.put("minecraft:eroded_badlands", 0xCC7836);
        CURATED.put("minecraft:wooded_badlands", 0xB07840);
        CURATED.put("minecraft:snowy_plains", 0xE6EDF2);
        CURATED.put("minecraft:ice_spikes", 0xD0E4F0);
        CURATED.put("minecraft:snowy_slopes", 0xD6E0E8);
        CURATED.put("minecraft:grove", 0xBCC8CC);
        CURATED.put("minecraft:frozen_peaks", 0xC4D4E0);
        CURATED.put("minecraft:jagged_peaks", 0xCEDAE2);
        CURATED.put("minecraft:stony_peaks", 0x9A8E7C);
        CURATED.put("minecraft:windswept_hills", 0x8A9080);
        CURATED.put("minecraft:windswept_gravelly_hills", 0x9A9888);
        CURATED.put("minecraft:windswept_forest", 0x5A7A56);
        CURATED.put("minecraft:mushroom_fields", 0x9A6FB0);
        CURATED.put("minecraft:cherry_grove", 0xE8A0C0);
        CURATED.put("minecraft:the_void", 0x101018);
        CURATED.put("minecraft:nether_wastes", 0x6B2218);
        CURATED.put("minecraft:soul_sand_valley", 0x4A3A30);
        CURATED.put("minecraft:crimson_forest", 0x8A1E1E);
        CURATED.put("minecraft:warped_forest", 0x1E6A6A);
        CURATED.put("minecraft:basalt_deltas", 0x3A3438);
        CURATED.put("minecraft:the_end", 0xD8D8A0);
        CURATED.put("minecraft:end_highlands", 0xD0D098);
        CURATED.put("minecraft:end_midlands", 0xC8C890);
        CURATED.put("minecraft:end_barrens", 0xB8B880);
        CURATED.put("minecraft:small_end_islands", 0x9090C0);
    }
}