package net.mokich.panoptic.network;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import java.util.ArrayList;
import java.util.List;

public class AllStructuresPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "all_structures");

    public static void encode(AllStructuresPacket msg, FriendlyByteBuf buf) {}

    public static AllStructuresPacket decode(FriendlyByteBuf buf) {
        return new AllStructuresPacket();
    }

    public static void send() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(new AllStructuresPacket(), buf);
        ClientPlayNetworking.send(CHANNEL, buf);
    }

    public static void handle(AllStructuresPacket msg, ServerPlayer player) {
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
            AllStructuresResultPacket.send(player, new AllStructuresResultPacket(list));
        }
    }
}