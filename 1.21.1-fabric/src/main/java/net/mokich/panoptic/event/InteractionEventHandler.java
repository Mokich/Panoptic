package net.mokich.panoptic.event;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.Guard;
import net.mokich.panoptic.data.seed.RemoteBiomes;
import net.mokich.panoptic.data.seed.RemoteStructs;
import net.mokich.panoptic.data.seed.ServerSeed;
import net.mokich.panoptic.screen.inspector.InspectorScreen;
import net.mokich.panoptic.screen.MainScreen;
import net.mokich.panoptic.screen.screengrab.ScreenInspectorScreen;
import net.mokich.panoptic.screen.screengrab.RegionSelectScreen;
import net.mokich.panoptic.config.ModBinds;
import net.mokich.panoptic.config.ModSettings;
import net.mokich.panoptic.config.Perms;
import net.mokich.panoptic.api.ui.TextTyping;
import net.mokich.panoptic.screen.seed.SeedMapScreen;
import net.mokich.panoptic.screen.trade.VillagerBrowserScreen;
import net.mokich.panoptic.screen.WheelScreen;
import net.mokich.panoptic.inspect.InspectEntry;
import net.mokich.panoptic.inspect.InspectStore;
import net.mokich.panoptic.inspect.InspectType;
import net.mokich.panoptic.inspect.Inspectors;
import net.mokich.panoptic.mixin.AbstractContainerScreenAccessor;
import net.mokich.panoptic.network.StructureCheckPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.mokich.panoptic.screenshot.HoverProbe;
import org.lwjgl.glfw.GLFW;

public final class InteractionEventHandler {
    private static final int CONFIRM_COLOR = 0x9070E0;
    private static long mainDownAt;
    private static int mainKeyCode = -1;
    private static boolean wheelShown;

    private InteractionEventHandler() {
    }

    public static void onDisconnect() {
        Perms.clearSync();
        ServerSeed.clear();
        RemoteStructs.clear();
        RemoteBiomes.clear();
    }

    public static void onClientTick() {
        Guard.run(InteractionEventHandler::clientTick);
    }

    private static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mainKeyCode != -1 && !wheelShown && System.currentTimeMillis() - mainDownAt >= ModSettings.getInt(ModSettings.WHEEL_HOLD_MS)) {
            wheelShown = true;
            if (mc.screen == null && mc.level != null) {
                mc.setScreen(new WheelScreen(mainKeyCode));
            }
        }
    }

    public static boolean onScreenKey(Screen s, int keyCode) {
        try {
            return screenKey(s, keyCode);
        } catch (Throwable t) {
            Guard.report(t);
            return false;
        }
    }

    private static boolean screenKey(Screen s, int keyCode) {
        if (s instanceof WheelScreen || s instanceof RegionSelectScreen) {
            return false;
        }
        if (!ModBinds.matchesNow(ModBinds.Bind.MAIN, keyCode) || isTyping(s)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return false;
        }
        mc.setScreen(new WheelScreen(keyCode, s));
        return true;
    }

    private static boolean isTyping(Screen s) {
        if (s == null) {
            return false;
        }
        if (s instanceof ChatScreen
                || s instanceof BookEditScreen
                || s instanceof AbstractSignEditScreen
                || s instanceof CreativeModeInventoryScreen) {
            return true;
        }
        if (s instanceof TextTyping t && t.gmtTyping()) {
            return true;
        }
        return s.getFocused() instanceof EditBox eb && eb.canConsumeInput();
    }

    public static void onKey(int key, int action) {
        Guard.run(() -> keyInput(key, action));
    }

    private static void keyInput(int key, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (action == GLFW.GLFW_PRESS && mc.screen == null) {
            if (ModBinds.matchesNow(ModBinds.Bind.MAIN, key)) {
                mainDownAt = System.currentTimeMillis();
                mainKeyCode = key;
                wheelShown = false;
                return;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.INSPECTOR, key)) {
                if (Perms.allowed(Perms.Feature.INSPECTOR)) {
                    mc.setScreen(new InspectorScreen());
                } else {
                    Perms.deny();
                }
                return;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.SEEDMAP, key)) {
                if (Perms.seedScreenAllowed()) {
                    mc.setScreen(new SeedMapScreen(null));
                } else {
                    Perms.deny();
                }
                return;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.VILLAGERS, key)) {
                if (Perms.allowed(Perms.Feature.TRADE)) {
                    mc.setScreen(new VillagerBrowserScreen(null));
                } else {
                    Perms.deny();
                }
                return;
            }
            if (ModBinds.matchesNow(ModBinds.Bind.SCREEN_INSPECTOR, key)) {
                if (Perms.allowed(Perms.Feature.SCREENS)) {
                    mc.setScreen(new ScreenInspectorScreen());
                } else {
                    Perms.deny();
                }
                return;
            }
        }
        if (action == GLFW.GLFW_RELEASE && mainKeyCode != -1 && key == mainKeyCode) {
            boolean openMain = !wheelShown && mc.screen == null;
            mainKeyCode = -1;
            if (openMain) {
                mc.setScreen(new MainScreen());
            }
            return;
        }

        if (action != GLFW.GLFW_RELEASE) return;
        if (!ModBinds.matchesNow(ModBinds.Bind.CAPTURE, key)) return;
        doCapture(mc);
    }

    public static boolean onMouse(int button, int action) {
        try {
            return mouseInput(button, action);
        } catch (Throwable t) {
            Guard.report(t);
            return false;
        }
    }

    private static boolean mouseInput(int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (action == GLFW.GLFW_PRESS && mc.screen == null) {
            if (ModBinds.matchesMouse(ModBinds.Bind.MAIN, button)) {
                mc.setScreen(new MainScreen());
                return true;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.INSPECTOR, button)) {
                if (Perms.allowed(Perms.Feature.INSPECTOR)) {
                    mc.setScreen(new InspectorScreen());
                } else {
                    Perms.deny();
                }
                return true;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.SEEDMAP, button)) {
                if (Perms.seedScreenAllowed()) {
                    mc.setScreen(new SeedMapScreen(null));
                } else {
                    Perms.deny();
                }
                return true;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.VILLAGERS, button)) {
                if (Perms.allowed(Perms.Feature.TRADE)) {
                    mc.setScreen(new VillagerBrowserScreen(null));
                } else {
                    Perms.deny();
                }
                return true;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.SCREEN_INSPECTOR, button)) {
                if (Perms.allowed(Perms.Feature.SCREENS)) {
                    mc.setScreen(new ScreenInspectorScreen());
                } else {
                    Perms.deny();
                }
                return true;
            }
        }
        if (action == GLFW.GLFW_RELEASE && ModBinds.matchesMouse(ModBinds.Bind.CAPTURE, button)) {
            doCapture(mc);
            return true;
        }
        return false;
    }

    private static double guiMouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
    }

    private static double guiMouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
    }

    private static void doCapture(Minecraft mc) {
        if (!Perms.allowed(Perms.Feature.INSPECTOR)) {
            Perms.deny();
            return;
        }
        if (mc.screen != null) {
            ItemStack hovered = HoverProbe.at(guiMouseX(mc), guiMouseY(mc));
            if (!hovered.isEmpty()) {
                capture(mc, Inspectors.item(hovered));
                return;
            }
            if (mc.screen instanceof AbstractContainerScreen<?> screen) {
                Slot slot = ((AbstractContainerScreenAccessor) screen).panoptic$getHoveredSlot();
                if (slot != null && slot.hasItem()) {
                    capture(mc, Inspectors.item(slot.getItem()));
                }
            }
            return;
        }

        boolean captured = false;

        HitResult fluidHit = mc.player.pick(20.0D, 0.0F, true);
        if (fluidHit instanceof BlockHitResult fh && fluidHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = fh.getBlockPos();
            FluidState fluid = mc.level.getFluidState(pos);
            if (!fluid.isEmpty() && mc.level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
                capture(mc, Inspectors.fluid(mc.level, pos, fluid));
                captured = true;
            }
        }

        if (!captured) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                capture(mc, Inspectors.block(mc.level, pos, mc.level.getBlockState(pos)));
            } else if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY) {
                capture(mc, Inspectors.entity(entityHit.getEntity()));
            } else {
                ItemStack mainHand = mc.player.getMainHandItem();
                if (!mainHand.isEmpty()) {
                    capture(mc, Inspectors.item(mainHand));
                }
            }
        }

        InspectEntry biome = Inspectors.biome(mc.level, mc.player.blockPosition());
        if (biome != null && !InspectStore.has(InspectType.BIOME, biome.id)) {
            InspectStore.add(biome);
        }

        ClientPlayNetworking.send(new StructureCheckPacket());
    }

    private static void capture(Minecraft mc, InspectEntry entry) {
        if (InspectStore.containsEqual(entry)) {
            mc.player.displayClientMessage(message("panoptic.msg.already", entry.title), true);
            return;
        }
        InspectStore.add(entry);
        mc.player.displayClientMessage(message("panoptic.msg.captured", entry.title), true);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 3.0F));
    }

    private static Component message(String key, String arg) {
        return Component.translatable(key, arg)
                .withStyle(s -> s.withColor(TextColor.fromRgb(CONFIRM_COLOR)));
    }
}