package net.mokich.panoptic.network;

import net.mokich.panoptic.Panoptic;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class AllStructuresPacket {
    public static void encode(AllStructuresPacket msg, FriendlyByteBuf buf) {}
    public static AllStructuresPacket decode(FriendlyByteBuf buf) {
        return new AllStructuresPacket();
    }

    public static void handle(AllStructuresPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            RegistryAccess access = player.serverLevel().registryAccess();
            Registry<Structure> registry = access.registryOrThrow(Registries.STRUCTURE);

            List<AllStructuresResultPacket.Info> list = new ArrayList<>();
            for (Holder.Reference<Structure> holder : registry.holders().toList()) {
                holder.unwrapKey().ifPresent(key -> {
                    AllStructuresResultPacket.Info info = new AllStructuresResultPacket.Info();
                    info.id = key.location().toString();
                    holder.tags().forEach(tag -> info.tags.add("#" + tag.location()));
                    list.add(info);
                });
            }

            if (!list.isEmpty()) {
                Panoptic.CHANNEL.send(new AllStructuresResultPacket(list),
                        PacketDistributor.PLAYER.with(player));
            }
        });
        ctx.setPacketHandled(true);
    }
}