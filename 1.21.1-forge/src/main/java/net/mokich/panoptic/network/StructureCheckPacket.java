package net.mokich.panoptic.network;

import net.mokich.panoptic.Panoptic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class StructureCheckPacket {
    public static void encode(StructureCheckPacket msg, FriendlyByteBuf buf) {}
    public static StructureCheckPacket decode(FriendlyByteBuf buf) {
        return new StructureCheckPacket();
    }

    public static void handle(StructureCheckPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            StructureManager structureManager = level.structureManager();
            RegistryAccess access = level.registryAccess();
            Registry<Structure> registry = access.registryOrThrow(Registries.STRUCTURE);

            List<StructureResultPacket.StructInfo> found = new ArrayList<>();
            for (Holder<Structure> holder : registry.holders().toList()) {
                StructureStart start = structureManager.getStructureAt(pos, holder.value());
                if (start == null || !start.isValid() || !start.getBoundingBox().isInside(pos)) {
                    continue;
                }
                holder.unwrapKey().ifPresent(key -> {
                    BoundingBox box = start.getBoundingBox();
                    StructureResultPacket.StructInfo info = new StructureResultPacket.StructInfo();
                    info.id = key.location().toString();
                    info.minX = box.minX();
                    info.minY = box.minY();
                    info.minZ = box.minZ();
                    info.maxX = box.maxX();
                    info.maxY = box.maxY();
                    info.maxZ = box.maxZ();
                    info.pieces = start.getPieces().size();
                    holder.tags().forEach(tag -> info.tags.add("#" + tag.location()));
                    found.add(info);
                });
            }

            if (!found.isEmpty()) {
                Panoptic.CHANNEL.send(new StructureResultPacket(found),
                        PacketDistributor.PLAYER.with(player));
            }
        });
        ctx.setPacketHandled(true);
    }
}