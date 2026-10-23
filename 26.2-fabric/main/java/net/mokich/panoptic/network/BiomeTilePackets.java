package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mokich.panoptic.data.seed.RemoteBiomes;

public final class BiomeTilePackets {
    public static final int RES = 128;
    private BiomeTilePackets() {}

    public static class Request implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "biome_tile_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.ofMember(Request::encode, Request::decode);

        public final String dim;
        public final int step;
        public final int tx;
        public final int tz;

        public Request(String dim, int step, int tx, int tz) {
            this.dim = dim;
            this.step = step;
            this.tx = tx;
            this.tz = tz;
        }

        @Override
        public Type<Request> type() {
            return TYPE;
        }

        public static void encode(Request msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.step);
            buf.writeVarInt(msg.tx);
            buf.writeVarInt(msg.tz);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public static class Result implements CustomPacketPayload {
        public static final Type<Result> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "biome_tile_result"));
        public static final StreamCodec<FriendlyByteBuf, Result> STREAM_CODEC =
                StreamCodec.ofMember(Result::encode, Result::decode);

        public final String dim;
        public final int step;
        public final int tx;
        public final int tz;
        public final int[] palette;
        public final byte[] grid;

        public Result(String dim, int step, int tx, int tz, int[] palette, byte[] grid) {
            this.dim = dim;
            this.step = step;
            this.tx = tx;
            this.tz = tz;
            this.palette = palette;
            this.grid = grid;
        }

        @Override
        public Type<Result> type() {
            return TYPE;
        }

        public static void encode(Result msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.step);
            buf.writeVarInt(msg.tx);
            buf.writeVarInt(msg.tz);
            buf.writeVarInt(msg.palette.length);
            for (int id : msg.palette) {
                buf.writeVarInt(id);
            }
            int i = 0;
            int cells = msg.grid.length;
            buf.writeVarInt(cells);
            while (i < cells) {
                byte v = msg.grid[i];
                int run = 1;
                while (i + run < cells && msg.grid[i + run] == v && run < 16384) {
                    run++;
                }
                buf.writeVarInt(run);
                buf.writeByte(v);
                i += run;
            }
        }

        public static Result decode(FriendlyByteBuf buf) {
            String dim = buf.readUtf(256);
            int step = buf.readVarInt();
            int tx = buf.readVarInt();
            int tz = buf.readVarInt();
            int pn = Math.min(buf.readVarInt(), 256);
            int[] palette = new int[pn];
            for (int i = 0; i < pn; i++) {
                palette[i] = buf.readVarInt();
            }
            int cells = Math.min(buf.readVarInt(), RES * RES);
            byte[] grid = new byte[cells];
            int i = 0;
            while (i < cells) {
                int run = Math.min(buf.readVarInt(), cells - i);
                byte v = buf.readByte();
                if (run <= 0) {
                    break;
                }
                for (int k = 0; k < run; k++) {
                    grid[i++] = v;
                }
            }
            return new Result(dim, step, tx, tz, palette, grid);
        }

        public static void handle(Result msg, ClientPlayNetworking.Context ctx) {
            ctx.client().execute(() ->
                    RemoteBiomes.onResult(msg.dim, msg.step, msg.tx, msg.tz, msg.palette, msg.grid));
        }
    }
}