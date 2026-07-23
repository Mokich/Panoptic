package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panoptic.data.seed.ServerSeed;

public class SeedPushPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "seed_push");

    public final boolean granted;
    public final long seed;

    public SeedPushPacket(boolean granted, long seed) {
        this.granted = granted;
        this.seed = seed;
    }

    public static void encode(SeedPushPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.granted);
        buf.writeLong(msg.seed);
    }

    public static SeedPushPacket decode(FriendlyByteBuf buf) {
        return new SeedPushPacket(buf.readBoolean(), buf.readLong());
    }

    public static void send(ServerPlayer player, SeedPushPacket msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(msg, buf);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    public static void handle(SeedPushPacket msg) {
        ServerSeed.set(msg.granted ? msg.seed : null);
    }
}