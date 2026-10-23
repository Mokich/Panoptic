package net.mokich.panoptic.screenshot;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GrabReplay {
    private static final Map<String, LivingEntity> PUPPETS = new HashMap<>();

    private GrabReplay() {
    }

    public static ItemStack replay(GuiGraphics g, List<GrabOp> ops, double offX, double offY, double zoom,
                                   Font font, int mouseX, int mouseY, int[] hoverRect) {
        return replay(g, ops, offX, offY, zoom, font, mouseX, mouseY, hoverRect, true, false);
    }

    public static ItemStack replay(GuiGraphics g, List<GrabOp> ops, double offX, double offY, double zoom,
                                   Font font, int mouseX, int mouseY, int[] hoverRect, boolean entities) {
        return replay(g, ops, offX, offY, zoom, font, mouseX, mouseY, hoverRect, entities, false);
    }

    public static ItemStack replay(GuiGraphics g, List<GrabOp> ops, double offX, double offY, double zoom,
                                   Font font, int mouseX, int mouseY, int[] hoverRect, boolean entities, boolean rasterOnly) {
        ItemStack hovered = ItemStack.EMPTY;
        int scissorDepth = 0;
        g.pose().pushPose();
        g.pose().translate(offX, offY, 0.0);
        g.pose().scale((float) zoom, (float) zoom, 1.0F);
        try {
            for (GrabOp o : ops) {
                if (rasterOnly && !"i".equals(o.t) && !"p".equals(o.t) && !"r".equals(o.t)) {
                    continue;
                }
                try {
                    switch (o.t) {
                        case "b" -> blitUV(g, ResourceLocation.tryParse(o.tex), o.x1, o.y1, o.x2, o.y2, o.minU, o.maxU, o.minV, o.maxV);
                        case "f" -> g.fill((int) o.x1, (int) o.y1, (int) o.x2, (int) o.y2, o.c1);
                        case "g" -> g.fillGradient((int) o.x1, (int) o.y1, (int) o.x2, (int) o.y2, o.c1, o.c2);
                        case "s" -> {
                            g.pose().pushPose();
                            try {
                                g.pose().translate(o.x1, o.y1, 0.0F);
                                if (o.scale > 0.0F && o.scale != 1.0F) {
                                    g.pose().scale(o.scale, o.scale, 1.0F);
                                }
                                g.drawString(font, o.text == null ? "" : o.text, 0, 0, o.c1, o.shadow);
                            } finally {
                                g.pose().popPose();
                            }
                        }
                        case "i" -> {
                            ItemStack st = o.stack();
                            if (!st.isEmpty()) {
                                if (!rasterOnly) {
                                    g.pose().pushPose();
                                    try {
                                        g.pose().translate(o.x1, o.y1, 0.0F);
                                        float s = o.scale / 16.0F;
                                        g.pose().scale(s, s, 1.0F);
                                        g.renderItem(st, 0, 0);
                                        g.renderItemDecorations(font, st, 0, 0);
                                    } finally {
                                        g.pose().popPose();
                                    }
                                }
                                int rx1 = (int) Math.round(offX + o.x1 * zoom);
                                int ry1 = (int) Math.round(offY + o.y1 * zoom);
                                int rx2 = (int) Math.round(offX + (o.x1 + o.scale) * zoom);
                                int ry2 = (int) Math.round(offY + (o.y1 + o.scale) * zoom);
                                if (mouseX >= rx1 && mouseX < rx2 && mouseY >= ry1 && mouseY < ry2) {
                                    hovered = st;
                                    if (hoverRect != null) {
                                        hoverRect[0] = rx1;
                                        hoverRect[1] = ry1;
                                        hoverRect[2] = rx2;
                                        hoverRect[3] = ry2;
                                    }
                                }
                            }
                        }
                        case "c" -> {
                            int sx1 = (int) Math.round(offX + o.x1 * zoom);
                            int sy1 = (int) Math.round(offY + o.y1 * zoom);
                            int sx2 = (int) Math.round(offX + o.x2 * zoom);
                            int sy2 = (int) Math.round(offY + o.y2 * zoom);
                            g.enableScissor(sx1, sy1, sx2, sy2);
                            scissorDepth++;
                        }
                        case "u" -> {
                            if (scissorDepth > 0) {
                                g.disableScissor();
                                scissorDepth--;
                            }
                        }
                        case "r", "p" -> {
                            g.pose().pushPose();
                            g.pose().translate(0.0F, 0.0F, 280.0F);
                            drawAnno(g, o);
                            g.pose().popPose();
                        }
                        case "e" -> {
                            LivingEntity ent = entities ? resolveEntity(o.tex) : null;
                            if (ent != null) {
                                int s = Math.max(1, (int) o.scale);
                                int cx = (int) o.x1;
                                int cy = (int) o.y1;
                                InventoryScreen.renderEntityInInventoryFollowsMouse(
                                        g, cx - s, cy - s * 2, cx + s, cy + s, s, 0.0625F,
                                        cx - o.minU, cy - o.minV, ent);
                            }
                        }
                        default -> {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } finally {
            while (scissorDepth-- > 0) {
                try {
                    g.disableScissor();
                } catch (Throwable ignored) {
                }
            }
            g.pose().popPose();
        }
        return hovered;
    }

    public static void drawAnno(GuiGraphics g, GrabOp o) {
        int th = Math.max(1, Math.round(o.scale <= 0 ? 2 : o.scale));
        if ("r".equals(o.t)) {
            int ax = (int) Math.min(o.x1, o.x2);
            int ay = (int) Math.min(o.y1, o.y2);
            int bx = (int) Math.max(o.x1, o.x2);
            int by = (int) Math.max(o.y1, o.y2);
            g.fill(ax, ay, bx, ay + th, o.c1);
            g.fill(ax, by - th, bx, by, o.c1);
            g.fill(ax, ay, ax + th, by, o.c1);
            g.fill(bx - th, ay, bx, by, o.c1);
        } else if ("p".equals(o.t) && o.pts != null && o.pts.length >= 4) {
            for (int i = 0; i + 3 < o.pts.length; i += 2) {
                stampLine(g, o.pts[i], o.pts[i + 1], o.pts[i + 2], o.pts[i + 3], th, o.c1);
            }
        }
    }

    public static void stampLine(GuiGraphics g, float ax, float ay, float bx, float by, int th, int color) {
        float dx = bx - ax;
        float dy = by - ay;
        float dist = Math.max(Math.abs(dx), Math.abs(dy));
        int n = Math.max(1, (int) (dist * 2));
        int half = th / 2;
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            int px = Math.round(ax + dx * f);
            int py = Math.round(ay + dy * f);
            g.fill(px - half, py - half, px - half + th, py - half + th, color);
        }
    }

    private static LivingEntity resolveEntity(String type) {
        Minecraft mc = Minecraft.getInstance();
        if (type == null || "self".equals(type)) {
            return mc.player;
        }
        if (mc.level == null) {
            return null;
        }
        if (PUPPETS.containsKey(type)) {
            return PUPPETS.get(type);
        }
        LivingEntity result = null;
        try {
            EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(type));
            Entity e = et == null ? null : et.create(mc.level);
            if (e instanceof LivingEntity le) {
                result = le;
            }
        } catch (Throwable ignored) {
        }
        PUPPETS.put(type, result);
        return result;
    }

    public static void blitUV(GuiGraphics g, ResourceLocation tex, float x1, float y1, float x2, float y2,
                              float u1, float u2, float v1, float v2) {
        if (tex == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f mat = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.addVertex(mat, x1, y1, 0.0F).setUv(u1, v1);
        buf.addVertex(mat, x1, y2, 0.0F).setUv(u1, v2);
        buf.addVertex(mat, x2, y2, 0.0F).setUv(u2, v2);
        buf.addVertex(mat, x2, y1, 0.0F).setUv(u2, v1);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }
}