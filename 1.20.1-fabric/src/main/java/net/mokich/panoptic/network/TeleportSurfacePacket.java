package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public class TeleportSurfacePacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "teleport_surface");

    public final ResourceLocation dim;
    public final int x;
    public final int z;

    public TeleportSurfacePacket(ResourceLocation dim, int x, int z) {
        this.dim = dim;
        this.x = x;
        this.z = z;
    }

    public static void encode(TeleportSurfacePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dim);
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.z);
    }

    public static TeleportSurfacePacket decode(FriendlyByteBuf buf) {
        return new TeleportSurfacePacket(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
    }

    public static void send(TeleportSurfacePacket msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(msg, buf);
        ClientPlayNetworking.send(CHANNEL, buf);
    }
}