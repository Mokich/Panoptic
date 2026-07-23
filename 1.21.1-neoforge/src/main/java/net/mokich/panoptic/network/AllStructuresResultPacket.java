package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class AllStructuresResultPacket implements CustomPacketPayload {
    public static final Type<AllStructuresResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "all_structures_result"));
    public static final StreamCodec<FriendlyByteBuf, AllStructuresResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(AllStructuresResultPacket::encode, AllStructuresResultPacket::decode);

    public final List<Info> structures;

    public AllStructuresResultPacket(List<Info> structures) {
        this.structures = structures;
    }

    @Override
    public Type<AllStructuresResultPacket> type() {
        return TYPE;
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

    public static void handle(AllStructuresResultPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StructureClientHandler.handleList(msg));
    }

    public static final class Info {
        public String id = "";
        public List<String> tags = new ArrayList<>();
    }
}