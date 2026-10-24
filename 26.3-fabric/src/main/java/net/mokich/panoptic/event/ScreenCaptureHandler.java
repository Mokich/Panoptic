package net.mokich.panoptic.event;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.mokich.panoptic.Guard;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.Perms;
import net.mokich.panoptic.screen.screengrab.RegionSelectScreen;
import net.mokich.panoptic.screen.screengrab.ScreenInspectorScreen;
import net.mokich.panoptic.screenshot.GrabOp;
import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.mokich.panoptic.screenshot.HoverProbe;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

public final class ScreenCaptureHandler {
    private static Screen pendingScreen;
    private static boolean armed;
    private static List<GrabOp> captureOps;
    private static String captureTitle;
    private static boolean captureRequested;

    private ScreenCaptureHandler() {}

    public static boolean onScreenKey(Screen screen, int keyCode) {
        boolean[] cancel = new boolean[1];
        Guard.run(() -> cancel[0] = screenKey(screen, keyCode));
        return cancel[0];
    }

    private static boolean screenKey(Screen screen, int keyCode) {
        if (screen instanceof RegionSelectScreen || screen instanceof ScreenInspectorScreen) {
            return false;
        }
        if (ModBinds.matchesNow(ModBinds.Bind.SCREENGRAB, keyCode)) {
            if (!Perms.allowed(Perms.Feature.SCREENS)) {
                Perms.deny();
                return true;
            }
            pendingScreen = screen;
            armed = false;
            return true;
        }
        return false;
    }

    public static void onWorldKey(int key, int action) {
        Guard.run(() -> worldKey(key, action));
    }

    private static void worldKey(int key, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && mc.player != null && action == InputConstants.PRESS
                && ModBinds.matchesNow(ModBinds.Bind.SCREENGRAB, key)) {
            mc.gui.hud.setOverlayMessage(Component.translatable("panoptic.grab.need_screen").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    public static void onRenderPre(Screen s) {
        HoverProbe.newFrame();
        if (pendingScreen != null && s == pendingScreen) {
            GuiCaptureRecorder.arm();
            armed = true;
        }
    }

    public static void onRenderPost(Screen s) {
        Guard.run(() -> screenPost(s));
    }

    private static void screenPost(Screen s) {
        if (!armed || s != pendingScreen) {
            return;
        }
        armed = false;
        Screen screen = pendingScreen;
        pendingScreen = null;
        GuiCaptureRecorder.mergeSpots(HoverProbe.spots());
        GuiCaptureRecorder.mergeSpots(HoverProbe.spots());
        captureOps = GuiCaptureRecorder.disarmAndDrain();
        captureTitle = screen.getTitle() != null ? screen.getTitle().getString() : null;
        captureRequested = true;
    }

    public static void onFrameEnd() {
        if (!captureRequested) {
            return;
        }
        captureRequested = false;
        List<GrabOp> ops = captureOps;
        String title = captureTitle;
        captureOps = null;
        captureTitle = null;
        Minecraft mc = Minecraft.getInstance();
        try {
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> {
                if (ops.isEmpty() && img == null) {
                    return;
                }
                mc.execute(() -> mc.gui.setScreen(new RegionSelectScreen(ops, title, img)));
            });
        } catch (Throwable ignored) {
            if (!ops.isEmpty()) {
                mc.execute(() -> mc.gui.setScreen(new RegionSelectScreen(ops, title, null)));
            }
        }
    }
}
