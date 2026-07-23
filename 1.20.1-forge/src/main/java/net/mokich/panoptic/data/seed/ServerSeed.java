package net.mokich.panoptic.data.seed;

public final class ServerSeed {
    private static volatile Long seed;

    private ServerSeed() {
    }

    public static Long get() {
        return seed;
    }

    public static void set(Long value) {
        seed = value;
    }

    public static void clear() {
        seed = null;
    }
}
