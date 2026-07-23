package net.mokich.panoptic.screenshot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

public final class GrabOp {

    private static HolderLookup.Provider regs() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess();
    }
    public String t;
    public float x1;
    public float y1;
    public float x2;
    public float y2;
    public float minU;
    public float maxU;
    public float minV;
    public float maxV;
    public String tex;
    public int c1;
    public int c2;
    public String text;
    public float scale;
    public boolean shadow;
    public float[] pts;
    public String data;
    public String nbt;

    public transient ItemStack stack;

    public ItemStack stack() {
        if (stack == null) {
            stack = ItemStack.EMPTY;
            if (text != null && !text.isEmpty()) {
                try {
                    CompoundTag tag = TagParser.parseTag(text);
                    HolderLookup.Provider regs = regs();
                    if (regs != null) {
                        stack = ItemStack.parseOptional(regs, tag);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return stack;
    }

    public static GrabOp blit(String tex, float x1, float y1, float x2, float y2,
                              float minU, float maxU, float minV, float maxV) {
        GrabOp o = new GrabOp();
        o.t = "b";
        o.tex = tex;
        o.x1 = x1;
        o.y1 = y1;
        o.x2 = x2;
        o.y2 = y2;
        o.minU = minU;
        o.maxU = maxU;
        o.minV = minV;
        o.maxV = maxV;
        return o;
    }

    public static GrabOp fill(float x1, float y1, float x2, float y2, int color) {
        GrabOp o = new GrabOp();
        o.t = "f";
        o.x1 = x1;
        o.y1 = y1;
        o.x2 = x2;
        o.y2 = y2;
        o.c1 = color;
        return o;
    }

    public static GrabOp gradient(float x1, float y1, float x2, float y2, int top, int bottom) {
        GrabOp o = new GrabOp();
        o.t = "g";
        o.x1 = x1;
        o.y1 = y1;
        o.x2 = x2;
        o.y2 = y2;
        o.c1 = top;
        o.c2 = bottom;
        return o;
    }

    public static GrabOp text(String text, float x, float y, float scale, int color, boolean shadow) {
        GrabOp o = new GrabOp();
        o.t = "s";
        o.text = text;
        o.x1 = x;
        o.y1 = y;
        o.scale = scale;
        o.c1 = color;
        o.shadow = shadow;
        return o;
    }

    public static GrabOp item(ItemStack stack, float x, float y, float size) {
        GrabOp o = new GrabOp();
        o.t = "i";
        HolderLookup.Provider regs = regs();
        o.text = regs == null ? "" : stack.save(regs).toString();
        o.stack = stack.copy();
        o.x1 = x;
        o.y1 = y;
        o.scale = size;
        return o;
    }

    public static GrabOp entity(String type, float x, float y, float scale, float angleX, float angleY) {
        GrabOp o = new GrabOp();
        o.t = "e";
        o.tex = type;
        o.x1 = x;
        o.y1 = y;
        o.scale = scale;
        o.minU = angleX;
        o.minV = angleY;
        return o;
    }

    public static GrabOp worldZone(String type, ItemStack stack, float x1, float y1, float x2, float y2, String data) {
        GrabOp o = new GrabOp();
        o.t = type;
        o.x1 = x1;
        o.y1 = y1;
        o.x2 = x2;
        o.y2 = y2;
        o.data = data;
        if (stack != null && !stack.isEmpty()) {
            HolderLookup.Provider regs = regs();
            o.text = regs == null ? "" : stack.save(regs).toString();
            o.stack = stack.copy();
        }
        return o;
    }

    public static GrabOp scissor(float x1, float y1, float x2, float y2) {
        GrabOp o = new GrabOp();
        o.t = "c";
        o.x1 = x1;
        o.y1 = y1;
        o.x2 = x2;
        o.y2 = y2;
        return o;
    }

    public static GrabOp unscissor() {
        GrabOp o = new GrabOp();
        o.t = "u";
        return o;
    }
}