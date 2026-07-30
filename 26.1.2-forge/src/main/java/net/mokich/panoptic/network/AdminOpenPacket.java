package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class AdminOpenPacket {
    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

    public static void handle(AdminOpenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
    }
}