package dev.junitmanager.ui;

import java.util.Locale;

final class LogLines {
    enum Level {
        ERROR,
        WARNING,
        INFO,
        DEBUG,
        PLAIN
    }

    private LogLines() {
    }

    static Level level(String line) {
        if (line == null || line.isEmpty()) {
            return Level.PLAIN;
        }
        String text = stripLeading(line);
        String tag = tag(text);
        if (tag == null) {
            return Level.PLAIN;
        }
        return switch (tag) {
            case "ERROR" -> Level.ERROR;
            case "WARNING", "WARN" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "DEBUG", "TRACE" -> Level.DEBUG;
            default -> Level.PLAIN;
        };
    }

    private static String tag(String text) {
        if (text.isEmpty() || text.charAt(0) != '[') {
            return null;
        }
        int end = text.indexOf(']');
        if (end < 2 || end > 12) {
            return null;
        }
        return text.substring(1, end).toUpperCase(Locale.ROOT);
    }

    private static String stripLeading(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) <= ' ') {
            index++;
        }
        return index == 0 ? line : line.substring(index);
    }
}
