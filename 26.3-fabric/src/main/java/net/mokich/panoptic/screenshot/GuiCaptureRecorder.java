package net.mokich.panoptic.screenshot;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class GuiCaptureRecorder {
    private static final List<GrabOp> OPS = new ArrayList<>();
    private static volatile boolean armed;

    private GuiCaptureRecorder() {}

    public static void arm() {
        OPS.clear();
        armed = true;
    }

    public static boolean isArmed() {
        return armed;
    }

    public static void mergeSpots(List<HoverProbe.Spot> spots) {
        if (!armed) {
            return;
        }
        List<float[]> taken = new ArrayList<>();
        for (GrabOp op : OPS) {
            if ("i".equals(op.t)) {
                taken.add(new float[]{op.x1, op.y1, op.scale, op.scale});
            }
        }
        List<HoverProbe.Spot> ordered = new ArrayList<>(spots);
        ordered.sort((a, b) -> Long.compare(b.time(), a.time()));
        Set<ItemStack> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (HoverProbe.Spot spot : ordered) {
            if (!cap()) {
                return;
            }
            if (!used.add(spot.stack()) || overlaps(taken, spot)) {
                continue;
            }
            taken.add(new float[]{spot.x(), spot.y(), spot.w(), spot.h()});
            OPS.add(GrabOp.item(spot.stack(), spot.x(), spot.y(), Math.max(spot.w(), spot.h())));
        }
    }

    private static boolean overlaps(List<float[]> taken, HoverProbe.Spot spot) {
        float area = Math.max(spot.w() * spot.h(), 1.0F);
        for (float[] r : taken) {
            float w = Math.min(r[0] + r[2], spot.x() + spot.w()) - Math.max(r[0], spot.x());
            float h = Math.min(r[1] + r[3], spot.y() + spot.h()) - Math.max(r[1], spot.y());
            if (w > 0.0F && h > 0.0F && w * h > area * 0.4F) {
                return true;
            }
        }
        return false;
    }

    public static List<GrabOp> disarmAndDrain() {
        armed = false;
        List<GrabOp> out = new ArrayList<>(OPS);
        OPS.clear();
        return out;
    }

    private static boolean cap() {
        return armed && OPS.size() < 16384;
    }

    private static Vector2f p(Matrix3x2f m, float x, float y) {
        return m.transformPosition(new Vector2f(x, y));
    }

    public static void blit(Matrix3x2f m, Identifier tex, float x1, float x2, float y1, float y2,
                            float minU, float maxU, float minV, float maxV) {
        if (!cap() || tex == null) {
            return;
        }
        Vector2f a = p(m, x1, y1);
        Vector2f b = p(m, x2, y2);
        OPS.add(GrabOp.blit(tex.toString(), a.x(), a.y(), b.x(), b.y(), minU, maxU, minV, maxV));
    }

    public static void fill(Matrix3x2f m, float x1, float y1, float x2, float y2, int color) {
        if (!cap()) {
            return;
        }
        Vector2f a = p(m, x1, y1);
        Vector2f b = p(m, x2, y2);
        OPS.add(GrabOp.fill(a.x(), a.y(), b.x(), b.y(), color));
    }

    public static void gradient(Matrix3x2f m, float x1, float y1, float x2, float y2, int top, int bottom) {
        if (!cap()) {
            return;
        }
        Vector2f a = p(m, x1, y1);
        Vector2f b = p(m, x2, y2);
        OPS.add(GrabOp.gradient(a.x(), a.y(), b.x(), b.y(), top, bottom));
    }

    public static void text(Matrix3x2f m, String text, float x, float y, int color, boolean shadow) {
        if (!cap() || text == null || text.isEmpty()) {
            return;
        }
        Vector2f a = p(m, x, y);
        OPS.add(GrabOp.text(text, a.x(), a.y(), m.m00(), color, shadow));
    }

    public static void itemAbs(ItemStack stack, Matrix3x2f m) {
        if (!cap() || stack == null || stack.isEmpty()) {
            return;
        }
        float sx = Math.abs(m.m00());
        float sy = Math.abs(m.m11());
        if (sx < 1.0F || sy < 1.0F) {
            return;
        }
        OPS.add(GrabOp.item(stack, m.m20() - sx / 2.0F, m.m21() - sy / 2.0F, Math.max(sx, sy)));
    }

    public static void itemAt(ItemStack stack, Matrix3x2f m, int x, int y) {
        if (!cap() || stack == null || stack.isEmpty()) {
            return;
        }
        Vector2f a = p(m, x, y);
        float scale = Math.abs(m.m00());
        OPS.add(GrabOp.item(stack, a.x(), a.y(), 16.0F * scale));
    }

    public static void entity(Matrix3x2f m, int x, int y, int size, float angleX, float angleY, String type) {
        if (!cap() || type == null) {
            return;
        }
        Vector2f a = p(m, x, y);
        OPS.add(GrabOp.entity(type, a.x(), a.y(), size * m.m00(), angleX, angleY));
    }

    public static void scissor(int x1, int y1, int x2, int y2) {
        if (!cap()) {
            return;
        }
        OPS.add(GrabOp.scissor(x1, y1, x2, y2));
    }

    public static void unscissor() {
        if (!cap()) {
            return;
        }
        OPS.add(GrabOp.unscissor());
    }
}