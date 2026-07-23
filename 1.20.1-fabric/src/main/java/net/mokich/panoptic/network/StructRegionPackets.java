package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panoptic.data.seed.RemoteStructs;

import java.util.ArrayList;
import java.util.List;

public final class StructRegionPackets {
    private StructRegionPackets() {
    }

    public static class Request {
        public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "struct_region_request");

        public final String dim;
        public final int rx;
        public final int rz;

        public Request(String dim, int rx, int rz) {
            this.dim = dim;
            this.rx = rx;
            this.rz = rz;
        }

        public static void encode(Request msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.rx);
            buf.writeVarInt(msg.rz);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
        }

        public static void send(Request msg) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            encode(msg, buf);
            ClientPlayNetworking.send(CHANNEL, buf);
        }
    }

    public static class Result {
        public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "struct_region_result");

        public final String dim;
        public final int rx;
        public final int rz;
        public final List<RemoteStructs.Raw> found;

        public Result(String dim, int rx, int rz, List<RemoteStructs.Raw> found) {
            this.dim = dim;
            this.rx = rx;
            this.rz = rz;
            this.found = found;
        }

        public static void encode(Result msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.rx);
            buf.writeVarInt(msg.rz);
            int n = Math.min(msg.found.size(), 4096);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                RemoteStructs.Raw f = msg.found.get(i);
                buf.writeUtf(f.id(), 256);
                buf.writeVarInt(f.x());
                buf.writeVarInt(f.z());
            }
        }

        public static Result decode(FriendlyByteBuf buf) {
            String dim = buf.readUtf(256);
            int rx = buf.readVarInt();
            int rz = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 4096);
            List<RemoteStructs.Raw> found = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                found.add(new RemoteStructs.Raw(buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
            }
            return new Result(dim, rx, rz, found);
        }

        public static void send(ServerPlayer player, Result msg) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            encode(msg, buf);
            ServerPlayNetworking.send(player, CHANNEL, buf);
        }

        public static void handle(Result msg) {
            RemoteStructs.onResult(msg.dim, msg.rx, msg.rz, msg.found);
        }
    }
}