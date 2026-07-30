package net.mokich.panoptic.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public final class HoverProbe {
    private static final List<ItemStack> STACKS = new ArrayList<>();
    private static final List<float[]> RECTS = new ArrayList<>();
    private static volatile boolean tracking;

    private HoverProbe() {}

    public static void newFrame() {
        tracking = true;
        STACKS.clear();
        RECTS.clear();
    }

    public static boolean tracking() {
        return tracking;
    }

    public static void itemAt(ItemStack stack, Matrix3x2f m, int x, int y) {
        if (!tracking || stack == null || stack.isEmpty() || STACKS.size() >= 8192
                || Minecraft.getInstance().screen == null) {
            return;
        }
        float scale = Math.abs(m.m00());
        if (scale < 1.0F) {
            return;
        }
        Vector2f a = m.transformPosition(new Vector2f(x, y));
        STACKS.add(stack);
        RECTS.add(new float[]{a.x(), a.y(), 16.0F * scale});
    }

    public static ItemStack at(double mx, double my) {
        for (int i = STACKS.size() - 1; i >= 0; i--) {
            float[] r = RECTS.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[2]) {
                return STACKS.get(i);
            }
        }
        return ItemStack.EMPTY;
    }
}