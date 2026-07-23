package net.mokich.panoptic.event;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.mokich.panoptic.Guard;
import net.neoforged.neoforge.network.PacketDistributor;
import net.mokich.panoptic.Panoptic;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.mokich.panoptic.screenshot.HoverProbe;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Panoptic.MODID, value = Dist.CLIENT)
public final class InteractionEventHandler {
    private static final int CONFIRM_COLOR = 0x9070E0;
    private static long mainDownAt;
    private static int mainKeyCode = -1;
    private static boolean wheelShown;

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Perms.clearSync();
        ServerSeed.clear();
        RemoteStructs.clear();
        RemoteBiomes.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Guard.run(() -> clientTick(event));
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mainKeyCode != -1 && !wheelShown && System.currentTimeMillis() - mainDownAt >= ModSettings.getInt(ModSettings.WHEEL_HOLD_MS)) {
            wheelShown = true;
            if (mc.screen == null && mc.level != null) {
                mc.setScreen(new WheelScreen(mainKeyCode));
            }
        }
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Guard.run(() -> screenKey(event));
    }

    private static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        Screen s = event.getScreen();
        if (s instanceof WheelScreen || s instanceof RegionSelectScreen) {
            return;
        }
        if (!ModBinds.matchesNow(ModBinds.Bind.MAIN, event.getKeyCode()) || isTyping(s)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.setScreen(new WheelScreen(event.getKeyCode(), s));
        event.setCanceled(true);
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

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Guard.run(() -> keyInput(event));
    }

    private static void keyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int action = event.getAction();
        int key = event.getKey();

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

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Guard.run(() -> mouseInput(event));
    }

    private static void mouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int button = event.getButton();
        if (event.getAction() == GLFW.GLFW_PRESS && mc.screen == null) {
            if (ModBinds.matchesMouse(ModBinds.Bind.MAIN, button)) {
                mc.setScreen(new MainScreen());
                event.setCanceled(true);
                return;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.INSPECTOR, button)) {
                if (Perms.allowed(Perms.Feature.INSPECTOR)) {
                    mc.setScreen(new InspectorScreen());
                } else {
                    Perms.deny();
                }
                event.setCanceled(true);
                return;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.SEEDMAP, button)) {
                if (Perms.seedScreenAllowed()) {
                    mc.setScreen(new SeedMapScreen(null));
                } else {
                    Perms.deny();
                }
                event.setCanceled(true);
                return;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.VILLAGERS, button)) {
                if (Perms.allowed(Perms.Feature.TRADE)) {
                    mc.setScreen(new VillagerBrowserScreen(null));
                } else {
                    Perms.deny();
                }
                event.setCanceled(true);
                return;
            }
            if (ModBinds.matchesMouse(ModBinds.Bind.SCREEN_INSPECTOR, button)) {
                if (Perms.allowed(Perms.Feature.SCREENS)) {
                    mc.setScreen(new ScreenInspectorScreen());
                } else {
                    Perms.deny();
                }
                event.setCanceled(true);
                return;
            }
        }
        if (event.getAction() == GLFW.GLFW_RELEASE && ModBinds.matchesMouse(ModBinds.Bind.CAPTURE, button)) {
            doCapture(mc);
            event.setCanceled(true);
        }
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
                Slot slot = screen.getSlotUnderMouse();
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

        PacketDistributor.sendToServer(new StructureCheckPacket());
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