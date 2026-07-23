package net.mokich.panoptic.screen.screengrab;

import net.mokich.panoptic.Guard;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.AnnoBar;
import net.mokich.panoptic.config.ModBinds;

import com.mojang.blaze3d.platform.NativeImage;
import net.mokich.panoptic.screenshot.GrabOp;
import net.mokich.panoptic.screenshot.GrabReplay;
import net.mokich.panoptic.screenshot.ScreenGrab;
import net.mokich.panoptic.screenshot.ScreenGrabStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RegionSelectScreen extends Screen {
    private final List<GrabOp> ops;
    private final String screenTitle;
    private NativeImage shot;
    private DynamicTexture previewTex;
    private ResourceLocation previewRL;

    private boolean editing;
    private boolean done;
    private double rawX;
    private double rawY;
    private double selL;
    private double selT;
    private double selR;
    private double selB;

    private final AnnoBar bar = new AnnoBar();
    private final List<GrabOp> annos = new ArrayList<>();
    private final List<Float> curPts = new ArrayList<>();
    private boolean drawingStroke;
    private double rectX;
    private double rectY;
    private boolean drawingRect;
    private boolean selectingRegion;

    private int dragHandle = -1;

    public RegionSelectScreen(List<GrabOp> ops, String screenTitle, NativeImage shot) {
        super(Component.translatable("panoptic.grab.select"));
        this.ops = ops;
        this.screenTitle = screenTitle;
        this.shot = shot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        try {
            renderSafe(g, mouseX, mouseY, partial);
        } catch (Throwable t) {
            Guard.report(t);
            onClose();
        }
    }

    private void ensurePreview() {
        if (shot != null && previewRL == null) {
            previewTex = new DynamicTexture(shot);
            previewRL = this.minecraft.getTextureManager().register("gmt_preview", previewTex);
        }
    }

    private void renderSafe(GuiGraphics g, int mouseX, int mouseY, float partial) {
        ensurePreview();
        if (previewRL != null) {
            GrabReplay.blitUV(g, previewRL, 0, 0, this.width, this.height, 0, 1, 0, 1);
        } else {
            GrabReplay.replay(g, ops, 0, 0, 1.0, this.font, -1, -1, null);
        }

        int x0 = (int) Math.min(selL, selR);
        int y0 = (int) Math.min(selT, selB);
        int x1 = (int) Math.max(selL, selR);
        int y1 = (int) Math.max(selT, selB);
        int dim = 0xC8000000;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        boolean hasSel = editing || x1 > x0 && y1 > y0;
        if (hasSel) {
            g.fill(0, 0, this.width, y0, dim);
            g.fill(0, y1, this.width, this.height, dim);
            g.fill(0, y0, x0, y1, dim);
            g.fill(x1, y0, this.width, y1, dim);
            GuiStyle.rect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, GuiStyle.ACCENT);
            String d = (x1 - x0) + " x " + (y1 - y0);
            g.drawString(this.font, d, x0, Math.max(1, y0 - 10), GuiStyle.ACCENT);
        } else {
            g.fill(0, 0, this.width, this.height, dim);
        }

        for (GrabOp a : annos) {
            GrabReplay.drawAnno(g, a);
        }
        drawInProgress(g, mouseX, mouseY);

        if (editing) {
            drawHandles(g, x0, y0, x1, y1);
            bar.render(g, this.font, this.width / 2, 8, mouseX, mouseY, !annos.isEmpty());
            if (bar.hoverTip != null) {
                g.renderTooltip(this.font, Component.literal(bar.hoverTip), mouseX, mouseY);
            }
        }
        g.pose().popPose();
    }

    private void drawInProgress(GuiGraphics g, int mouseX, int mouseY) {
        int c = bar.color();
        if (drawingStroke && curPts.size() >= 4) {
            for (int i = 0; i + 3 < curPts.size(); i += 2) {
                GrabReplay.stampLine(g, curPts.get(i), curPts.get(i + 1), curPts.get(i + 2), curPts.get(i + 3), bar.thickness, c);
            }
        }
        if (drawingRect) {
            int ax = (int) Math.min(rectX, mouseX);
            int ay = (int) Math.min(rectY, mouseY);
            int bx = (int) Math.max(rectX, mouseX);
            int by = (int) Math.max(rectY, mouseY);
            int th = bar.thickness;
            g.fill(ax, ay, bx, ay + th, c);
            g.fill(ax, by - th, bx, by, c);
            g.fill(ax, ay, ax + th, by, c);
            g.fill(bx - th, ay, bx, by, c);
        }
    }

    private void drawHandles(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int mx = (x0 + x1) / 2;
        int my = (y0 + y1) / 2;
        int[][] hs = {{x0, y0}, {mx, y0}, {x1, y0}, {x1, my}, {x1, y1}, {mx, y1}, {x0, y1}, {x0, my}};
        for (int[] h : hs) {
            g.fill(h[0] - 3, h[1] - 3, h[0] + 3, h[1] + 3, 0xFF141109);
            g.fill(h[0] - 2, h[1] - 2, h[0] + 2, h[1] + 2, GuiStyle.ACCENT);
        }
    }

    private int hitHandle(double mx, double my) {
        int x0 = (int) Math.min(selL, selR);
        int y0 = (int) Math.min(selT, selB);
        int x1 = (int) Math.max(selL, selR);
        int y1 = (int) Math.max(selT, selB);
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;
        int[][] hs = {{x0, y0}, {cx, y0}, {x1, y0}, {x1, cy}, {x1, y1}, {cx, y1}, {x0, y1}, {x0, cy}};
        for (int i = 0; i < hs.length; i++) {
            if (Math.abs(mx - hs[i][0]) <= 5 && Math.abs(my - hs[i][1]) <= 5) {
                return i;
            }
        }
        return -1;
    }

    private boolean inSelection(double mx, double my) {
        return mx >= Math.min(selL, selR) && mx <= Math.max(selL, selR)
                && my >= Math.min(selT, selB) && my <= Math.max(selT, selB);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (editing) {
            if (bar.pickerMouseDown(mx, my)) {
                return true;
            }
            int a = bar.click(mx, my);
            if (a >= 0) {
                toolClick(a);
                return true;
            }
            if (button == 0) {
                int handle = hitHandle(mx, my);
                if (handle >= 0) {
                    dragHandle = handle;
                    return true;
                }
                if (bar.tool == AnnoBar.ACT_CURSOR) {
                    if (inSelection(mx, my)) {
                        dragHandle = 8;
                    }
                    return true;
                }
                if (bar.tool == AnnoBar.ACT_PEN) {
                    drawingStroke = true;
                    curPts.clear();
                    curPts.add((float) clampX(mx));
                    curPts.add((float) clampY(my));
                    return true;
                }
                if (bar.tool == AnnoBar.ACT_BOX) {
                    drawingRect = true;
                    rectX = clampX(mx);
                    rectY = clampY(my);
                    return true;
                }
            }
            return true;
        }
        if (button == 0) {
            rawX = mx;
            rawY = my;
            selL = mx;
            selT = my;
            selR = mx;
            selB = my;
            selectingRegion = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!editing && selectingRegion) {
            selL = Math.min(rawX, mx);
            selT = Math.min(rawY, my);
            selR = Math.max(rawX, mx);
            selB = Math.max(rawY, my);
            return true;
        }
        if (editing) {
            if (bar.pickerMouseDrag(mx, my)) {
                return true;
            }
            if (dragHandle >= 0) {
                resize(dragHandle, dx, dy);
                return true;
            }
            if (bar.tool == AnnoBar.ACT_PEN && drawingStroke) {
                curPts.add((float) clampX(mx));
                curPts.add((float) clampY(my));
                return true;
            }
            if (bar.tool == AnnoBar.ACT_BOX && drawingRect) {
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!editing && selectingRegion && button == 0) {
            selectingRegion = false;
            if (Math.abs(selR - selL) > 4 && Math.abs(selB - selT) > 4) {
                editing = true;
            }
            return true;
        }
        if (editing && button == 0) {
            bar.pickerMouseUp();
            if (dragHandle >= 0) {
                dragHandle = -1;
            } else if (bar.tool == AnnoBar.ACT_PEN && drawingStroke) {
                drawingStroke = false;
                if (curPts.size() >= 4) {
                    GrabOp a = new GrabOp();
                    a.t = "p";
                    a.c1 = bar.color();
                    a.scale = bar.thickness;
                    float[] arr = new float[curPts.size()];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = curPts.get(i);
                    }
                    a.pts = arr;
                    annos.add(a);
                }
                curPts.clear();
            } else if (bar.tool == AnnoBar.ACT_BOX && drawingRect) {
                drawingRect = false;
                double bx = clampX(mx);
                double by = clampY(my);
                if (Math.abs(bx - rectX) > 3 && Math.abs(by - rectY) > 3) {
                    GrabOp a = new GrabOp();
                    a.t = "r";
                    a.x1 = (float) rectX;
                    a.y1 = (float) rectY;
                    a.x2 = (float) bx;
                    a.y2 = (float) by;
                    a.c1 = bar.color();
                    a.scale = bar.thickness;
                    annos.add(a);
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void resize(int handle, double dx, double dy) {
        boolean l = handle == 0 || handle == 6 || handle == 7;
        boolean r = handle == 2 || handle == 3 || handle == 4;
        boolean t = handle == 0 || handle == 1 || handle == 2;
        boolean b = handle == 4 || handle == 5 || handle == 6;
        double left = Math.min(selL, selR);
        double top = Math.min(selT, selB);
        double right = Math.max(selL, selR);
        double bottom = Math.max(selT, selB);
        if (handle == 8) {
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
        } else {
            if (l) {
                left = Math.min(right - 8, left + dx);
            }
            if (r) {
                right = Math.max(left + 8, right + dx);
            }
            if (t) {
                top = Math.min(bottom - 8, top + dy);
            }
            if (b) {
                bottom = Math.max(top + 8, bottom + dy);
            }
        }
        selL = left;
        selT = top;
        selR = right;
        selB = bottom;
    }

    private double clampX(double x) {
        return Math.max(Math.min(selL, selR), Math.min(Math.max(selL, selR), x));
    }

    private double clampY(double y) {
        return Math.max(Math.min(selT, selB), Math.min(Math.max(selT, selB), y));
    }

    private void toolClick(int id) {
        switch (id) {
            case AnnoBar.ACT_CURSOR, AnnoBar.ACT_PEN, AnnoBar.ACT_BOX -> bar.tool = id;
            case AnnoBar.ACT_COLOR -> bar.togglePicker();
            case AnnoBar.ACT_UNDO -> {
                if (!annos.isEmpty()) {
                    annos.remove(annos.size() - 1);
                }
            }
            case AnnoBar.ACT_DONE -> capture();
            case AnnoBar.ACT_CANCEL -> onClose();
            default -> {
            }
        }
    }

    private void capture() {
        if (done) {
            return;
        }
        done = true;
        int x0 = (int) Math.min(selL, selR);
        int y0 = (int) Math.min(selT, selB);
        int x1 = (int) Math.max(selL, selR);
        int y1 = (int) Math.max(selT, selB);

        ScreenGrab grab = new ScreenGrab();
        grab.id = System.currentTimeMillis();
        grab.source = "gui";
        grab.screenTitle = screenTitle;
        grab.regionW = x1 - x0;
        grab.regionH = y1 - y0;
        grab.ops = new ArrayList<>();
        for (GrabOp o : ops) {
            if ("u".equals(o.t) || "c".equals(o.t) || intersects(o, x0, y0, x1, y1)) {
                grab.ops.add(translate(o, x0, y0));
            }
        }
        for (GrabOp a : annos) {
            grab.ops.add(translateAnno(a, x0, y0));
        }
        saveBg(grab, x0, y0, x1, y1);

        ScreenGrabStore.add(grab);
        this.minecraft.setScreen(new ScreenInspectorScreen(grab));
    }

    private void saveBg(ScreenGrab grab, int x0, int y0, int x1, int y1) {
        if (shot == null) {
            return;
        }
        try {
            double gs = this.minecraft.getWindow().getGuiScale();
            int px = Math.max(0, (int) Math.round(x0 * gs));
            int py = Math.max(0, (int) Math.round(y0 * gs));
            int pw = Math.min(shot.getWidth() - px, (int) Math.round((x1 - x0) * gs));
            int ph = Math.min(shot.getHeight() - py, (int) Math.round((y1 - y0) * gs));
            if (pw <= 0 || ph <= 0) {
                return;
            }
            NativeImage sub = new NativeImage(pw, ph, false);
            for (int yy = 0; yy < ph; yy++) {
                for (int xx = 0; xx < pw; xx++) {
                    sub.setPixelRGBA(xx, yy, shot.getPixelRGBA(px + xx, py + yy));
                }
            }
            Path dir = ScreenGrabStore.grabsDir();
            Files.createDirectories(dir);
            sub.writeToFile(dir.resolve(grab.id + ".png").toFile());
            sub.close();
            grab.bg = grab.id + ".png";
            grab.bgW = pw;
            grab.bgH = ph;
        } catch (Throwable ignored) {
        }
    }

    private boolean intersects(GrabOp o, int x0, int y0, int x1, int y1) {
        float ax1;
        float ay1;
        float ax2;
        float ay2;
        switch (o.t) {
            case "i" -> {
                ax1 = o.x1;
                ay1 = o.y1;
                ax2 = o.x1 + o.scale;
                ay2 = o.y1 + o.scale;
            }
            case "s" -> {
                float w = this.font.width(o.text == null ? "" : o.text) * Math.max(1.0F, o.scale);
                ax1 = o.x1;
                ay1 = o.y1;
                ax2 = o.x1 + w;
                ay2 = o.y1 + 9.0F * Math.max(1.0F, o.scale);
            }
            default -> {
                ax1 = Math.min(o.x1, o.x2);
                ay1 = Math.min(o.y1, o.y2);
                ax2 = Math.max(o.x1, o.x2);
                ay2 = Math.max(o.y1, o.y2);
            }
        }
        return ax2 >= x0 && ax1 <= x1 && ay2 >= y0 && ay1 <= y1;
    }

    private GrabOp translate(GrabOp o, int dx, int dy) {
        GrabOp n = new GrabOp();
        n.t = o.t;
        n.x1 = o.x1 - dx;
        n.y1 = o.y1 - dy;
        n.x2 = o.x2 - dx;
        n.y2 = o.y2 - dy;
        n.minU = o.minU;
        n.maxU = o.maxU;
        n.minV = o.minV;
        n.maxV = o.maxV;
        n.tex = o.tex;
        n.c1 = o.c1;
        n.c2 = o.c2;
        n.text = o.text;
        n.scale = o.scale;
        n.shadow = o.shadow;
        n.data = o.data;
        n.nbt = o.nbt;
        return n;
    }

    private GrabOp translateAnno(GrabOp a, int dx, int dy) {
        GrabOp n = translate(a, dx, dy);
        if (a.pts != null) {
            float[] p = new float[a.pts.length];
            for (int i = 0; i < p.length; i += 2) {
                p[i] = a.pts[i] - dx;
                p[i + 1] = a.pts[i + 1] - dy;
            }
            n.pts = p;
        }
        return n;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            onClose();
            return true;
        }
        if (editing) {
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_DONE, key) || key == 257 || key == 335) {
                capture();
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_UNDO, key) && !annos.isEmpty()) {
                annos.remove(annos.size() - 1);
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_CURSOR, key)) {
                bar.tool = AnnoBar.ACT_CURSOR;
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_PEN, key)) {
                bar.tool = AnnoBar.ACT_PEN;
                return true;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.DRAW_BOX, key)) {
                bar.tool = AnnoBar.ACT_BOX;
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (previewRL != null) {
            try {
                previewTex.close();
                this.minecraft.getTextureManager().release(previewRL);
            } catch (Throwable ignored) {
            }
            previewRL = null;
            previewTex = null;
            shot = null;
        } else if (shot != null) {
            try {
                shot.close();
            } catch (Throwable ignored) {
            }
            shot = null;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}