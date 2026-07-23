package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class AdminOpenPacket {
    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {
    }

    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

    public static void handle(AdminOpenPacket msg, Supplier<Context> ctx) {
        ctx.get().setPacketHandled(true);
    }
}