package net.mokich.panoptic.screenshot;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ItemQuad {
    private static final float MIN_SIZE = 0.5F;

    private ItemQuad() {
    }

    public static float[] of(Matrix4f m) {
        Vector3f a = m.transformPosition(new Vector3f(-0.5F, -0.5F, 0.0F));
        Vector3f b = m.transformPosition(new Vector3f(0.5F, 0.5F, 0.0F));
        float x = Math.min(a.x(), b.x());
        float y = Math.min(a.y(), b.y());
        float w = Math.abs(b.x() - a.x());
        float h = Math.abs(b.y() - a.y());
        if (!Float.isFinite(x) || !Float.isFinite(y) || w < MIN_SIZE || h < MIN_SIZE) {
            return null;
        }
        return new float[]{x, y, w, h};
    }
}