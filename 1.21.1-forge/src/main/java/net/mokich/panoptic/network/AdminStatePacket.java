package net.mokich.panoptic.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.mokich.panoptic.screen.PermsAdminScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdminStatePacket {
    public record PlayerRow(UUID id, String name, String group, Set<String> allow, Set<String> deny) {}

    public final Map<String, Set<String>> groups;
    public final List<PlayerRow> players;
    public final String rawJson;

    public AdminStatePacket(Map<String, Set<String>> groups, List<PlayerRow> players, String rawJson) {
        this.groups = groups;
        this.players = players;
        this.rawJson = rawJson;
    }

    public static void encode(AdminStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.groups.size());
        for (Map.Entry<String, Set<String>> e : msg.groups.entrySet()) {
            buf.writeUtf(e.getKey(), 64);
            buf.writeVarInt(e.getValue().size());
            for (String n : e.getValue()) {
                buf.writeUtf(n, 64);
            }
        }
        buf.writeVarInt(msg.players.size());
        for (PlayerRow p : msg.players) {
            buf.writeUUID(p.id());
            buf.writeUtf(p.name(), 64);
            buf.writeUtf(p.group(), 64);
            buf.writeVarInt(p.allow().size());
            for (String n : p.allow()) {
                buf.writeUtf(n, 64);
            }
            buf.writeVarInt(p.deny().size());
            for (String n : p.deny()) {
                buf.writeUtf(n, 64);
            }
        }
        buf.writeUtf(msg.rawJson, 32000);
    }

    public static AdminStatePacket decode(FriendlyByteBuf buf) {
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        int gc = Math.min(buf.readVarInt(), 256);
        for (int i = 0; i < gc; i++) {
            String name = buf.readUtf(64);
            int nc = Math.min(buf.readVarInt(), 64);
            Set<String> nodes = new LinkedHashSet<>();
            for (int j = 0; j < nc; j++) {
                nodes.add(buf.readUtf(64));
            }
            groups.put(name, nodes);
        }
        List<PlayerRow> players = new ArrayList<>();
        int pc = Math.min(buf.readVarInt(), 4096);
        for (int i = 0; i < pc; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            String group = buf.readUtf(64);
            int ac = Math.min(buf.readVarInt(), 64);
            Set<String> allow = new LinkedHashSet<>();
            for (int j = 0; j < ac; j++) {
                allow.add(buf.readUtf(64));
            }
            int dc = Math.min(buf.readVarInt(), 64);
            Set<String> deny = new LinkedHashSet<>();
            for (int j = 0; j < dc; j++) {
                deny.add(buf.readUtf(64));
            }
            players.add(new PlayerRow(id, name, group, allow, deny));
        }
        String raw = buf.readUtf(32000);
        return new AdminStatePacket(groups, players, raw);
    }

    public static void handle(AdminStatePacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (Minecraft.getInstance().screen instanceof PermsAdminScreen s) {
                s.applyState(msg);
            }
        }));
        ctx.setPacketHandled(true);
    }
}