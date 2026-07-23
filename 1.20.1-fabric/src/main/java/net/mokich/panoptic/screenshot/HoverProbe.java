package net.mokich.panoptic.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

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

    public static void item(ItemStack stack, Matrix4f m) {
        if (!tracking || stack == null || stack.isEmpty() || STACKS.size() >= 8192
                || Minecraft.getInstance().screen == null) {
            return;
        }
        float sx = Math.abs(m.m00());
        float sy = Math.abs(m.m11());
        if (sx < 1.0F || sy < 1.0F) {
            return;
        }
        STACKS.add(stack);
        RECTS.add(new float[]{m.m30() - sx / 2.0F, m.m31() - sy / 2.0F, Math.max(sx, sy)});
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