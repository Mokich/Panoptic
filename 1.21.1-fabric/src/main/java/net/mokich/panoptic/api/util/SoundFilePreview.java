package net.mokich.panoptic.api.util;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Consumer;

public final class SoundFilePreview extends AbstractSoundInstance {
    private static SoundFilePreview playing;
    private static String label = "";
    private static String lastPath;
    private static long startedAt;
    private static long durationMs;
    private static boolean paused;
    private static long pausedElapsed;
    private static long pendingSeekMs = -1;

    public static boolean active() {
        return playing != null && Minecraft.getInstance().getSoundManager().isActive(playing);
    }

    public static boolean playingNow() {
        return active() && !paused;
    }

    public static long durationMs() {
        return durationMs;
    }

    public static long elapsedMs() {
        if (!active()) {
            return 0L;
        }
        long e = paused ? pausedElapsed : System.currentTimeMillis() - startedAt;
        return durationMs > 0 ? Math.min(e, durationMs) : Math.max(0L, e);
    }

    public static float progress() {
        if (durationMs <= 0 || !active()) {
            return 0.0F;
        }
        return Math.min(1.0F, elapsedMs() / (float) durationMs);
    }

    public static boolean barVisible() {
        return !label.isEmpty();
    }

    public static void closeBar() {
        stopCurrent();
        label = "";
        lastPath = null;
    }

    public static void replay() {
        if (lastPath != null) {
            play(lastPath);
        }
    }

    public static String label() {
        return label;
    }

    public static void togglePause() {
        if (!active()) {
            replay();
            return;
        }
        if (paused) {
            startedAt = System.currentTimeMillis() - pausedElapsed;
            paused = false;
            withChannel(Channel::unpause);
        } else {
            pausedElapsed = elapsedMs();
            paused = true;
            withChannel(Channel::pause);
        }
    }

    public static void seek(float frac) {
        if (durationMs <= 0) {
            return;
        }
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        if (!active()) {
            replay();
        }
        pendingSeekMs = (long) (frac * durationMs);
        tick();
    }

    public static void tick() {
        if (pendingSeekMs < 0 || !active()) {
            return;
        }
        ChannelAccess.ChannelHandle h = handle();
        if (h == null) {
            return;
        }
        long t = pendingSeekMs;
        pendingSeekMs = -1;
        h.execute(ch -> {
            int src = sourceId(ch);
            if (src >= 0) {
                AL10.alSourcef(src, AL11.AL_SEC_OFFSET, t / 1000.0F);
            }
        });
        startedAt = System.currentTimeMillis() - t;
        if (paused) {
            pausedElapsed = t;
        }
    }

    private static ChannelAccess.ChannelHandle handle() {
        try {
            SoundManager sm = Minecraft.getInstance().getSoundManager();
            SoundEngine engine = null;
            for (Field f : SoundManager.class.getDeclaredFields()) {
                if (f.getType() == SoundEngine.class) {
                    f.setAccessible(true);
                    engine = (SoundEngine) f.get(sm);
                    break;
                }
            }
            if (engine == null || playing == null) {
                return null;
            }
            for (Field f : SoundEngine.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                Object m = f.get(engine);
                if (m instanceof Map<?, ?> map) {
                    Object h = map.get(playing);
                    if (h instanceof ChannelAccess.ChannelHandle ch) {
                        return ch;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void withChannel(Consumer<Channel> fn) {
        ChannelAccess.ChannelHandle h = handle();
        if (h != null) {
            h.execute(fn);
        }
    }

    private static int sourceId(Channel ch) {
        try {
            for (Field f : Channel.class.getDeclaredFields()) {
                if (f.getType() == int.class && Modifier.isFinal(f.getModifiers())
                        && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f.getInt(ch);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private SoundFilePreview(ResourceLocation direct) {
        super(direct, SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 1.0F;
        this.pitch = 1.0F;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(this.location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F),
                1, Sound.Type.FILE, false, false, 16);
        return new WeighedSoundEvents(this.location, null);
    }

    public static boolean play(String resourcePath) {
        int assets = resourcePath.indexOf("assets/");
        String p = assets >= 0 ? resourcePath.substring(assets + 7) : resourcePath;
        int slash = p.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        String ns = p.substring(0, slash);
        String rest = p.substring(slash + 1);
        if (!rest.startsWith("sounds/") || !rest.endsWith(".ogg")) {
            return false;
        }
        String name = rest.substring("sounds/".length(), rest.length() - 4);
        ResourceLocation rl = ResourceLocation.tryParse(ns + ":" + name);
        if (rl == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (playing != null) {
                mc.getSoundManager().stop(playing);
            }
            playing = new SoundFilePreview(rl);
            mc.getSoundManager().play(playing);
            int lastSlash = resourcePath.lastIndexOf('/');
            label = lastSlash >= 0 ? resourcePath.substring(lastSlash + 1) : resourcePath;
            lastPath = resourcePath;
            startedAt = System.currentTimeMillis();
            paused = false;
            pausedElapsed = 0L;
            pendingSeekMs = -1L;
            durationMs = oggDurationMs(ResourceLocation.fromNamespaceAndPath(ns, "sounds/" + name + ".ogg"));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static long oggDurationMs(ResourceLocation ogg) {
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(ogg);
            if (opt.isEmpty()) {
                return 0L;
            }
            byte[] d;
            try (var in = opt.get().open()) {
                d = in.readAllBytes();
            }
            int sr = 0;
            for (int i = 0; i + 18 < d.length; i++) {
                if (d[i] == 1 && d[i + 1] == 'v' && d[i + 2] == 'o' && d[i + 3] == 'r'
                        && d[i + 4] == 'b' && d[i + 5] == 'i' && d[i + 6] == 's') {
                    int o = i + 12;
                    sr = (d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8 | (d[o + 2] & 0xFF) << 16 | (d[o + 3] & 0xFF) << 24;
                    break;
                }
            }
            if (sr <= 0) {
                return 0L;
            }
            long granule = 0L;
            for (int i = d.length - 14; i >= 0; i--) {
                if (d[i] == 'O' && d[i + 1] == 'g' && d[i + 2] == 'g' && d[i + 3] == 'S') {
                    long g = 0L;
                    for (int b = 0; b < 8; b++) {
                        g |= (long) (d[i + 6 + b] & 0xFF) << (8 * b);
                    }
                    granule = g;
                    break;
                }
            }
            return granule <= 0L ? 0L : granule * 1000L / sr;
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static void stopCurrent() {
        if (playing != null) {
            try {
                Minecraft.getInstance().getSoundManager().stop(playing);
            } catch (Throwable ignored) {
            }
            playing = null;
        }
        paused = false;
        pausedElapsed = 0L;
        pendingSeekMs = -1L;
    }
}