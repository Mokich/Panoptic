package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class SpawnVillagerPacket implements CustomPacketPayload {
    public static final Type<SpawnVillagerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "spawn_villager"));
    public static final StreamCodec<FriendlyByteBuf, SpawnVillagerPacket> STREAM_CODEC =
            StreamCodec.ofMember(SpawnVillagerPacket::encode, SpawnVillagerPacket::decode);

    public final String professionId;
    public final boolean wandering;

    public SpawnVillagerPacket(String professionId, boolean wandering) {
        this.professionId = professionId;
        this.wandering = wandering;
    }

    @Override
    public Type<SpawnVillagerPacket> type() {
        return TYPE;
    }

    public static void encode(SpawnVillagerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.professionId, 128);
        buf.writeBoolean(msg.wandering);
    }

    public static SpawnVillagerPacket decode(FriendlyByteBuf buf) {
        return new SpawnVillagerPacket(buf.readUtf(128), buf.readBoolean());
    }

}