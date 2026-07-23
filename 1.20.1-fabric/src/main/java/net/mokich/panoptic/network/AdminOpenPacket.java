package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public class AdminOpenPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "admin_open");

    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

    public static void send(AdminOpenPacket msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(msg, buf);
        ClientPlayNetworking.send(CHANNEL, buf);
    }
}