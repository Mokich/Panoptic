package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.mokich.panoptic.data.seed.ServerSeed;
import net.minecraftforge.fml.loading.FMLEnvironment;


public class SeedPushPacket {
    public final boolean granted;
    public final long seed;

    public SeedPushPacket(boolean granted, long seed) {
        this.granted = granted;
        this.seed = seed;
    }

    public static void encode(SeedPushPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.granted);
        buf.writeLong(msg.seed);
    }

    public static SeedPushPacket decode(FriendlyByteBuf buf) {
        return new SeedPushPacket(buf.readBoolean(), buf.readLong());
    }

    public static void handle(SeedPushPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> clientOnly(() -> ServerSeed.set(msg.granted ? msg.seed : null)));
        ctx.setPacketHandled(true);
    }

    private static void clientOnly(Runnable task) {
        if (FMLEnvironment.dist.isClient()) {
            task.run();
        }
    }
}