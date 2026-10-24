package net.mokich.panoptic.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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

    public static class Request implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("panoptic", "trade_book_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.ofMember(Request::encode, Request::decode);

        public final String professionId;
        public final boolean wandering;

        public Request(String professionId, boolean wandering) {
            this.professionId = professionId;
            this.wandering = wandering;
        }

        @Override
        public Type<Request> type() {
            return TYPE;
        }

        public static void encode(Request msg, RegistryFriendlyByteBuf buf) {
            buf.writeUtf(msg.professionId, 256);
            buf.writeBoolean(msg.wandering);
        }

        public static Request decode(RegistryFriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readBoolean());
        }

        public static void handle(Request msg, ServerPlayNetworking.Context ctx) {
        }
    }

    public static class Result implements CustomPacketPayload {
        public static final Type<Result> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("panoptic", "trade_book_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC =
                StreamCodec.ofMember(Result::encode, Result::decode);

        public final String professionId;
        public final boolean wandering;
        public final List<Entry> entries;

        public Result(String professionId, boolean wandering, List<Entry> entries) {
            this.professionId = professionId;
            this.wandering = wandering;
            this.entries = entries;
        }

        @Override
        public Type<Result> type() {
            return TYPE;
        }

        public static void encode(Result msg, RegistryFriendlyByteBuf buf) {
            buf.writeUtf(msg.professionId, 256);
            buf.writeBoolean(msg.wandering);
            int n = Math.min(msg.entries.size(), MAX_ENTRIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Entry e = msg.entries.get(i);
                buf.writeVarInt(e.level());
                buf.writeUtf(e.sourceMod() == null ? "" : e.sourceMod(), 128);
                int m = Math.min(e.offers().size(), MAX_OFFERS);
                buf.writeVarInt(m);
                for (int j = 0; j < m; j++) {
                    Offer o = e.offers().get(j);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, o.costA());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, o.costB());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, o.result());
                    buf.writeVarInt(o.maxUses());
                    buf.writeVarInt(o.xp());
                }
            }
        }

        public static Result decode(RegistryFriendlyByteBuf buf) {
            String id = buf.readUtf(256);
            boolean wandering = buf.readBoolean();
            int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int level = buf.readVarInt();
                String mod = buf.readUtf(128);
                int m = Math.min(buf.readVarInt(), MAX_OFFERS);
                List<Offer> offers = new ArrayList<>(m);
                for (int j = 0; j < m; j++) {
                    ItemStack a = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack b = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    ItemStack r = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    offers.add(new Offer(a, b, r, buf.readVarInt(), buf.readVarInt()));
                }
                entries.add(new Entry(level, mod.isEmpty() ? null : mod, offers));
            }
            return new Result(id, wandering, entries);
        }

        public static void handle(Result msg, ClientPlayNetworking.Context ctx) {
            ctx.client().execute(() ->
                    VillagerBook.applyRemote(msg.professionId, msg.wandering, msg.entries));
        }
    }
}
