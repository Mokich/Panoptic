package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.mokich.panoptic.config.Perms;

import java.util.ArrayList;
import java.util.List;

public class PermsSyncPacket implements CustomPacketPayload {
    public static final Type<PermsSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "perms_sync"));
    public static final StreamCodec<FriendlyByteBuf, PermsSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(PermsSyncPacket::encode, PermsSyncPacket::decode);

    private final List<String> nodes;

    public PermsSyncPacket(List<String> nodes) {
        this.nodes = nodes;
    }

    @Override
    public Type<PermsSyncPacket> type() {
        return TYPE;
    }

    public static void encode(PermsSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.nodes.size());
        for (String n : msg.nodes) {
            buf.writeUtf(n, 64);
        }
    }

    public static PermsSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> nodes = new ArrayList<>(Math.min(n, 64));
        for (int i = 0; i < n && i < 64; i++) {
            nodes.add(buf.readUtf(64));
        }
        return new PermsSyncPacket(nodes);
    }

    public static void handle(PermsSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Perms.applySyncNodes(msg.nodes));
    }
}