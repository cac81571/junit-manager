package dev.junitmanager.suite;

/**
 * 実行時パラメータ。優先順位はシステムプロパティ → 環境変数 → デフォルト。
 * 未定義のときだけ {@code null}（デフォルト未指定時）。空文字は有効な値として返す。
 * 数値・真偽は空文字や空白のみのときはパースせずデフォルト（未指定時は {@code null}）。
 * <pre>
 *   if (TestParams.get("PARAM") == null) { ... }
 *   Integer n = TestParams.getInt("PARAM");
 *   TestParams.getInt("PARAM", 1000);
 * </pre>
 * Maven 例: {@code mvn test -DPARAM=2000}
 * または {@code set PARAM=2000} してから {@code mvn test}
 */
public final class TestParams {
    private TestParams() {
    }

    public static String get(String name) {
        return get(name, null);
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

    public static Integer getInt(String name) {
        return getInt(name, null);
    }

    public static Integer getInt(String name, Integer defaultValue) {
        String raw = get(name, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " は整数である必要があります: " + raw, ex);
        }
    }

    public static Long getLong(String name) {
        return getLong(name, null);
    }

    public static Long getLong(String name, Long defaultValue) {
        String raw = get(name, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " は整数である必要があります: " + raw, ex);
        }
    }

    public static Boolean getBoolean(String name) {
        return getBoolean(name, null);
    }

    public static Boolean getBoolean(String name, Boolean defaultValue) {
        String raw = get(name, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.valueOf(raw.trim());
    }
}
