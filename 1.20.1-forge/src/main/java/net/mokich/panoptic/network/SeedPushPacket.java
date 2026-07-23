package net.mokich.panoptic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.mokich.panoptic.data.seed.ServerSeed;

import java.util.function.Supplier;

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

    public static void handle(SeedPushPacket msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ServerSeed.set(msg.granted ? msg.seed : null)));
        ctx.get().setPacketHandled(true);
    }
}