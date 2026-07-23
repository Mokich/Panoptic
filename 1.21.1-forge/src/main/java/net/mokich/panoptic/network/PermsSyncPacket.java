package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.mokich.panoptic.config.Perms;

import java.util.ArrayList;
import java.util.List;

public class PermsSyncPacket {
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

    public static void handle(PermsSyncPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Perms.applySyncNodes(msg.nodes)));
        ctx.setPacketHandled(true);
    }
}