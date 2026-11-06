package dev.junitmanager.model;

import java.util.Locale;

public final class DurationFormat {
    private DurationFormat() {
    }

    public static String of(long durationMillis) {
        if (durationMillis < 0) {
            return "";
        }
        if (durationMillis < 1000) {
            return durationMillis + " ms";
        }
        return String.format(Locale.US, "%.1f s", durationMillis / 1000.0);
    }
}
