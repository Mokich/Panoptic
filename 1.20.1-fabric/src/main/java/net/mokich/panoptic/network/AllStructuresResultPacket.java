package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.ArrayList;
import java.util.List;

public class AllStructuresResultPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "all_structures_result");

    public final List<Info> structures;

    public AllStructuresResultPacket(List<Info> structures) {
        this.structures = structures;
    }

    public static void encode(AllStructuresResultPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.structures.size());
        for (Info s : msg.structures) {
            buf.writeUtf(s.id);
            buf.writeVarInt(s.tags.size());
            for (String tag : s.tags) {
                buf.writeUtf(tag);
            }
        }
    }

    public static AllStructuresResultPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Info> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Info s = new Info();
            s.id = buf.readUtf();
            int tagCount = buf.readVarInt();
            for (int t = 0; t < tagCount; t++) {
                s.tags.add(buf.readUtf());
            }
            list.add(s);
        }
        return new AllStructuresResultPacket(list);
    }

    public static void send(ServerPlayer player, AllStructuresResultPacket msg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(msg, buf);
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    public static void handle(AllStructuresResultPacket msg) {
        StructureClientHandler.handleList(msg);
    }

    public static final class Info {
        public String id = "";
        public List<String> tags = new ArrayList<>();
    }
}