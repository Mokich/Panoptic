package net.mokich.panoptic.screen;

import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.mokich.panoptic.Guard;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.api.ui.Icons;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.config.Perms;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

public class WheelScreen extends Screen {
    private static int PLATE_IN = GuiStyle.T(0xF01B160E);
    private static int PLATE_OUT = GuiStyle.T(0xF0100C07);
    private static int PLATE_IN_HOT = GuiStyle.T(0xF6564027);
    private static int PLATE_OUT_HOT = GuiStyle.T(0xF6382A16);

    static {
        GuiStyle.onTheme(() -> {
            PLATE_IN = GuiStyle.T(0xF01B160E);
            PLATE_OUT = GuiStyle.T(0xF0100C07);
            PLATE_IN_HOT = GuiStyle.T(0xF6564027);
            PLATE_OUT_HOT = GuiStyle.T(0xF6382A16);
        });
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float maxLife;
        float size;
        boolean spark;
    }

    private final List<MainScreen.Tool> tools = MainScreen.tools();
    private final List<Particle> particles = new ArrayList<>();
    private final RandomSource rand = RandomSource.create();
    private final int holdKey;
    private final long openedAt = System.currentTimeMillis();
    private final float[] hoverAnim;
    private int hovered = -1;
    private int prevHovered = -2;
    private long lastNano;
    private float plaqueAnim;
    private static final float[] SPIN = {0.072F, -0.113F, 0.167F};
    private static final float EVENT_LIFE = 0.85F;

    private final float[] rot = new float[GmtLogo.RINGS];
    private boolean armed = true;
    private float eventAge = -1.0F;
    private float eventAngle;
    private float hubTime;
    private float hubDt;
    private float vw;
    private float vh;
    private static final float REF_SCALE = 3.0F;

    private final Screen parent;

    public WheelScreen(int holdKey) {
        this(holdKey, null);
    }

    public WheelScreen(int holdKey, Screen parent) {
        super(Component.translatable("panoptic.main.title"));
        this.holdKey = holdKey;
        this.parent = parent;
        this.hoverAnim = new float[tools.size()];
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        if (lastNano == 0) {
            lastNano = now;
        }
        float dt = (float) Math.min((now - lastNano) / 1.0e9, 0.1);
        lastNano = now;

        float t = Mth.clamp((System.currentTimeMillis() - openedAt) / 170.0F, 0.0F, 1.0F);
        float appear = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);

        g.fillGradient(0, 0, this.width, this.height,
                mulAlpha(0x8C000000, appear), mulAlpha(0xB4000000, appear));

        double gs = this.minecraft != null ? this.minecraft.getWindow().getGuiScale() : REF_SCALE;
        float s = (float) (REF_SCALE / gs);
        vw = this.width / s;
        vh = this.height / s;
        float mvx = mouseX / s;
        float mvy = mouseY / s;
        g.pose().pushPose();
        g.pose().scale(s, s, 1.0F);

        float cx = vw / 2.0F;
        float cy = vh / 2.0F - 14;
        float base = Math.min(vw, vh);
        float r2 = base * 0.30F * (0.84F + 0.16F * appear);
        float r1 = r2 * 0.50F;
        float rotOff = (1.0F - appear) * 0.16F;
        int n = tools.size();
        float span = (float) (Math.PI * 2 / n);

        hovered = sectorAt(mvx, mvy, cx, cy, r1);
        if (hovered != prevHovered) {
            if (appear >= 0.85F && hovered >= 0 && this.minecraft != null) {
                if (ModSettings.getBool(ModSettings.UI_SOUNDS)) {
                    this.minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.9F, 0.2F));
                }
                if (ModSettings.getBool(ModSettings.WHEEL_PARTICLES)) {
                    burstSparks(cx, cy, r2, hovered, span, rotOff);
                }
            }
            prevHovered = hovered;
        }
        float k = 1.0F - (float) Math.exp(-dt * 17.0);
        for (int i = 0; i < n; i++) {
            hoverAnim[i] += ((i == hovered ? 1.0F : 0.0F) - hoverAnim[i]) * k;
        }
        plaqueAnim += ((hovered >= 0 ? 1.0F : 0.0F) - plaqueAnim) * (1.0F - (float) Math.exp(-dt * 14.0));

        updateParticles(dt);

        ring(g, cx, cy, r2 + 2.5F, r2 + 6.5F, mulAlpha(GuiStyle.T(0xE0170F07), appear), mulAlpha(GuiStyle.T(0xE0241B0E), appear));
        ring(g, cx, cy, r2 + 6.5F, r2 + 8.0F, mulAlpha(GuiStyle.BORDER_B, appear), mulAlpha(GuiStyle.T(0xFF4A3A1D), appear));

        for (int i = 0; i < n; i++) {
            float ha = hoverAnim[i];
            float pad = 0.035F;
            float a0 = (float) (-Math.PI / 2) + i * span - span / 2 + pad + rotOff;
            float a1 = a0 + span - pad * 2;
            float push = 7.5F * ha;
            float ri = r1 + push * 0.35F;
            float ro = r2 + push;

            arc(g, cx, cy, ri, ro, a0, a1,
                    mulAlpha(lerpColor(PLATE_IN, PLATE_IN_HOT, ha), appear),
                    mulAlpha(lerpColor(PLATE_OUT, PLATE_OUT_HOT, ha), appear));
            arc(g, cx, cy, ro - 2.0F, ro, a0, a1,
                    mulAlpha(lerpColor(GuiStyle.T(0x40FFE7B0), GuiStyle.T(0x90FFE7B0), ha), appear),
                    mulAlpha(lerpColor(GuiStyle.T(0x18FFE7B0), GuiStyle.T(0x50FFE7B0), ha), appear));
            arc(g, cx, cy, ri, ri + 1.6F, a0, a1,
                    mulAlpha(0x70000000, appear), mulAlpha(0x28000000, appear));

            int edge = lerpColor(GuiStyle.BORDER_B, GuiStyle.ACCENT, ha);
            arc(g, cx, cy, ro, ro + 1.6F + 0.6F * ha, a0, a1, mulAlpha(edge, appear), mulAlpha(edge, appear));
            arc(g, cx, cy, ri - 1.4F, ri, a0, a1,
                    mulAlpha(lerpColor(GuiStyle.T(0xFF4A3A1D), GuiStyle.ACCENT, ha), appear),
                    mulAlpha(lerpColor(GuiStyle.T(0xFF4A3A1D), GuiStyle.ACCENT, ha), appear));

            float divA = a0 - pad;
            arc(g, cx, cy, ri - 1.0F, ro + 1.0F, divA - 0.008F, divA + 0.008F,
                    mulAlpha(GuiStyle.T(0xFF3A2E17), appear), mulAlpha(GuiStyle.T(0xFF3A2E17), appear));
            dot(g, cx + Mth.cos(divA) * ((ri + ro) / 2), cy + Mth.sin(divA) * ((ri + ro) / 2),
                    1.9F, mulAlpha(GuiStyle.T(0xFF8C6C33), appear));

            float mid = a0 + (a1 - a0) / 2;
            float rm = (ri + ro) / 2;
            float bob = ha * Mth.sin(System.currentTimeMillis() % 1400L / 1400.0F * (float) (Math.PI * 2)) * 1.3F;
            int ix = Math.round(cx + Mth.cos(mid) * rm);
            int iy = Math.round(cy + Mth.sin(mid) * rm + bob);
            g.pose().pushPose();
            g.pose().translate(ix, iy, 0);
            float iconBase = r2 * 0.0122F;
            float sc = iconBase * (1.0F + 0.41F * ha) * (0.6F + 0.4F * appear);
            g.pose().scale(sc, sc, 1.0F);
            g.renderFakeItem(tools.get(i).icon(), -8, -8);
            if (!tools.get(i).unlocked()) {
                g.pose().translate(0, 0, 300);
                g.pose().scale(1.6F, 1.6F, 1.0F);
                int shadow = mulAlpha(0xFF1A0E08, appear);
                Icons.iconCross(g, -4, -3, shadow);
                Icons.iconCross(g, -2, -3, shadow);
                Icons.iconCross(g, -3, -4, shadow);
                Icons.iconCross(g, -3, -2, shadow);
                Icons.iconCross(g, -3, -3, mulAlpha(0xFFFF5B5B, appear));
            }
            g.pose().popPose();
        }

        drawParticles(g, true);
        hubDt = dt;
        drawHub(g, cx, cy, r1 - 6.0F, appear, Math.round(mvx), Math.round(mvy));
        drawPlaque(g, cx, cy + r2 + 26, appear);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partial);
    }

    private void drawHub(GuiGraphics g, float cx, float cy, float hubR, float appear, int mouseX, int mouseY) {
        ring(g, cx, cy, 0, hubR, mulAlpha(0xF41A150D, appear), mulAlpha(0xF40E0B06, appear));
        ring(g, cx, cy, hubR - 1.4F, hubR, mulAlpha(GuiStyle.BORDER_B, appear), mulAlpha(GuiStyle.BORDER_B, appear));
        ring(g, cx, cy, hubR - 3.6F, hubR - 2.8F, mulAlpha(0x30FFE7B0, appear), mulAlpha(0x30FFE7B0, appear));
        if (appear < 0.5F) {
            return;
        }
        drawLogo(g, cx, cy, hubR, hubDt, appear);
    }

    private void drawLogo(GuiGraphics g, float cx, float cy, float hubR, float dt, float appear) {
        hubTime += dt;
        for (int i = 0; i < GmtLogo.RINGS; i++) {
            rot[i] += SPIN[i] * dt;
        }

        float sx = 0.0F;
        float sy = 0.0F;
        for (int i = 0; i < GmtLogo.RINGS; i++) {
            sx += Mth.cos(rot[i]);
            sy += Mth.sin(rot[i]);
        }
        float align = Mth.sqrt(sx * sx + sy * sy) / GmtLogo.RINGS;
        float alignAngle = (float) Math.atan2(sy, sx);

        if (align > 0.9985F) {
            if (armed && eventAge < 0.0F) {
                armed = false;
                eventAge = 0.0F;
                eventAngle = alignAngle;
                if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
                    this.minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.09F));
                }
            }
        } else if (align < 0.96F) {
            armed = true;
        }

        float ev = 0.0F;
        if (eventAge >= 0.0F) {
            eventAge += dt;
            if (eventAge > EVENT_LIFE) {
                eventAge = -1.0F;
            } else {
                float k = eventAge / EVENT_LIFE;
                ev = (1.0F - k) * (1.0F - k);
            }
        }

        float slitHalf = hubR * 1.95F * GmtLogo.SLIT_HALF;
        float coreR = hubR * 1.95F * GmtLogo.CORE_RADIUS;

        if (ev > 0.01F) {
            float reach = hubR * 0.90F;
            barGrad(g, cx, cy, eventAngle, coreR * 0.5F, reach, slitHalf,
                    mulAlpha(0xFFFFF0D2, ev * appear), mulAlpha(0xFFFFC155, ev * 0.55F * appear));
            barGrad(g, cx, cy, eventAngle, coreR * 0.5F, reach, slitHalf * 2.4F,
                    mulAlpha(0xFFFFA840, ev * 0.16F * appear), mulAlpha(0xFFFFA840, 0.0F));
        }

        int size = Math.max(8, Math.round(hubR * 1.95F));
        for (int i = 0; i < GmtLogo.RINGS; i++) {
            blitLogo(g, GmtLogo.ringTexture(i), cx, cy, size, rot[i], appear, 1.0F, 1.0F, 1.0F);
            if (ev > 0.02F) {
                blitLogo(g, GmtLogo.ringTexture(i), cx, cy, size, rot[i],
                        appear * ev * 0.30F, 1.0F, 0.86F, 0.58F);
            }
        }

        float idle = 0.06F * (0.5F + 0.5F * Mth.sin(hubTime * 0.9F));
        blitLogo(g, GmtLogo.CORE_TEX, cx, cy, Math.round(size * 1.45F), 0.0F,
                appear * (idle + 0.42F * ev), 1.0F, 0.82F, 0.48F);
        blitLogo(g, GmtLogo.CORE_TEX, cx, cy, Math.round(size * (1.0F + 0.10F * ev)), 0.0F,
                appear * (0.62F + 0.38F * ev), 1.0F, 1.0F, 1.0F);
    }

    private void blitLogo(GuiGraphics g, ResourceLocation tex,
                          float cx, float cy, int size, float rot, float alpha,
                          float r, float gr, float b) {
        if (alpha <= 0.01F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, Mth.clamp(alpha, 0.0F, 1.0F));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().mulPose(Axis.ZP.rotation(rot));
        g.pose().translate(-size / 2.0F, -size / 2.0F, 0.0F);
        g.blit(tex, 0, 0, 0, 0, size, size, size, size);
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void barGrad(GuiGraphics g, float cx, float cy, float ang,
                                float r0, float r1, float halfW, int cIn, int cOut) {
        if (r1 <= r0) {
            return;
        }
        float cos = Mth.cos(ang);
        float sin = Mth.sin(ang);
        float px = -sin * halfW;
        float py = cos * halfW;
        int slices = 8;
        for (int i = 0; i < slices; i++) {
            float t0 = i / (float) slices;
            float t1 = (i + 1) / (float) slices;
            float ra = Mth.lerp(t0, r0, r1);
            float rb = Mth.lerp(t1, r0, r1);
            int col = lerpColor(cIn, cOut, (t0 + t1) * 0.5F);
            float ax = cx + cos * ra;
            float ay = cy + sin * ra;
            float bx = cx + cos * rb;
            float by = cy + sin * rb;
            quad(g, ax + px, ay + py, bx + px, by + py, bx - px, by - py, ax - px, ay - py, col);
        }
    }

    private static void quad(GuiGraphics g, float x1, float y1, float x2, float y2,
                             float x3, float y3, float x4, float y4, int argb) {
        tri(g, x1, y1, x2, y2, x3, y3, argb);
        tri(g, x1, y1, x3, y3, x4, y4, argb);
    }

    private static void tri(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        Matrix4f mat = g.pose().last().pose();
        float al = (argb >>> 24 & 255) / 255.0F;
        float r = (argb >> 16 & 255) / 255.0F;
        float gr = (argb >> 8 & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(mat, x1, y1, 0).setColor(r, gr, b, al);
        buf.addVertex(mat, x2, y2, 0).setColor(r, gr, b, al);
        buf.addVertex(mat, x3, y3, 0).setColor(r, gr, b, al);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void drawPlaque(GuiGraphics g, float cx, float cy, float appear) {
        if (plaqueAnim < 0.02F || hovered < 0) {
            return;
        }
        MainScreen.Tool tool = tools.get(hovered);
        String name = I18n.get(tool.nameKey());
        List<FormattedCharSequence> desc = this.font.split(Component.translatable(
                tool.unlocked() ? tool.descKey() : "panoptic.perm.locked"), 220);
        boolean hasBind = tool.bind() != null && ModBinds.key(tool.bind()).code != ModBinds.UNBOUND;
        String bind = hasBind ? ModBinds.label(tool.bind()) : null;

        int w = Math.max(this.font.width(name) + 44, 160);
        for (FormattedCharSequence l : desc) {
            w = Math.max(w, this.font.width(l) + 24);
        }
        if (bind != null) {
            w = Math.max(w, this.font.width(bind) + 48);
        }
        int lines = Math.min(2, desc.size());
        int h = 24 + lines * 10 + (bind != null ? 21 : 6);

        float slide = 1.0F - (1.0F - plaqueAnim) * (1.0F - plaqueAnim);
        int px1 = (int) cx - w / 2;
        int py1 = (int) (cy - (1.0F - slide) * 8);
        if (py1 + h > vh - 10) {
            py1 = (int) vh - 10 - h;
        }
        int px2 = px1 + w;
        int py2 = py1 + h;

        g.pose().pushPose();
        g.pose().translate(0, 0, 20);
        if (slide * appear > 0.03F) {
            GuiStyle.panel(g, px1, py1, px2, py2);
            g.drawCenteredString(this.font, name, (px1 + px2) / 2, py1 + 6, GuiStyle.ACCENT);
            GuiStyle.divider(g, px1 + 7, px2 - 7, py1 + 18);
            int y = py1 + 24;
            for (int i = 0; i < lines; i++) {
                g.drawCenteredString(this.font, desc.get(i), (px1 + px2) / 2, y, GuiStyle.MUTED);
                y += 10;
            }
            if (bind != null) {
                int bw = this.font.width(bind) + 14;
                int bx = (px1 + px2) / 2 - bw / 2;
                GuiStyle.keycap(g, this.font, bx, y + 3, bx + bw, y + 16, bind, false, false, true);
            }
        }
        g.pose().popPose();
    }


    private void burstSparks(float cx, float cy, float r2, int sector, float span, float rotOff) {
        float mid = (float) (-Math.PI / 2) + sector * span + rotOff;
        for (int i = 0; i < 14; i++) {
            if (particles.size() > 200) {
                return;
            }
            Particle p = new Particle();
            float a = mid + (rand.nextFloat() - 0.5F) * span * 0.85F;
            float r = r2 + rand.nextFloat() * 4.0F;
            p.x = cx + Mth.cos(a) * r;
            p.y = cy + Mth.sin(a) * r;
            float speed = 26.0F + rand.nextFloat() * 46.0F;
            p.vx = Mth.cos(a) * speed + (rand.nextFloat() - 0.5F) * 14.0F;
            p.vy = Mth.sin(a) * speed + (rand.nextFloat() - 0.5F) * 14.0F;
            p.maxLife = 0.30F + rand.nextFloat() * 0.35F;
            p.life = p.maxLife;
            p.size = rand.nextFloat() < 0.35F ? 2.0F : 1.0F;
            p.spark = true;
            particles.add(p);
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.spark) {
                p.vy += 92.0F * dt;
                p.vx *= 1.0F - 2.1F * dt;
                p.vy *= 1.0F - 0.7F * dt;
            } else {
                p.vy -= 2.0F * dt;
                p.vx += (rand.nextFloat() - 0.5F) * 9.0F * dt;
            }
        }
    }

    private void drawParticles(GuiGraphics g, boolean sparks) {
        for (Particle p : particles) {
            if (p.spark != sparks) {
                continue;
            }
            float f = Mth.clamp(p.life / p.maxLife, 0.0F, 1.0F);
            int alpha = (int) (255 * (p.spark ? f : f * 0.42F));
            if (alpha < 6) {
                continue;
            }
            int base = p.spark
                    ? lerpColor(GuiStyle.T(0xFFFF9A3C), GuiStyle.T(0xFFFFE7B0), f)
                    : GuiStyle.T(0xFFC8A868);
            int col = (alpha << 24) | (base & 0xFFFFFF);
            int s = (int) Math.max(1, p.size);
            g.fill((int) p.x, (int) p.y, (int) p.x + s, (int) p.y + s, col);
            if (p.spark && p.size > 1.5F) {
                g.fill((int) p.x - 1, (int) p.y, (int) p.x, (int) p.y + s, ((alpha / 3) << 24) | (base & 0xFFFFFF));
            }
        }
    }

    private static void dot(GuiGraphics g, float x, float y, float r, int color) {
        ring(g, x, y, 0, r, color, color);
    }

    private static void ring(GuiGraphics g, float cx, float cy, float r1, float r2, int cIn, int cOut) {
        arc(g, cx, cy, r1, r2, 0, (float) (Math.PI * 2), cIn, cOut);
    }

    private static int mulAlpha(int argb, float mul) {
        int a = (int) ((argb >>> 24) * Mth.clamp(mul, 0.0F, 1.0F));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
                | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    private int sectorAt(double mx, double my, float cx, float cy, float r1) {
        double dx = mx - cx;
        double dy = my - cy;
        if (Math.sqrt(dx * dx + dy * dy) < r1 * 0.94) {
            return -1;
        }
        int n = tools.size();
        double span = Math.PI * 2 / n;
        double rel = Math.atan2(dy, dx) + Math.PI / 2 + span / 2;
        rel = (rel % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return (int) (rel / span) % n;
    }

    private static void arc(GuiGraphics g, float cx, float cy, float r1, float r2,
                            float a0, float a1, int colInner, int colOuter) {
        Matrix4f mat = g.pose().last().pose();
        float ia = (colInner >>> 24 & 255) / 255.0F;
        float ir = (colInner >> 16 & 255) / 255.0F;
        float ig = (colInner >> 8 & 255) / 255.0F;
        float ib = (colInner & 255) / 255.0F;
        float oa = (colOuter >>> 24 & 255) / 255.0F;
        float or = (colOuter >> 16 & 255) / 255.0F;
        float og = (colOuter >> 8 & 255) / 255.0F;
        float ob = (colOuter & 255) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(4, (int) Math.ceil((a1 - a0) / 0.04F));
        for (int i = 0; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps;
            float cos = Mth.cos(a);
            float sin = Mth.sin(a);
            buf.addVertex(mat, cx + cos * r2, cy + sin * r2, 0).setColor(or, og, ob, oa);
            buf.addVertex(mat, cx + cos * r1, cy + sin * r1, 0).setColor(ir, ig, ib, ia);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void activate(int i) {
        try {
            if (i >= 0 && i < tools.size()) {
                if (!tools.get(i).unlocked()) {
                    Perms.deny();
                    onClose();
                    return;
                }
                if (this.minecraft != null && ModSettings.getBool(ModSettings.UI_SOUNDS)) {
                    this.minecraft.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.15F, 0.65F));
                }
                this.minecraft.setScreen(tools.get(i).open().apply(null));
            } else {
                onClose();
            }
        } catch (Throwable t) {
            Guard.report(t);
            onClose();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int mods) {
        if (keyCode == holdKey) {
            activate(hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            activate(hovered);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}