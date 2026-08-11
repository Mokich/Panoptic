package net.mokich.panoptic.config;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.Level;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogFeed {
    public record Line(long time, Level level, String logger, String text) {}

    private static final int DEFAULT_MAX_LINES = 20000;
    private static final long POLL_INTERVAL = 200L;
    private static final long TAIL_LIMIT = 4L * 1024L * 1024L;
    private static final long CHUNK = 8L * 1024L * 1024L;
    private static final int MAX_TEXT = 400;

    private static final Pattern WITH_LOGGER =
            Pattern.compile("^\\[[^\\]]*\\] \\[[^\\]/]*/([A-Z]+)\\] \\[([^\\]]*)\\]: (.*)$");
    private static final Pattern WITHOUT_LOGGER =
            Pattern.compile("^\\[[^\\]]*\\] \\[[^\\]/]*/([A-Z]+)\\]: (.*)$");

    private static final Deque<Line> LINES = new ArrayDeque<>();

    private static Path file;
    private static long position;
    private static long lastPoll;
    private static int maxLines = DEFAULT_MAX_LINES;
    private static int warns;
    private static int errors;
    private static Level carry = Level.INFO;
    private static List<Line> cached = List.of();
    private static int version;
    private static int cachedVersion = -1;

    private LogFeed() {
    }

    public static void attach() {
    }

    public static List<Line> snapshot() {
        poll();
        if (cachedVersion != version) {
            cached = new ArrayList<>(LINES);
            cachedVersion = version;
        }
        return cached;
    }

    public static int warns() {
        return warns;
    }

    public static int errors() {
        return errors;
    }

    private static void poll() {
        long now = System.currentTimeMillis();
        if (now - lastPoll < POLL_INTERVAL) {
            return;
        }
        lastPoll = now;
        refreshMaxLines();
        try {
            read(now);
        } catch (Throwable ignored) {
        }
    }

    private static void read(long now) throws Exception {
        if (file == null) {
            file = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length < position) {
                reset();
            }
            if (position == 0L && maxLines > 0 && length > TAIL_LIMIT) {
                position = length - TAIL_LIMIT;
            }
            if (length == position) {
                return;
            }
            raf.seek(position);
            byte[] buffer = new byte[(int) Math.min(length - position, CHUNK)];
            int read = raf.read(buffer);
            if (read <= 0) {
                return;
            }
            int end = lastBreak(buffer, read);
            if (end <= 0) {
                return;
            }
            position += end;
            boolean first = LINES.isEmpty() && position == end;
            String block = new String(buffer, 0, end, StandardCharsets.UTF_8);
            for (String raw : block.split("\n")) {
                if (first) {
                    first = false;
                    if (!raw.startsWith("[")) {
                        continue;
                    }
                }
                accept(raw, now);
            }
            trim();
            version++;
        }
    }

    private static void reset() {
        LINES.clear();
        position = 0L;
        warns = 0;
        errors = 0;
        carry = Level.INFO;
        version++;
    }

    private static int lastBreak(byte[] buffer, int read) {
        for (int i = read - 1; i >= 0; i--) {
            if (buffer[i] == '\n') {
                return i + 1;
            }
        }
        return read == buffer.length ? read : 0;
    }

    private static void accept(String raw, long now) {
        String text = raw.replace('\r', ' ').replace('\t', ' ');
        if (text.isBlank()) {
            return;
        }
        Level level = carry;
        String logger = "";
        Matcher m = WITH_LOGGER.matcher(text);
        if (m.matches()) {
            level = levelOf(m.group(1));
            logger = shorten(m.group(2));
            text = m.group(3);
        } else {
            m = WITHOUT_LOGGER.matcher(text);
            if (m.matches()) {
                level = levelOf(m.group(1));
                text = m.group(2);
            }
        }
        carry = level;
        if (text.isBlank()) {
            return;
        }
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        if (level == Level.ERROR || level == Level.FATAL) {
            errors++;
        } else if (level == Level.WARN) {
            warns++;
        }
        LINES.addLast(new Line(now, level, logger, text));
    }

    private static Level levelOf(String name) {
        Level level = Level.getLevel(name);
        return level == null ? Level.INFO : level;
    }

    private static String shorten(String logger) {
        String name = logger;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            name = name.substring(0, slash);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            name = name.substring(dot + 1);
        }
        return name;
    }

    private static void trim() {
        int cap = maxLines;
        if (cap <= 0) {
            return;
        }
        while (LINES.size() > cap) {
            LINES.removeFirst();
        }
    }

    private static void refreshMaxLines() {
        try {
            int value = ModSettings.getInt(ModSettings.LOG_MAX_LINES);
            if (value >= 0) {
                maxLines = value;
            }
        } catch (Throwable ignored) {
        }
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