package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class AdminOpenPacket implements CustomPacketPayload {
    public static final Type<AdminOpenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "admin_open"));
    public static final StreamCodec<FriendlyByteBuf, AdminOpenPacket> STREAM_CODEC =
            StreamCodec.ofMember(AdminOpenPacket::encode, AdminOpenPacket::decode);

    @Override
    public Type<AdminOpenPacket> type() {
        return TYPE;
    }

    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {}

    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

}