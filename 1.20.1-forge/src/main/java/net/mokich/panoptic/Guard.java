package net.mokich.panoptic;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class Guard {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean reported;

    private Guard() {
    }

    public static void run(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            report(t);
        }
    }

    public static void report(Throwable t) {
        if (!reported) {
            reported = true;
            LOG.error("Panoptic a feature was suppressed to avoid crashing your pack; " + "the mod will keep running passively. Root cause:", t);
        }
    }
}