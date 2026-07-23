package net.mokich.panoptic.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.mokich.panoptic.Guard;
import net.mokich.panoptic.Panoptic;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.mokich.panoptic.screenshot.HoverProbe;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(modid = Panoptic.MODID, value = Dist.CLIENT)
public final class ScreenCaptureHandler {
    private static Screen pendingScreen;
    private static boolean armed;

    private ScreenCaptureHandler() {}

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Guard.run(() -> screenKey(event));
    }

    private static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof RegionSelectScreen || screen instanceof ScreenInspectorScreen) {
            return;
        }
        if (ModBinds.matchesNow(ModBinds.Bind.SCREENGRAB, event.getKeyCode())) {
            if (!Perms.allowed(Perms.Feature.SCREENS)) {
                Perms.deny();
                event.setCanceled(true);
                return;
            }
            pendingScreen = screen;
            armed = false;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onWorldKey(InputEvent.Key event) {
        Guard.run(() -> worldKey(event));
    }

    private static void worldKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null && event.getAction() == GLFW.GLFW_PRESS
                && ModBinds.matchesNow(ModBinds.Bind.SCREENGRAB, event.getKey())) {
            mc.player.displayClientMessage(Component.translatable("panoptic.grab.need_screen").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenPre(ScreenEvent.Render.Pre event) {
        HoverProbe.newFrame();
        if (pendingScreen != null && event.getScreen() == pendingScreen) {
            GuiCaptureRecorder.arm();
            armed = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        Guard.run(() -> screenPost(event));
    }

    private static void screenPost(ScreenEvent.Render.Post event) {
        if (!armed || event.getScreen() != pendingScreen) {
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