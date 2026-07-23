package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panoptic.data.seed.RemoteBiomes;

public final class BiomeTilePackets {
    public static final int RES = 128;
    private BiomeTilePackets() {}

    public static class Request {
        public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "biome_tile_request");

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

        public static void encode(Request msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.step);
            buf.writeVarInt(msg.tx);
            buf.writeVarInt(msg.tz);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public static void send(Request msg) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            encode(msg, buf);
            ClientPlayNetworking.send(CHANNEL, buf);
        }
    }

    public static class Result {
        public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "biome_tile_result");

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

        public static void send(ServerPlayer player, Result msg) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            encode(msg, buf);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }

        public static void handle(Result msg) {
            RemoteBiomes.onResult(msg.dim, msg.step, msg.tx, msg.tz, msg.palette, msg.grid);
        }
    }
}