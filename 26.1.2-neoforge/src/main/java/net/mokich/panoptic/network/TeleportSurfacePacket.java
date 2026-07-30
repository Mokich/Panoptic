package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TeleportSurfacePacket implements CustomPacketPayload {
    public static final Type<TeleportSurfacePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("panoptic", "teleport_surface"));
    public static final StreamCodec<FriendlyByteBuf, TeleportSurfacePacket> STREAM_CODEC =
            StreamCodec.ofMember(TeleportSurfacePacket::encode, TeleportSurfacePacket::decode);

    public final Identifier dim;
    public final int x;
    public final int z;

    public TeleportSurfacePacket(Identifier dim, int x, int z) {
        this.dim = dim;
        this.x = x;
        this.z = z;
    }

    @Override
    public Type<TeleportSurfacePacket> type() {
        return TYPE;
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
        ClientPacketDistributor.sendToServer(msg);
    }

    public static void handle(TeleportSurfacePacket msg, IPayloadContext ctx) {
    }
}