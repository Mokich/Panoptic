package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.data.seed.ServerSeed;

public class SeedPushPacket implements CustomPacketPayload {
    public static final Type<SeedPushPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "seed_push"));
    public static final StreamCodec<FriendlyByteBuf, SeedPushPacket> STREAM_CODEC =
            StreamCodec.ofMember(SeedPushPacket::encode, SeedPushPacket::decode);

    public final boolean granted;
    public final long seed;

    public SeedPushPacket(boolean granted, long seed) {
        this.granted = granted;
        this.seed = seed;
    }

    @Override
    public Type<SeedPushPacket> type() {
        return TYPE;
    }

    public static void encode(SeedPushPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.granted);
        buf.writeLong(msg.seed);
    }

    public static SeedPushPacket decode(FriendlyByteBuf buf) {
        return new SeedPushPacket(buf.readBoolean(), buf.readLong());
    }

    public static void handle(SeedPushPacket msg, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> ServerSeed.set(msg.granted ? msg.seed : null));
    }
}