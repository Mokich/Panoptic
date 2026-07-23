package net.mokich.panoptic;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.mokich.panoptic.config.LogFeed;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import net.mokich.panoptic.config.PanopticConfig;
import net.mokich.panoptic.network.*;

@Mod("panoptic")
public class Panoptic {
   public static final String MODID = "panoptic";
   public static final SimpleChannel CHANNEL = ChannelBuilder
         .named(ResourceLocation.fromNamespaceAndPath("panoptic", "main"))
         .networkProtocolVersion(1)
         .optional()
         .simpleChannel();
   public static final SimpleChannel PERMS_CHANNEL = ChannelBuilder
         .named(ResourceLocation.fromNamespaceAndPath("panoptic", "perms"))
         .networkProtocolVersion(1)
         .optional()
         .simpleChannel();

   public Panoptic(FMLJavaModLoadingContext context) {
      context.getModEventBus().addListener(this::setup);
      ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
            PanopticConfig.SPEC);
      if (FMLEnvironment.dist.isClient()) {
         System.setProperty("java.awt.headless", "false");
         LogFeed.attach();
      }
   }

   private void setup(FMLCommonSetupEvent event) {
      CHANNEL.messageBuilder(StructureCheckPacket.class, 0).encoder(StructureCheckPacket::encode).decoder(StructureCheckPacket::decode).consumerNetworkThread(StructureCheckPacket::handle).add();
      CHANNEL.messageBuilder(StructureResultPacket.class, 1).encoder(StructureResultPacket::encode).decoder(StructureResultPacket::decode).consumerNetworkThread(StructureResultPacket::handle).add();
      CHANNEL.messageBuilder(AllStructuresPacket.class, 2).encoder(AllStructuresPacket::encode).decoder(AllStructuresPacket::decode).consumerNetworkThread(AllStructuresPacket::handle).add();
      CHANNEL.messageBuilder(AllStructuresResultPacket.class, 3).encoder(AllStructuresResultPacket::encode).decoder(AllStructuresResultPacket::decode).consumerNetworkThread(AllStructuresResultPacket::handle).add();
      CHANNEL.build();
      PERMS_CHANNEL.messageBuilder(PermsSyncPacket.class, 0).encoder(PermsSyncPacket::encode).decoder(PermsSyncPacket::decode).consumerNetworkThread(PermsSyncPacket::handle).add();
      PERMS_CHANNEL.messageBuilder(AdminOpenPacket.class, 1).encoder(AdminOpenPacket::encode).decoder(AdminOpenPacket::decode).consumerNetworkThread(AdminOpenPacket::handle).add();
      PERMS_CHANNEL.messageBuilder(AdminStatePacket.class, 2).encoder(AdminStatePacket::encode).decoder(AdminStatePacket::decode).consumerNetworkThread(AdminStatePacket::handle).add();
      PERMS_CHANNEL.messageBuilder(AdminEditPacket.class, 3).encoder(AdminEditPacket::encode).decoder(AdminEditPacket::decode).consumerNetworkThread(AdminEditPacket::handle).add();
      PERMS_CHANNEL.messageBuilder(SeedPushPacket.class, 4).encoder(SeedPushPacket::encode).decoder(SeedPushPacket::decode).consumerNetworkThread(SeedPushPacket::handle).add();
      PERMS_CHANNEL.messageBuilder(SpawnVillagerPacket.class, 5).encoder(SpawnVillagerPacket::encode).decoder(SpawnVillagerPacket::decode).consumerNetworkThread(SpawnVillagerPacket::handle).add();
      PERMS_CHANNEL.messageBuilder(GiveRequestPacket.class, 6).encoder(GiveRequestPacket::encode).decoder(GiveRequestPacket::decode).consumerNetworkThread(GiveRequestPacket::handle).add();
      PERMS_CHANNEL.messageBuilder(StructRegionPackets.Request.class, 9).encoder(StructRegionPackets.Request::encode).decoder(StructRegionPackets.Request::decode).consumerNetworkThread(StructRegionPackets.Request::handle).add();
      PERMS_CHANNEL.messageBuilder(StructRegionPackets.Result.class, 10).encoder(StructRegionPackets.Result::encode).decoder(StructRegionPackets.Result::decode).consumerNetworkThread(StructRegionPackets.Result::handle).add();
      PERMS_CHANNEL.messageBuilder(BiomeTilePackets.Request.class, 11).encoder(BiomeTilePackets.Request::encode).decoder(BiomeTilePackets.Request::decode).consumerNetworkThread(BiomeTilePackets.Request::handle).add();
      PERMS_CHANNEL.messageBuilder(BiomeTilePackets.Result.class, 12).encoder(BiomeTilePackets.Result::encode).decoder(BiomeTilePackets.Result::decode).consumerNetworkThread(BiomeTilePackets.Result::handle).add();
      PERMS_CHANNEL.build();
   }
}
