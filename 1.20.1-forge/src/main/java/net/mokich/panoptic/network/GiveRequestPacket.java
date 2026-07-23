package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GiveRequestPacket {
    public final List<ItemStack> items;

    public GiveRequestPacket(List<ItemStack> items) {
        this.items = items;
    }

    public static void encode(GiveRequestPacket msg, FriendlyByteBuf buf) {
        int n = Math.min(msg.items.size(), 27);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeItem(msg.items.get(i));
        }
    }

    public static GiveRequestPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 27);
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(buf.readItem());
        }
        return new GiveRequestPacket(items);
    }

    public static void handle(GiveRequestPacket msg, Supplier<Context> ctx) {
        ctx.get().setPacketHandled(true);
    }
}