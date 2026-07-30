package net.mokich.panoptic.api.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.mokich.panoptic.screenshot.ScreenGrab;
import net.mokich.panoptic.screenshot.ScreenGrabStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BgTex {
    private static final Map<String, Identifier> LOCATIONS = new HashMap<>();
    private static final Map<String, DynamicTexture> TEXTURES = new HashMap<>();

    private BgTex() {}

    public static Identifier get(ScreenGrab grab) {
        if (grab.bg == null) {
            return null;
        }
        if (LOCATIONS.containsKey(grab.bg)) {
            return LOCATIONS.get(grab.bg);
        }
        Identifier rl = null;
        try (InputStream in = Files.newInputStream(ScreenGrabStore.grabsDir().resolve(grab.bg))) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture tex = new DynamicTexture(() -> "panoptic-bg", img);
            rl = Identifier.withDefaultNamespace("panoptic_grab_" + UUID.randomUUID().toString().replace("-", ""));
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            TEXTURES.put(grab.bg, tex);
        } catch (Throwable ignored) {
        }
        LOCATIONS.put(grab.bg, rl);
        return rl;
    }

    public static void drop(ScreenGrab grab) {
        if (grab.bg == null) {
            return;
        }
        Identifier rl = LOCATIONS.remove(grab.bg);
        DynamicTexture tex = TEXTURES.remove(grab.bg);
        try {
            if (rl != null) {
                Minecraft.getInstance().getTextureManager().release(rl);
            }
            if (tex != null) {
                tex.close();
            }
        } catch (Throwable ignored) {
        }
    }
}