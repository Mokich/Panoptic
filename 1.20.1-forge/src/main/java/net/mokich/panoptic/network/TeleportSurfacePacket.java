package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent.Context;
import net.mokich.panoptic.Panoptic;

import java.util.function.Supplier;

public class TeleportSurfacePacket {
    public final ResourceLocation dim;
    public final int x;
    public final int z;

    public TeleportSurfacePacket(ResourceLocation dim, int x, int z) {
        this.dim = dim;
        this.x = x;
        this.z = z;
    }

    public static void send(TeleportSurfacePacket msg) {
        Panoptic.PERMS_CHANNEL.sendToServer(msg);
    }

    public static void encode(TeleportSurfacePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dim);
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.z);
    }

    public static TeleportSurfacePacket decode(FriendlyByteBuf buf) {
        return new TeleportSurfacePacket(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TeleportSurfacePacket msg, Supplier<Context> ctx) {
        ctx.get().setPacketHandled(true);
    }
}