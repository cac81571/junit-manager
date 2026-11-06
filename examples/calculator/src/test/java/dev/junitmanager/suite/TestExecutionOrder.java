package dev.junitmanager.suite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestExecutionOrder {
    public static final List<String> METHODS = List.copyOf(load());

    private TestExecutionOrder() {
    }

    private static List<String> load() {
        Path csv = Path.of("order.csv");
        if (!Files.isRegularFile(csv)) {
            Path nested = Path.of("examples/calculator/order.csv");
            if (Files.isRegularFile(nested)) {
                csv = nested;
            }
        }
        try {
            List<String> methods = new ArrayList<>();
            for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                    continue;
                }
                int hash = trimmed.indexOf('#');
                if (hash <= 0 || hash == trimmed.length() - 1 || trimmed.indexOf('#', hash + 1) >= 0) {
                    throw new IllegalArgumentException("形式は クラス#メソッド です: " + line);
                }
                methods.add(trimmed);
            }
            return methods;
        } catch (Exception ex) {
            throw new IllegalStateException("order.csv を読めません: " + csv.toAbsolutePath(), ex);
        }
    }
}
