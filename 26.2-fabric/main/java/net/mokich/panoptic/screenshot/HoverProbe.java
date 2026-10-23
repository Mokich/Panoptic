package net.mokich.panoptic.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HoverProbe {
    public record Spot(ItemStack stack, float x, float y, float w, float h, long time) {}

    private static final int MAX_SPOTS = 8192;

    private static final Map<Long, Spot> SPOTS = new LinkedHashMap<>();

    private static Screen owner;
    private static long stamp;

    private HoverProbe() {}

    public static void newFrame() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != owner) {
            owner = screen;
            SPOTS.clear();
        }
        stamp++;
    }

    public static boolean tracking() {
        return Minecraft.getInstance().screen != null;
    }

    public static void item(ItemStack stack, Matrix4f m) {
        if (stack == null || stack.isEmpty() || Minecraft.getInstance().screen == null) {
            return;
        }
        float[] rect = ItemQuad.of(m);
        if (rect == null) {
            return;
        }
        long key = key(rect[0], rect[1]);
        if (SPOTS.size() >= MAX_SPOTS && !SPOTS.containsKey(key)) {
            return;
        }
        SPOTS.put(key, new Spot(stack, rect[0], rect[1], rect[2], rect[3], stamp));
    }

    public static ItemStack at(double mx, double my) {
        Spot best = null;
        for (Spot spot : SPOTS.values()) {
            if (mx < spot.x() || mx >= spot.x() + spot.w() || my < spot.y() || my >= spot.y() + spot.h()) {
                continue;
            }
            if (best == null || spot.time() >= best.time()) {
                best = spot;
            }
        }
        return best == null ? ItemStack.EMPTY : best.stack();
    }

    public static List<Spot> spots() {
        return new ArrayList<>(SPOTS.values());
    }

    public static long key(float x, float y) {
        return ((long) Math.round(x) << 32) ^ (Math.round(y) & 0xFFFFFFFFL);
    }
}