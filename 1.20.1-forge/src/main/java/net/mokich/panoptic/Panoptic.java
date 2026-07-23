package net.mokich.panoptic;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.mokich.panoptic.config.LogFeed;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.mokich.panoptic.config.PanopticConfig;
import net.mokich.panoptic.network.*;

@Mod("panoptic")
public class Panoptic {
   public static final String MODID = "panoptic";
   private static final String PROTOCOL_VERSION = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("panoptic", "main"), () -> "1", s -> true, s -> true
   );
   public static final SimpleChannel PERMS_CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("panoptic", "perms"), () -> "1", s -> true, s -> true
   );

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
      CHANNEL.registerMessage(0, StructureCheckPacket.class, StructureCheckPacket::encode, StructureCheckPacket::decode, StructureCheckPacket::handle);
      CHANNEL.registerMessage(1, StructureResultPacket.class, StructureResultPacket::encode, StructureResultPacket::decode, StructureResultPacket::handle);
      CHANNEL.registerMessage(2, AllStructuresPacket.class, AllStructuresPacket::encode, AllStructuresPacket::decode, AllStructuresPacket::handle);
      CHANNEL.registerMessage(3, AllStructuresResultPacket.class, AllStructuresResultPacket::encode, AllStructuresResultPacket::decode, AllStructuresResultPacket::handle);
      PERMS_CHANNEL.registerMessage(0, PermsSyncPacket.class, PermsSyncPacket::encode, PermsSyncPacket::decode, PermsSyncPacket::handle);
      PERMS_CHANNEL.registerMessage(1, AdminOpenPacket.class, AdminOpenPacket::encode, AdminOpenPacket::decode, AdminOpenPacket::handle);
      PERMS_CHANNEL.registerMessage(2, AdminStatePacket.class, AdminStatePacket::encode, AdminStatePacket::decode, AdminStatePacket::handle);
      PERMS_CHANNEL.registerMessage(3, AdminEditPacket.class, AdminEditPacket::encode, AdminEditPacket::decode, AdminEditPacket::handle);
      PERMS_CHANNEL.registerMessage(4, SeedPushPacket.class, SeedPushPacket::encode, SeedPushPacket::decode, SeedPushPacket::handle);
      PERMS_CHANNEL.registerMessage(5, SpawnVillagerPacket.class, SpawnVillagerPacket::encode, SpawnVillagerPacket::decode, SpawnVillagerPacket::handle);
      PERMS_CHANNEL.registerMessage(6, GiveRequestPacket.class, GiveRequestPacket::encode, GiveRequestPacket::decode, GiveRequestPacket::handle);
      PERMS_CHANNEL.registerMessage(9, StructRegionPackets.Request.class, StructRegionPackets.Request::encode, StructRegionPackets.Request::decode, StructRegionPackets.Request::handle);
      PERMS_CHANNEL.registerMessage(10, StructRegionPackets.Result.class, StructRegionPackets.Result::encode, StructRegionPackets.Result::decode, StructRegionPackets.Result::handle);
      PERMS_CHANNEL.registerMessage(11, BiomeTilePackets.Request.class, BiomeTilePackets.Request::encode, BiomeTilePackets.Request::decode, BiomeTilePackets.Request::handle);
      PERMS_CHANNEL.registerMessage(12, BiomeTilePackets.Result.class, BiomeTilePackets.Result::encode, BiomeTilePackets.Result::decode, BiomeTilePackets.Result::handle);
   }
}