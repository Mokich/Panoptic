package net.mokich.panoptic.api.util;

public final class Disc {
    private static final int[][] SPANS = new int[33][];

    private Disc() {
    }

    public static int[] spans(int r) {
        if (r >= 0 && r < SPANS.length) {
            int[] c = SPANS[r];
            if (c == null) {
                c = compute(r);
                SPANS[r] = c;
            }
            return c;
        }
        return compute(r);
    }

    private static int[] compute(int r) {
        int[] c = new int[2 * r + 1];
        for (int dy = -r; dy <= r; dy++) {
            c[dy + r] = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
        }
        return c;
    }
}