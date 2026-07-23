package net.mokich.panoptic.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

public class GiveRequestPacket {
    public final List<ItemStack> items;

    public GiveRequestPacket(List<ItemStack> items) {
        this.items = items;
    }

    private static RegistryAccess regs() {
        if (FMLEnvironment.dist.isClient()) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                return level.registryAccess();
            }
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    public static void encode(GiveRequestPacket msg, FriendlyByteBuf buf) {
        RegistryAccess regs = regs();
        int n = regs == null ? 0 : Math.min(msg.items.size(), 27);
        buf.writeVarInt(n);
        if (n > 0) {
            RegistryFriendlyByteBuf rb = new RegistryFriendlyByteBuf(buf, regs);
            for (int i = 0; i < n; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(rb, msg.items.get(i));
            }
        }
    }

    public static GiveRequestPacket decode(FriendlyByteBuf buf) {
        RegistryAccess regs = regs();
        int n = Math.min(buf.readVarInt(), 27);
        List<ItemStack> items = new ArrayList<>(n);
        if (regs != null) {
            RegistryFriendlyByteBuf rb = new RegistryFriendlyByteBuf(buf, regs);
            for (int i = 0; i < n; i++) {
                items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(rb));
            }
        }
        return new GiveRequestPacket(items);
    }

    public static void handle(GiveRequestPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
    }
}
