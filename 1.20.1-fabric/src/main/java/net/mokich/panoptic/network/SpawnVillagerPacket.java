package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public class SpawnVillagerPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "spawn_villager");

    public final String professionId;
    public final boolean wandering;

    public SpawnVillagerPacket(String professionId, boolean wandering) {
        this.professionId = professionId;
        this.wandering = wandering;
    }

    public static void encode(SpawnVillagerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.professionId, 128);
        buf.writeBoolean(msg.wandering);
    }

    public static SpawnVillagerPacket decode(FriendlyByteBuf buf) {
        return new SpawnVillagerPacket(buf.readUtf(128), buf.readBoolean());
    }

    public static void send(SpawnVillagerPacket msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(msg, buf);
        ClientPlayNetworking.send(CHANNEL, buf);
    }
}