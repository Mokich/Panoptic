package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

public class StructureResultPacket implements CustomPacketPayload {
    public static final Type<StructureResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "structure_result"));
    public static final StreamCodec<FriendlyByteBuf, StructureResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(StructureResultPacket::encode, StructureResultPacket::decode);

    public final List<StructInfo> structures;

    public StructureResultPacket(List<StructInfo> structures) {
        this.structures = structures;
    }

    @Override
    public Type<StructureResultPacket> type() {
        return TYPE;
    }

    public static void encode(StructureResultPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.structures.size());
        for (StructInfo s : msg.structures) {
            buf.writeUtf(s.id);
            buf.writeInt(s.minX);
            buf.writeInt(s.minY);
            buf.writeInt(s.minZ);
            buf.writeInt(s.maxX);
            buf.writeInt(s.maxY);
            buf.writeInt(s.maxZ);
            buf.writeVarInt(s.pieces);
            buf.writeVarInt(s.tags.size());
            for (String tag : s.tags) {
                buf.writeUtf(tag);
            }
        }
    }

    public static StructureResultPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<StructInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StructInfo s = new StructInfo();
            s.id = buf.readUtf();
            s.minX = buf.readInt();
            s.minY = buf.readInt();
            s.minZ = buf.readInt();
            s.maxX = buf.readInt();
            s.maxY = buf.readInt();
            s.maxZ = buf.readInt();
            s.pieces = buf.readVarInt();
            int tagCount = buf.readVarInt();
            for (int t = 0; t < tagCount; t++) {
                s.tags.add(buf.readUtf());
            }
            list.add(s);
        }
        return new StructureResultPacket(list);
    }

    public static void handle(StructureResultPacket msg, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> StructureClientHandler.handle(msg));
    }

    public static final class StructInfo {
        public String id = "";
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public int pieces;
        public List<String> tags = new ArrayList<>();
    }
}