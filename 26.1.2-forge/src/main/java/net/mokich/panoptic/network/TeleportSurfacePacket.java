package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import net.mokich.panoptic.Panoptic;

public class TeleportSurfacePacket {
    public final Identifier dim;
    public final int x;
    public final int z;

    public TeleportSurfacePacket(Identifier dim, int x, int z) {
        this.dim = dim;
        this.x = x;
        this.z = z;
    }

    public static void encode(TeleportSurfacePacket msg, FriendlyByteBuf buf) {
        buf.writeIdentifier(msg.dim);
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.z);
    }

    public static TeleportSurfacePacket decode(FriendlyByteBuf buf) {
        return new TeleportSurfacePacket(buf.readIdentifier(), buf.readVarInt(), buf.readVarInt());
    }

    public static void send(TeleportSurfacePacket msg) {
        Panoptic.PERMS_CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }

    public static void handle(TeleportSurfacePacket msg, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
    }
}