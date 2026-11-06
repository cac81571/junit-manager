package dev.junitmanager.ui;

import dev.junitmanager.model.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Sources {
    private static final Pattern STACK = Pattern.compile("at\\s+([\\w$]+(?:\\.[\\w$]+)*)\\(([^):]+\\.java):(\\d+)\\)");

    private Sources() {
    }

    public record StackFrame(String className, String fileName, int line) {
    }

    public static StackFrame parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher matcher = STACK.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String owner = matcher.group(1);
        int dot = owner.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException ex) {
            return null;
        }
        return new StackFrame(owner.substring(0, dot), matcher.group(2), lineNumber);
    }

    public static Path find(Workspace workspace, String className, String fileName) {
        if (workspace == null) {
            return null;
        }
        if (className != null && !className.isBlank()) {
            String outer = className;
            int dollar = outer.indexOf('$');
            if (dollar >= 0) {
                outer = outer.substring(0, dollar);
            }
            String relative = outer.replace('.', '/') + ".java";
            for (Path root : workspace.sourceRoots()) {
                Path candidate = root.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String simple = Path.of(fileName).getFileName().toString();
        for (Path root : workspace.sourceRoots()) {
            try (var stream = Files.walk(root, 20)) {
                Path found = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals(simple))
                        .findFirst()
                        .orElse(null);
                if (found != null) {
                    return found;
                }
            } catch (IOException ignored) {
                // 次のソースルートを探す
            }
        }
        return null;
    }
}
