package net.mokich.panoptic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.screens.PauseScreen;
import net.mokich.panoptic.config.LogFeed;
import net.mokich.panoptic.event.InteractionEventHandler;
import net.mokich.panoptic.event.PauseMenuHandler;
import net.mokich.panoptic.event.ScreenCaptureHandler;
import net.mokich.panoptic.network.AdminStatePacket;
import net.mokich.panoptic.network.AllStructuresPacket;
import net.mokich.panoptic.network.AllStructuresResultPacket;
import net.mokich.panoptic.network.BiomeTilePackets;
import net.mokich.panoptic.network.PermsSyncPacket;
import net.mokich.panoptic.network.SeedPushPacket;
import net.mokich.panoptic.network.StructRegionPackets;
import net.mokich.panoptic.network.StructureCheckPacket;
import net.mokich.panoptic.network.StructureResultPacket;

public final class PanopticClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.setProperty("java.awt.headless", "false");
        LogFeed.attach();
        registerReceivers();
        registerEvents();
    }

    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(StructureCheckPacket.CHANNEL, (server, player, handler, buf, sender) -> {
            StructureCheckPacket msg = StructureCheckPacket.decode(buf);
            server.execute(() -> StructureCheckPacket.handle(msg, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(AllStructuresPacket.CHANNEL, (server, player, handler, buf, sender) -> {
            AllStructuresPacket msg = AllStructuresPacket.decode(buf);
            server.execute(() -> AllStructuresPacket.handle(msg, player));
        });

        ClientPlayNetworking.registerGlobalReceiver(StructureResultPacket.CHANNEL, (client, handler, buf, sender) -> {
            StructureResultPacket msg = StructureResultPacket.decode(buf);
            client.execute(() -> StructureResultPacket.handle(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(AllStructuresResultPacket.CHANNEL, (client, handler, buf, sender) -> {
            AllStructuresResultPacket msg = AllStructuresResultPacket.decode(buf);
            client.execute(() -> AllStructuresResultPacket.handle(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(PermsSyncPacket.CHANNEL, (client, handler, buf, sender) -> {
            PermsSyncPacket msg = PermsSyncPacket.decode(buf);
            client.execute(() -> PermsSyncPacket.handle(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(AdminStatePacket.CHANNEL, (client, handler, buf, sender) -> {
            AdminStatePacket msg = AdminStatePacket.decode(buf);
            client.execute(() -> AdminStatePacket.handle(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(SeedPushPacket.CHANNEL, (client, handler, buf, sender) -> {
            SeedPushPacket msg = SeedPushPacket.decode(buf);
            client.execute(() -> SeedPushPacket.handle(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(StructRegionPackets.Result.CHANNEL, (client, handler, buf, sender) -> {
            StructRegionPackets.Result msg = StructRegionPackets.Result.decode(buf);
            client.execute(() -> StructRegionPackets.Result.handle(msg));
        });
        ClientPlayNetworking.registerGlobalReceiver(BiomeTilePackets.Result.CHANNEL, (client, handler, buf, sender) -> {
            BiomeTilePackets.Result msg = BiomeTilePackets.Result.decode(buf);
            client.execute(() -> BiomeTilePackets.Result.handle(msg));
        });
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> InteractionEventHandler.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> InteractionEventHandler.onDisconnect());
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                PauseMenuHandler.addButton(screen);
            }
            ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    ScreenCaptureHandler.onRenderPre(s));
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) ->
                    ScreenCaptureHandler.onRenderPost(s));
        });
    }
}