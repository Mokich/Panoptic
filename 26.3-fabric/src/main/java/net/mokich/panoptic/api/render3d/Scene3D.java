package net.mokich.panoptic.api.render3d;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.mokich.panoptic.mixin.GuiRenderStateAccessor;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

public final class Scene3D {
    private static final class Placed {
        final BlockState state;
        final int x;
        final int y;
        final int z;

        Placed(BlockState state, int x, int y, int z) {
            this.state = state;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Batch {
        Identifier atlas;
        float[] pos;
        float[] uv;
        int[] color;
        int[] cullIdx;
        int faces;
        float[] xy;
        long[] order;
        boolean[] flip;

        Batch(Identifier atlas, int cap) {
            this.atlas = atlas;
            this.pos = new float[cap * 12];
            this.uv = new float[cap * 8];
            this.color = new int[cap];
            this.cullIdx = new int[cap];
        }

        void grow(int need) {
            if (faces + need <= color.length) {
                return;
            }
            int cap = Math.max(color.length * 2, faces + need);
            float[] p = new float[cap * 12];
            System.arraycopy(pos, 0, p, 0, faces * 12);
            pos = p;
            float[] u = new float[cap * 8];
            System.arraycopy(uv, 0, u, 0, faces * 8);
            uv = u;
            int[] c = new int[cap];
            System.arraycopy(color, 0, c, 0, faces);
            color = c;
            int[] ci = new int[cap];
            System.arraycopy(cullIdx, 0, ci, 0, faces);
            cullIdx = ci;
        }

        void ensureScratch() {
            if (xy == null || xy.length < faces * 8) {
                xy = new float[faces * 8];
                order = new long[faces];
                flip = new boolean[faces];
            }
        }
    }

    private final List<Placed> blocks = new ArrayList<>();
    private List<Batch> batches;
    private float yaw = 0.7853982F;
    private float pitch = 0.5235988F;
    private float scale = 16.0F;
    private float ox;
    private float oy;
    private float oz;

    public Scene3D add(BlockState state, int x, int y, int z) {
        if (state != null && !state.isAir()) {
            blocks.add(new Placed(state, x, y, z));
            batches = null;
        }
        return this;
    }

    public Scene3D angles(float yawRad, float pitchRad) {
        this.yaw = yawRad;
        this.pitch = pitchRad;
        return this;
    }

    public Scene3D scale(float px) {
        this.scale = px;
        return this;
    }

    public Scene3D origin(float x, float y, float z) {
        this.ox = x;
        this.oy = y;
        this.oz = z;
        return this;
    }

    public Scene3D centerOn(float sx, float sy, float sz) {
        return origin(sx * 0.5F, sy * 0.5F, sz * 0.5F);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int faceCount() {
        compile();
        int n = 0;
        for (Batch b : batches) {
            n += b.faces;
        }
        return n;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private void compile() {
        if (batches != null) {
            return;
        }
        Set<Long> solid = new HashSet<>();
        for (Placed p : blocks) {
            try {
                if (p.state.isSolidRender()) {
                    solid.add(key(p.x, p.y, p.z));
                }
            } catch (Throwable ignored) {
            }
        }
        Map<Identifier, Batch> map = new LinkedHashMap<>();
        for (Placed p : blocks) {
            BlockMesh mesh = BlockMesh.of(p.state);
            if (mesh.isEmpty()) {
                continue;
            }
            for (BlockMesh.Face f : mesh.faces()) {
                Direction c = f.cull();
                if (c != null && solid.contains(key(p.x + c.getStepX(), p.y + c.getStepY(), p.z + c.getStepZ()))) {
                    continue;
                }
                Batch b = map.computeIfAbsent(f.atlas(), a -> new Batch(a, 256));
                b.grow(1);
                int fi = b.faces;
                for (int i = 0; i < 4; i++) {
                    b.pos[fi * 12 + i * 3] = p.x + f.xyz()[i * 3];
                    b.pos[fi * 12 + i * 3 + 1] = p.y + f.xyz()[i * 3 + 1];
                    b.pos[fi * 12 + i * 3 + 2] = p.z + f.xyz()[i * 3 + 2];
                    b.uv[fi * 8 + i * 2] = f.uv()[i * 2];
                    b.uv[fi * 8 + i * 2 + 1] = f.uv()[i * 2 + 1];
                }
                b.color[fi] = colorOf(f, p.state);
                b.cullIdx[fi] = c == null ? -1 : c.ordinal();
                b.faces++;
            }
        }
        batches = new ArrayList<>(map.values());
    }

    public float fitScale(float boxW, float boxH) {
        Matrix4f m = matrix(1.0F);
        float minx = Float.MAX_VALUE;
        float maxx = -Float.MAX_VALUE;
        float miny = Float.MAX_VALUE;
        float maxy = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (Placed p : blocks) {
            for (int c = 0; c < 8; c++) {
                v.set(p.x + (c & 1), p.y + ((c >> 1) & 1), p.z + ((c >> 2) & 1));
                m.transformPosition(v);
                minx = Math.min(minx, v.x);
                maxx = Math.max(maxx, v.x);
                miny = Math.min(miny, v.y);
                maxy = Math.max(maxy, v.y);
            }
        }
        float w = maxx - minx;
        float h = maxy - miny;
        if (w <= 0.0001F || h <= 0.0001F) {
            return scale;
        }
        return Math.min(boxW / w, boxH / h);
    }

    private Matrix4f matrix(float s) {
        return new Matrix4f()
                .scale(s, -s, s)
                .rotateX(pitch)
                .rotateY(yaw)
                .translate(-ox, -oy, -oz);
    }

    private static int sortKey(float f) {
        int bits = Float.floatToIntBits(f);
        return bits ^ ((bits >> 31) & 0x7FFFFFFF);
    }

    public void render(GuiGraphicsExtractor g, float cx, float cy) {
        compile();
        if (batches.isEmpty()) {
            return;
        }
        Matrix4f m = matrix(scale);
        Vector3f v = new Vector3f();
        Direction[] dirs = Direction.values();
        float[] facing = new float[dirs.length];
        for (int d = 0; d < dirs.length; d++) {
            v.set(dirs[d].getStepX(), dirs[d].getStepY(), dirs[d].getStepZ());
            m.transformDirection(v);
            facing[d] = v.z;
        }
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        GuiRenderStateAccessor acc = (GuiRenderStateAccessor) g;
        for (Batch b : batches) {
            if (b.faces == 0) {
                continue;
            }
            b.ensureScratch();
            float minx = Float.MAX_VALUE;
            float maxx = -Float.MAX_VALUE;
            float miny = Float.MAX_VALUE;
            float maxy = -Float.MAX_VALUE;
            int kept = 0;
            for (int f = 0; f < b.faces; f++) {
                int ci = b.cullIdx[f];
                if (ci >= 0 && facing[ci] <= 0.0F) {
                    continue;
                }
                float dsum = 0.0F;
                float area = 0.0F;
                int po = f * 12;
                int xo = f * 8;
                for (int i = 0; i < 4; i++) {
                    v.set(b.pos[po + i * 3], b.pos[po + i * 3 + 1], b.pos[po + i * 3 + 2]);
                    m.transformPosition(v);
                    float sx = cx + v.x;
                    float sy = cy + v.y;
                    dsum += v.z;
                    b.xy[xo + i * 2] = sx;
                    b.xy[xo + i * 2 + 1] = sy;
                    if (sx < minx) {
                        minx = sx;
                    }
                    if (sx > maxx) {
                        maxx = sx;
                    }
                    if (sy < miny) {
                        miny = sy;
                    }
                    if (sy > maxy) {
                        maxy = sy;
                    }
                }
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) & 3;
                    area += b.xy[xo + i * 2] * b.xy[xo + j * 2 + 1]
                            - b.xy[xo + j * 2] * b.xy[xo + i * 2 + 1];
                }
                b.flip[f] = area > 0.0F;
                b.order[kept++] = ((long) sortKey(dsum * 0.25F) << 32) | (f & 0xFFFFFFFFL);
            }
            if (kept == 0) {
                continue;
            }
            Arrays.sort(b.order, 0, kept);
            ScreenRectangle bounds = new ScreenRectangle(Mth.floor(minx) - 1, Mth.floor(miny) - 1,
                    Mth.ceil(maxx - minx) + 2, Mth.ceil(maxy - miny) + 2).transformMaxBounds(pose);
            acc.panoptic$guiRenderState().addGuiElement(
                    new BatchEl(pose, b.xy, b.uv, b.color, b.order, b.flip, kept, b.atlas, bounds));
        }
    }

    private static int colorOf(BlockMesh.Face f, BlockState state) {
        int base = f.tinted() ? tintOf(state, f.tintIndex()) : 0xFFFFFF;
        int s = f.shade();
        int r = ((base >> 16) & 0xFF) * s / 255;
        int gg = ((base >> 8) & 0xFF) * s / 255;
        int b = (base & 0xFF) * s / 255;
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private static int tintOf(BlockState state, int tintIndex) {
        try {
            BlockTintSource src = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
            if (src == null) {
                return 0xFFFFFF;
            }
            int c = src.color(state);
            return c == -1 ? 0xFFFFFF : (c & 0xFFFFFF);
        } catch (Throwable t) {
            return 0xFFFFFF;
        }
    }

    private record BatchEl(Matrix3x2f pose, float[] xy, float[] uv, int[] color, long[] order,
                           boolean[] flip, int faces, Identifier atlas, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < faces; i++) {
                int f = (int) (order[i] & 0xFFFFFFFFL);
                int c = color[f];
                int xo = f * 8;
                boolean fl = flip[f];
                for (int v = 0; v < 4; v++) {
                    int k = fl ? (4 - v) & 3 : v;
                    vc.addVertexWith2DPose(pose, xy[xo + k * 2], xy[xo + k * 2 + 1])
                            .setUv(uv[xo + k * 2], uv[xo + k * 2 + 1])
                            .setColor(c);
                }
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public TextureSetup textureSetup() {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(atlas);
            return TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler());
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }
    }
}