package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class AdminEditPacket {
    public static final byte OP_GROUP_CREATE = 0;
    public static final byte OP_GROUP_REMOVE = 1;
    public static final byte OP_GROUP_NODE_ADD = 2;
    public static final byte OP_GROUP_NODE_REMOVE = 3;
    public static final byte OP_PLAYER_GROUP = 4;
    public static final byte OP_PLAYER_ALLOW = 5;
    public static final byte OP_PLAYER_DENY = 6;
    public static final byte OP_PLAYER_UNSET = 7;
    public static final byte OP_IMPORT = 8;

    public final byte op;
    public final String a;
    public final String b;

    public AdminEditPacket(byte op, String a, String b) {
        this.op = op;
        this.a = a;
        this.b = b;
    }

    public static void encode(AdminEditPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.op);
        buf.writeUtf(msg.a, 32000);
        buf.writeUtf(msg.b, 64);
    }

    public static AdminEditPacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        String a = buf.readUtf(32000);
        String b = buf.readUtf(64);
        return new AdminEditPacket(op, a, b);
    }

    public static void handle(AdminEditPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
    }
}