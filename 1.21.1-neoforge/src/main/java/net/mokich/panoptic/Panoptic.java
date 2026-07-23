package net.mokich.panoptic;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.mokich.panoptic.config.LogFeed;
import net.mokich.panoptic.config.PanopticConfig;
import net.mokich.panoptic.network.*;

@Mod(Panoptic.MODID)
public class Panoptic {
    public static final String MODID = "panoptic";

    public Panoptic(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.COMMON, PanopticConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            System.setProperty("java.awt.headless", "false");
            LogFeed.attach();
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();

        r.playToServer(StructureCheckPacket.TYPE, StructureCheckPacket.STREAM_CODEC, StructureCheckPacket::handle);
        r.playToClient(StructureResultPacket.TYPE, StructureResultPacket.STREAM_CODEC, StructureResultPacket::handle);
        r.playToServer(AllStructuresPacket.TYPE, AllStructuresPacket.STREAM_CODEC, AllStructuresPacket::handle);
        r.playToClient(AllStructuresResultPacket.TYPE, AllStructuresResultPacket.STREAM_CODEC, AllStructuresResultPacket::handle);

        r.playToClient(PermsSyncPacket.TYPE, PermsSyncPacket.STREAM_CODEC, PermsSyncPacket::handle);
        r.playToServer(AdminOpenPacket.TYPE, AdminOpenPacket.STREAM_CODEC, AdminOpenPacket::handle);
        r.playToClient(AdminStatePacket.TYPE, AdminStatePacket.STREAM_CODEC, AdminStatePacket::handle);
        r.playToServer(AdminEditPacket.TYPE, AdminEditPacket.STREAM_CODEC, AdminEditPacket::handle);

        r.playToClient(SeedPushPacket.TYPE, SeedPushPacket.STREAM_CODEC, SeedPushPacket::handle);
        r.playToServer(SpawnVillagerPacket.TYPE, SpawnVillagerPacket.STREAM_CODEC, SpawnVillagerPacket::handle);
        r.playToServer(GiveRequestPacket.TYPE, GiveRequestPacket.STREAM_CODEC, GiveRequestPacket::handle);

        r.playToServer(StructRegionPackets.Request.TYPE, StructRegionPackets.Request.STREAM_CODEC, StructRegionPackets.Request::handle);
        r.playToClient(StructRegionPackets.Result.TYPE, StructRegionPackets.Result.STREAM_CODEC, StructRegionPackets.Result::handle);
        r.playToServer(BiomeTilePackets.Request.TYPE, BiomeTilePackets.Request.STREAM_CODEC, BiomeTilePackets.Request::handle);
        r.playToClient(BiomeTilePackets.Result.TYPE, BiomeTilePackets.Result.STREAM_CODEC, BiomeTilePackets.Result::handle);
    }
}