package net.mokich.panoptic.network;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.ArrayList;
import java.util.List;

public class AllStructuresPacket implements CustomPacketPayload {
    public static final Type<AllStructuresPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "all_structures"));
    public static final StreamCodec<FriendlyByteBuf, AllStructuresPacket> STREAM_CODEC =
            StreamCodec.ofMember(AllStructuresPacket::encode, AllStructuresPacket::decode);

    @Override
    public Type<AllStructuresPacket> type() {
        return TYPE;
    }

    public static void encode(AllStructuresPacket msg, FriendlyByteBuf buf) {}

    public static AllStructuresPacket decode(FriendlyByteBuf buf) {
        return new AllStructuresPacket();
    }

    public static void handle(AllStructuresPacket msg, ServerPlayNetworking.Context ctx) {
        ServerPlayer player = ctx.player();
        ctx.server().execute(() -> {
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
                ServerPlayNetworking.send(player, new AllStructuresResultPacket(list));
            }
        });
    }
}