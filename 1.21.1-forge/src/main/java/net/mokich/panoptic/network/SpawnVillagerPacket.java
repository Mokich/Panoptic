package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SpawnVillagerPacket {
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

    public static void handle(SpawnVillagerPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
    }
}