package net.mokich.panoptic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Util;
import net.minecraft.client.gui.screens.PauseScreen;
import net.mokich.panoptic.config.LogFeed;
import net.mokich.panoptic.event.InteractionEventHandler;
import net.mokich.panoptic.event.PauseMenuHandler;
import net.mokich.panoptic.event.ScreenCaptureHandler;
import net.mokich.panoptic.network.AdminEditPacket;
import net.mokich.panoptic.network.TeleportSurfacePacket;
import net.mokich.panoptic.network.TradeBookPackets;
import net.mokich.panoptic.network.AdminOpenPacket;
import net.mokich.panoptic.network.AdminStatePacket;
import net.mokich.panoptic.network.AllStructuresPacket;
import net.mokich.panoptic.network.AllStructuresResultPacket;
import net.mokich.panoptic.network.BiomeTilePackets;
import net.mokich.panoptic.network.GiveRequestPacket;
import net.mokich.panoptic.network.PermsSyncPacket;
import net.mokich.panoptic.network.SeedPushPacket;
import net.mokich.panoptic.network.SpawnVillagerPacket;
import net.mokich.panoptic.network.StructRegionPackets;
import net.mokich.panoptic.network.StructureCheckPacket;
import net.mokich.panoptic.network.StructureResultPacket;

public final class PanopticClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (Util.getPlatform() != Util.OS.OSX) {
            System.setProperty("java.awt.headless", "false");
        }
        LogFeed.attach();
        registerPayloads();
        registerReceivers();
        registerEvents();
    }

    private void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(StructureCheckPacket.TYPE, StructureCheckPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StructureResultPacket.TYPE, StructureResultPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AllStructuresPacket.TYPE, AllStructuresPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AllStructuresResultPacket.TYPE, AllStructuresResultPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PermsSyncPacket.TYPE, PermsSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AdminOpenPacket.TYPE, AdminOpenPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminStatePacket.TYPE, AdminStatePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AdminEditPacket.TYPE, AdminEditPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SeedPushPacket.TYPE, SeedPushPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpawnVillagerPacket.TYPE, SpawnVillagerPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GiveRequestPacket.TYPE, GiveRequestPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StructRegionPackets.Request.TYPE, StructRegionPackets.Request.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StructRegionPackets.Result.TYPE, StructRegionPackets.Result.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BiomeTilePackets.Request.TYPE, BiomeTilePackets.Request.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TeleportSurfacePacket.TYPE, TeleportSurfacePacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BiomeTilePackets.Result.TYPE, BiomeTilePackets.Result.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TradeBookPackets.Request.TYPE, TradeBookPackets.Request.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeBookPackets.Result.TYPE, TradeBookPackets.Result.STREAM_CODEC);
    }

    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(StructureCheckPacket.TYPE, StructureCheckPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(AllStructuresPacket.TYPE, AllStructuresPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(StructureResultPacket.TYPE, StructureResultPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(AllStructuresResultPacket.TYPE, AllStructuresResultPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(PermsSyncPacket.TYPE, PermsSyncPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(AdminStatePacket.TYPE, AdminStatePacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(SeedPushPacket.TYPE, SeedPushPacket::handle);
        ClientPlayNetworking.registerGlobalReceiver(StructRegionPackets.Result.TYPE, StructRegionPackets.Result::handle);
        ClientPlayNetworking.registerGlobalReceiver(BiomeTilePackets.Result.TYPE, BiomeTilePackets.Result::handle);
        ServerPlayNetworking.registerGlobalReceiver(TradeBookPackets.Request.TYPE, TradeBookPackets.Request::handle);
        ClientPlayNetworking.registerGlobalReceiver(TradeBookPackets.Result.TYPE, TradeBookPackets.Result::handle);
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> InteractionEventHandler.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> InteractionEventHandler.onDisconnect());
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                PauseMenuHandler.addButton(screen);
            }
            ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    ScreenCaptureHandler.onRenderPre(s));
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    ScreenCaptureHandler.onRenderPost(s));
        });
    }
}