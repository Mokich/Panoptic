package net.mokich.panoptic.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

public final class LogFeed {
    public record Line(long time, Level level, String logger, String text) {}

    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static final Object LOCK = new Object();
    private static volatile boolean attached;
    private static int warns;
    private static int errors;
    private static int version;
    private static List<Line> cached = List.of();
    private static int cachedVersion = -1;
    private static long lastRebuild;

    private LogFeed() {}

    public static void attach() {
        if (attached) {
            return;
        }
        attached = true;
        try {
            Logger root = (Logger) LogManager.getRootLogger();
            AbstractAppender appender = new AbstractAppender("PanopticFeed", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    accept(event);
                }
            };
            appender.start();
            root.addAppender(appender);
        } catch (Throwable ignored) {
        }
    }

    private static void accept(LogEvent event) {
        Level level = event.getLevel();
        String logger = event.getLoggerName();
        if (logger == null) {
            logger = "";
        }
        int dot = logger.lastIndexOf('.');
        if (dot >= 0 && dot < logger.length() - 1) {
            logger = logger.substring(dot + 1);
        }
        String msg;
        try {
            msg = event.getMessage().getFormattedMessage();
        } catch (Throwable t) {
            return;
        }
        if (msg == null || msg.isBlank()) {
            return;
        }
        msg = msg.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        if (msg.length() > 400) {
            msg = msg.substring(0, 400);
        }
        synchronized (LOCK) {
            if (level == Level.ERROR || level == Level.FATAL) {
                errors++;
            } else if (level == Level.WARN) {
                warns++;
            }
            LINES.addLast(new Line(System.currentTimeMillis(), level, logger, msg));
            int cap = ModSettings.getInt(ModSettings.LOG_MAX_LINES);
            if (cap > 0) {
                while (LINES.size() > cap) {
                    LINES.removeFirst();
                }
            }
            version++;
        }
    }

    public static List<Line> snapshot() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (cachedVersion != version && now - lastRebuild >= 50L) {
                cached = new ArrayList<>(LINES);
                cachedVersion = version;
                lastRebuild = now;
            }
            return cached;
        }
    }

    public static int warns() {
        return warns;
    }

    public static int errors() {
        return errors;
    }

    public static int colorOf(Level level) {
        if (level == Level.ERROR || level == Level.FATAL) {
            return 0xFFE06666;
        }
        if (level == Level.WARN) {
            return 0xFFE0B341;
        }
        if (level == Level.INFO) {
            return 0xFFACA188;
        }
        return 0xFF766C52;
    }

    public static int barColorOf(Level level) {
        if (level == Level.ERROR || level == Level.FATAL) {
            return 0xFFE06666;
        }
        if (level == Level.WARN) {
            return 0xFFE0B341;
        }
        if (level == Level.INFO) {
            return 0xFF6E5527;
        }
        return 0xFF3A2E17;
    }
}