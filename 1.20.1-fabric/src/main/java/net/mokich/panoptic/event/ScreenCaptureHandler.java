package net.mokich.panoptic.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.mokich.panoptic.Guard;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.Perms;
import net.mokich.panoptic.screen.screengrab.RegionSelectScreen;
import net.mokich.panoptic.screen.screengrab.ScreenInspectorScreen;
import com.mojang.blaze3d.platform.NativeImage;
import net.mokich.panoptic.screenshot.GrabOp;
import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.mokich.panoptic.screenshot.HoverProbe;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class ScreenCaptureHandler {
    private static Screen pendingScreen;
    private static boolean armed;

    private ScreenCaptureHandler() {}

    public static boolean onScreenKey(Screen screen, int keyCode) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null && action == GLFW.GLFW_PRESS
                && ModBinds.matchesNow(ModBinds.Bind.SCREENGRAB, key)) {
            mc.player.displayClientMessage(Component.translatable("panoptic.grab.need_screen").withStyle(ChatFormatting.YELLOW), true);
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
        List<GrabOp> ops = GuiCaptureRecorder.disarmAndDrain();
        Minecraft mc = Minecraft.getInstance();
        NativeImage shot = null;
        try {
            shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        } catch (Throwable ignored) {
        }
        if (ops.isEmpty() && shot == null) {
            return;
        }
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        NativeImage finalShot = shot;
        mc.execute(() -> mc.setScreen(new RegionSelectScreen(ops, title, finalShot)));
    }
}