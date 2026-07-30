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
import net.mokich.panoptic.data.trade.VillagerBook;

import java.util.ArrayList;
import java.util.List;

public final class TradeBookPackets {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_OFFERS = 200;

    private TradeBookPackets() {
    }

    public record Offer(ItemStack costA, ItemStack costB, ItemStack result, int maxUses, int xp) {
    }

    public record Entry(int level, String sourceMod, List<Offer> offers) {
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

    public static class Request {
        public final String professionId;
        public final boolean wandering;

        public Request(String professionId, boolean wandering) {
            this.professionId = professionId;
            this.wandering = wandering;
        }

        public static void encode(Request msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.professionId, 256);
            buf.writeBoolean(msg.wandering);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readBoolean());
        }

        public static void handle(Request msg, CustomPayloadEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }
    }

    public static class Result {
        public final String professionId;
        public final boolean wandering;
        public final List<Entry> entries;

        public Result(String professionId, boolean wandering, List<Entry> entries) {
            this.professionId = professionId;
            this.wandering = wandering;
            this.entries = entries;
        }

        public static void encode(Result msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.professionId, 256);
            buf.writeBoolean(msg.wandering);
            RegistryAccess regs = regs();
            int n = regs == null ? 0 : Math.min(msg.entries.size(), MAX_ENTRIES);
            buf.writeVarInt(n);
            if (n == 0) {
                return;
            }
            RegistryFriendlyByteBuf rb = new RegistryFriendlyByteBuf(buf, regs);
            for (int i = 0; i < n; i++) {
                Entry e = msg.entries.get(i);
                buf.writeVarInt(e.level());
                buf.writeUtf(e.sourceMod() == null ? "" : e.sourceMod(), 128);
                int m = Math.min(e.offers().size(), MAX_OFFERS);
                buf.writeVarInt(m);
                for (int j = 0; j < m; j++) {
                    Offer o = e.offers().get(j);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(rb, o.costA());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(rb, o.costB());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(rb, o.result());
                    buf.writeVarInt(o.maxUses());
                    buf.writeVarInt(o.xp());
                }
            }
        }

        public static Result decode(FriendlyByteBuf buf) {
            String id = buf.readUtf(256);
            boolean wandering = buf.readBoolean();
            int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
            List<Entry> entries = new ArrayList<>(n);
            if (n == 0) {
                return new Result(id, wandering, entries);
            }
            RegistryAccess regs = regs();
            if (regs == null) {
                return new Result(id, wandering, entries);
            }
            RegistryFriendlyByteBuf rb = new RegistryFriendlyByteBuf(buf, regs);
            for (int i = 0; i < n; i++) {
                int level = buf.readVarInt();
                String mod = buf.readUtf(128);
                int m = Math.min(buf.readVarInt(), MAX_OFFERS);
                List<Offer> offers = new ArrayList<>(m);
                for (int j = 0; j < m; j++) {
                    ItemStack a = ItemStack.OPTIONAL_STREAM_CODEC.decode(rb);
                    ItemStack b = ItemStack.OPTIONAL_STREAM_CODEC.decode(rb);
                    ItemStack r = ItemStack.OPTIONAL_STREAM_CODEC.decode(rb);
                    offers.add(new Offer(a, b, r, buf.readVarInt(), buf.readVarInt()));
                }
                entries.add(new Entry(level, mod.isEmpty() ? null : mod, offers));
            }
            return new Result(id, wandering, entries);
        }

        public static void handle(Result msg, CustomPayloadEvent.Context ctx) {
            ctx.enqueueWork(() -> clientOnly(() ->
                    VillagerBook.applyRemote(msg.professionId, msg.wandering, msg.entries)));
            ctx.setPacketHandled(true);
        }
    }

    private static void clientOnly(Runnable task) {
        if (FMLEnvironment.dist.isClient()) {
            task.run();
        }
    }
}