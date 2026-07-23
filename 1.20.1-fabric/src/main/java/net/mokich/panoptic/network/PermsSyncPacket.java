package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panoptic.config.Perms;

import java.util.ArrayList;
import java.util.List;

public class PermsSyncPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "perms_sync");

    private final List<String> nodes;

    public PermsSyncPacket(List<String> nodes) {
        this.nodes = nodes;
    }

    public static void encode(PermsSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.nodes.size());
        for (String n : msg.nodes) {
            buf.writeUtf(n, 64);
        }
    }

    public static PermsSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> nodes = new ArrayList<>(Math.min(n, 64));
        for (int i = 0; i < n && i < 64; i++) {
            nodes.add(buf.readUtf(64));
        }
        return new PermsSyncPacket(nodes);
    }

    public static void send(ServerPlayer player, PermsSyncPacket msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(msg, buf);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    public static void handle(PermsSyncPacket msg) {
        Perms.applySyncNodes(msg.nodes);
    }
}