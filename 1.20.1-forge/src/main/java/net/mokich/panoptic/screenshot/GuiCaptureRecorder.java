package net.mokich.panoptic.screenshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

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

    public static List<GrabOp> disarmAndDrain() {
        armed = false;
        List<GrabOp> out = new ArrayList<>(OPS);
        OPS.clear();
        return out;
    }

    private static boolean cap() {
        return armed && OPS.size() < 16384;
    }

    private static Vector3f p(Matrix4f m, float x, float y) {
        return m.transformPosition(new Vector3f(x, y, 0.0F));
    }

    public static void blit(Matrix4f m, ResourceLocation tex, float x1, float x2, float y1, float y2,
                            float minU, float maxU, float minV, float maxV) {
        if (!cap() || tex == null) {
            return;
        }
        Vector3f a = p(m, x1, y1);
        Vector3f b = p(m, x2, y2);
        OPS.add(GrabOp.blit(tex.toString(), a.x(), a.y(), b.x(), b.y(), minU, maxU, minV, maxV));
    }

    public static void fill(Matrix4f m, float x1, float y1, float x2, float y2, int color) {
        if (!cap()) {
            return;
        }
        Vector3f a = p(m, x1, y1);
        Vector3f b = p(m, x2, y2);
        OPS.add(GrabOp.fill(a.x(), a.y(), b.x(), b.y(), color));
    }

    public static void gradient(Matrix4f m, float x1, float y1, float x2, float y2, int top, int bottom) {
        if (!cap()) {
            return;
        }
        Vector3f a = p(m, x1, y1);
        Vector3f b = p(m, x2, y2);
        OPS.add(GrabOp.gradient(a.x(), a.y(), b.x(), b.y(), top, bottom));
    }

    public static void text(Matrix4f m, String text, float x, float y, int color, boolean shadow) {
        if (!cap() || text == null || text.isEmpty()) {
            return;
        }
        Vector3f a = p(m, x, y);
        OPS.add(GrabOp.text(text, a.x(), a.y(), m.m00(), color, shadow));
    }

    public static void itemAbs(ItemStack stack, Matrix4f m) {
        if (!cap() || stack == null || stack.isEmpty()) {
            return;
        }
        float sx = Math.abs(m.m00());
        float sy = Math.abs(m.m11());
        if (sx < 1.0F || sy < 1.0F) {
            return;
        }
        OPS.add(GrabOp.item(stack, m.m30() - sx / 2.0F, m.m31() - sy / 2.0F, Math.max(sx, sy)));
    }

    public static void entity(Matrix4f m, int x, int y, int size, float angleX, float angleY, String type) {
        if (!cap() || type == null) {
            return;
        }
        Vector3f a = p(m, x, y);
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