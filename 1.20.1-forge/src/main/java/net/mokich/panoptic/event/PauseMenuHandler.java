package net.mokich.panoptic.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.mokich.panoptic.Panoptic;
import net.mokich.panoptic.api.ui.GuiStyle;
import net.mokich.panoptic.screen.MainScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Panoptic.MODID, value = Dist.CLIENT)
public final class PauseMenuHandler {

    private PauseMenuHandler() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) {
            return;
        }
        Screen s = event.getScreen();
        Button reportBugs = null;
        Button top = null;
        for (GuiEventListener l : s.children()) {
            if (l instanceof Button w && w.getHeight() == 20) {
                if (w.getMessage().getContents() instanceof TranslatableContents tc
                        && "menu.reportBugs".equals(tc.getKey())) {
                    reportBugs = w;
                }
                if (top == null || w.getY() < top.getY()
                        || (w.getY() == top.getY() && w.getWidth() > top.getWidth())) {
                    top = w;
                }
            }
        }
        int x;
        int y;
        if (reportBugs != null) {
            x = reportBugs.getX() + reportBugs.getWidth() + 4;
            y = reportBugs.getY();
        } else if (top != null) {
            x = s.width / 2 + 106;
            y = top.getY() + 48;
        } else {
            return;
        }
        IconButton b = new IconButton(x, y, btn -> Minecraft.getInstance().setScreen(new MainScreen()));
        b.setTooltip(Tooltip.create(Component.translatable("panoptic.pause.open")));
        event.addListener(b);
    }

    private static final class IconButton extends Button {
        private IconButton(int x, int y, OnPress onPress) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            super.renderWidget(g, mx, my, pt);
            int cx = getX() + 10;
            int cy = getY() + 10;
            g.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0xF22C2213);
            int bcol = isHoveredOrFocused() ? GuiStyle.ACCENT : GuiStyle.T(0xFF6A5630);
            g.fill(cx - 8, cy - 8, cx + 8, cy - 7, bcol);
            g.fill(cx - 8, cy + 7, cx + 8, cy + 8, bcol);
            g.fill(cx - 8, cy - 8, cx - 7, cy + 8, bcol);
            g.fill(cx + 7, cy - 8, cx + 8, cy + 8, bcol);
            box(g, cx, cy, 6, GuiStyle.ACCENT);
            box(g, cx, cy, 3, 0xFFFFD886);
            g.fill(cx + 1, cy - 1, cx + 6, cy + 1, 0xFFFFE7B0);
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFF4D8);
        }

        private static void box(GuiGraphics g, int cx, int cy, int r, int col) {
            g.fill(cx - r, cy - r, cx + r, cy - r + 1, col);
            g.fill(cx - r, cy + r - 1, cx + r, cy + r, col);
            g.fill(cx - r, cy - r, cx - r + 1, cy + r, col);
            g.fill(cx + r - 1, cy - r, cx + r, cy - 1, col);
            g.fill(cx + r - 1, cy + 1, cx + r, cy + r, col);
        }
    }
}