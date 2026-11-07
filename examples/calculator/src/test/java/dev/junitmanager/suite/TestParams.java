package dev.junitmanager.suite;

/**
 * 実行時パラメータ。優先順位はシステムプロパティ → 環境変数 → デフォルト。
 * <pre>
 *   TestParams.get("PARAM", "1000");
 *   TestParams.getInt("PARAM", 1000);
 * </pre>
 * Maven 例: {@code mvn test -DPARAM=2000}（pom で systemPropertyVariables に渡す）
 * または {@code set PARAM=2000} してから {@code mvn test}
 */
public final class TestParams {
    private TestParams() {
    }

    public static String get(String name, String defaultValue) {
        String property = System.getProperty(name);
        if (property != null) {
            return property;
        }
        String env = System.getenv(name);
        if (env != null) {
            return env;
        }
        return defaultValue;
    }

    public static int getInt(String name, int defaultValue) {
        String raw = get(name, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " は整数である必要があります: " + raw, ex);
        }
    }

    public static long getLong(String name, long defaultValue) {
        String raw = get(name, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " は整数である必要があります: " + raw, ex);
        }
    }

    public static boolean getBoolean(String name, boolean defaultValue) {
        String raw = get(name, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
