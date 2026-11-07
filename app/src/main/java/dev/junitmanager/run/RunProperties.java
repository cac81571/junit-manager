package dev.junitmanager.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 実行時に渡すシステムプロパティ。1行 {@code KEY=値} または {@code -DKEY=値}。 */
public final class RunProperties {
    private RunProperties() {
    }

    public static List<String> mavenArgs(String text) {
        Map<String, String> values = parse(text);
        List<String> args = new ArrayList<>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            args.add("-D" + entry.getKey() + "=" + entry.getValue());
        }
        return args;
    }

    public static String jvmOptions(String text) {
        return String.join(" ", mavenArgs(text));
    }

    public static boolean isInternal(String arg) {
        if (arg == null || !arg.startsWith("-D")) {
            return true;
        }
        String body = arg.substring(2);
        int eq = body.indexOf('=');
        String key = eq < 0 ? body : body.substring(0, eq);
        return key.equals("confirm")
                || key.equals("test")
                || key.equals("forkCount")
                || key.equals("style.color")
                || key.equals("trimStackTrace")
                || key.startsWith("surefire.")
                || key.startsWith("maven.surefire.");
    }

    static Map<String, String> parse(String text) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("-D")) {
                line = line.substring(2);
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
                continue;
            }
            if (isInternal("-D" + key + "=")) {
                continue;
            }
            values.put(key, value);
        }
        return values;
    }
}
