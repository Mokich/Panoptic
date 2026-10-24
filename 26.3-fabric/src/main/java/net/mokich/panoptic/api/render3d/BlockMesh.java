package net.mokich.panoptic.api.render3d;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockMesh {
    public record Face(float[] xyz, float[] uv, Identifier atlas, int shade, boolean tinted, int tintIndex,
                       Direction cull) {}

    private static final Map<BlockState, BlockMesh> CACHE = new HashMap<>();
    private static Object cacheKey;

    private final List<Face> faces;

    private BlockMesh(List<Face> faces) {
        this.faces = faces;
    }

    public List<Face> faces() {
        return faces;
    }

    public boolean isEmpty() {
        return faces.isEmpty();
    }

    public static synchronized BlockMesh of(BlockState state) {
        Object key = Minecraft.getInstance().getModelManager();
        if (key != cacheKey) {
            CACHE.clear();
            cacheKey = key;
        }
        BlockMesh cached = CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        BlockMesh built = build(state);
        CACHE.put(state, built);
        return built;
    }

    public static synchronized void invalidate() {
        CACHE.clear();
        cacheKey = null;
    }

    private static BlockMesh build(BlockState state) {
        List<Face> out = new ArrayList<>();
        try {
            BlockStateModel model = Minecraft.getInstance().getModelManager()
                    .getBlockStateModelSet().get(state);
            if (model != null) {
                List<BlockStateModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(42L), parts);
                for (BlockStateModelPart part : parts) {
                    collect(part, null, out);
                    for (Direction d : Direction.values()) {
                        collect(part, d, out);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new BlockMesh(List.copyOf(out));
    }

    private static void collect(BlockStateModelPart part, Direction cull, List<Face> out) {
        List<BakedQuad> quads;
        try {
            quads = part.getQuads(cull);
        } catch (Throwable t) {
            return;
        }
        if (quads == null) {
            return;
        }
        for (BakedQuad q : quads) {
            Face f = convert(q, cull);
            if (f != null) {
                out.add(f);
            }
        }
    }

    private static Face convert(BakedQuad q, Direction cull) {
        try {
            float[] xyz = new float[12];
            float[] uv = new float[8];
            for (int i = 0; i < 4; i++) {
                Vector3fc p = q.position(i);
                xyz[i * 3] = p.x();
                xyz[i * 3 + 1] = p.y();
                xyz[i * 3 + 2] = p.z();
                long packed = q.packedUV(i);
                uv[i * 2] = UVPair.unpackU(packed);
                uv[i * 2 + 1] = UVPair.unpackV(packed);
            }
            BakedQuad.MaterialInfo info = q.materialInfo();
            Identifier atlas = info.sprite().atlasLocation();
            return new Face(xyz, uv, atlas, shadeOf(q.direction()), info.isTinted(), info.tintIndex(), cull);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int shadeOf(Direction dir) {
        if (dir == null) {
            return 255;
        }
        return switch (dir) {
            case DOWN -> 128;
            case UP -> 255;
            case NORTH, SOUTH -> 204;
            case EAST, WEST -> 153;
        };
    }
}